@file:UseSerializers(TestCaseRunResultSerializer::class)
package io.engenious.sift.node

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.Tongs
import com.github.tarcv.tongs.TongsRunner
import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.NoTestCasesFoundException
import com.github.tarcv.tongs.injector.RuleManagerFactory
import com.github.tarcv.tongs.injector.runner.DeviceTestRunnerFactoryInjector.deviceTestRunnerFactory
import com.github.tarcv.tongs.injector.runner.ProgressReporterInjector.progressReporter
import com.github.tarcv.tongs.model.TestCaseEventQueue
import com.github.tarcv.tongs.pooling.NoDevicesForPoolException
import com.github.tarcv.tongs.pooling.PoolLoader
import io.engenious.sift.MergedConfigWithInjectedVars
import io.engenious.sift.OrchestratorConfig
import io.engenious.sift.applyLocalNodeConfiguration
import io.engenious.sift.node.serialization.RemoteDevice
import io.engenious.sift.node.serialization.RemoteTestCaseEvent
import io.engenious.sift.node.serialization.TestCaseRunResultSerializer
import io.engenious.sift.node.serialization.deviceIdentifierAsString
import io.engenious.sift.setupCommonTongsConfiguration
import io.engenious.sift.singleLocalNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.koin.core.context.KoinContextHandler
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class Node(config: MergedConfigWithInjectedVars) {
    init {
        val thisNode = config.mergedConfigWithInjectedVars.nodes.singleLocalNode() as OrchestratorConfig.Node.RemoteNode
        val tempRoot = Files.createDirectories(Paths.get(thisNode.deploymentPath, "tmp")) // TODO: delete on shutdown
        val tempOutputPath = Files.createTempDirectory(tempRoot, "output")
        val runnerConfiguration = Configuration.Builder()
            .setupCommonTongsConfiguration(config)
            .applyLocalNodeConfiguration(config)
            .withOutput(tempOutputPath.toFile())
            .build(true)

        val tongs = Tongs(runnerConfiguration)
        val runnerModule = Tongs::class.memberProperties
            .single { it.name == "runnerModule" }
            .also { it.isAccessible = true }
            .get(tongs) as Module
        startKoin {
            modules(runnerModule)
        }
    }

    private val globalLock = Any()
    private val pool by lazy {
        val tongsRunner by KoinContextHandler.get().inject<TongsRunner>()
        val poolLoader = tongsRunner.extractProperty("poolLoader") as PoolLoader
        poolLoader.loadPools()
            .single()
    }
    private val devices by lazy {
        try {
            pool.devices
        } catch (e: NoDevicesForPoolException) {
            emptyList<Device>()
        }
    }
    private val tests by lazy {
        TongsRunner.Companion::class.declaredFunctions
            .single { it.name == "createTestSuiteLoaderForPool" }
            .also { it.isAccessible = true }
            .let {
                try {
                    @Suppress("UNCHECKED_CAST")
                    it.call(TongsRunner.Companion, pool) as Collection<TestCaseEvent>
                } catch (e: NoTestCasesFoundException) {
                    emptyList()
                }
            }
    }

    fun provideDevices(): ProvidedDevices = synchronized(globalLock) {
        ProvidedDevices(translateDevices())
    }

    @Serializable
    data class CollectTests(
        val deviceIds: Set<String>
    )

    fun collectTests(params: CollectTests): CollectedTests = synchronized(globalLock) {
        val deviceIdSet = params.deviceIds
        val collectingDevices = devices
            .filter { it.deviceIdentifierAsString() in deviceIdSet }

        if (collectingDevices.size != deviceIdSet.size) {
            throw IllegalArgumentException("Unknown devices were requested")
        }

        val filteredTests = tests
            .filter { testCase ->
                collectingDevices.any { testCase.isEnabledOn(it) }
            }
            .map(RemoteTestCaseEvent.Companion::fromTestCaseEvent)

        return CollectedTests(filteredTests)
    }

    private fun translateDevices(): List<RemoteDevice> = devices.map(RemoteDevice.Companion::fromLocalDevice)

    @Serializable
    data class RunTest(
        val deviceId: String,
        val testCase: String
    )

    fun runTest(params: RunTest): TestRunResult {
        val tongsRunner by KoinContextHandler.get().inject<TongsRunner>()
        val ruleManagerFactory = tongsRunner.extractProperty("ruleManagerFactory") as RuleManagerFactory
        val device = pool.devices.singleOrNull() { it.deviceIdentifierAsString() == params.deviceId }
            ?: throw IllegalArgumentException("No such device found")
        val deviceTestRunner = deviceTestRunnerFactory().createDeviceTestRunner(
            pool, device, ruleManagerFactory
        )
        val testCaseEvent = tests.singleOrNull { it.testCase.toString() == params.testCase }
            ?: throw java.lang.IllegalArgumentException("No such testcase found")
        val results = mutableListOf<TestCaseRunResult>()

        // TODO: run pool and device rules
        val latch = CountDownLatch(1)
        deviceTestRunner.run(
            TestCaseEventQueue(
                listOf(testCaseEvent),
                results
            ),
            latch,
            progressReporter()
        )
        latch.await()

        return results.singleOrNull()
            ?.let {
                TestRunResult(
                    it.copy(device = RemoteDevice.fromLocalDevice(it.device))
                )
            }
            ?: throw Exception("Fatal crash during the test case execution")
    }
}

@Serializable
class CollectedTests(
    val testCases: Collection<RemoteTestCaseEvent>
)

@Serializable
data class ProvidedDevices(
    val devices: List<RemoteDevice>
)

@Serializable
data class TestRunResult(
    val result: TestCaseRunResult
)

private inline fun <reified T : Any> T.extractProperty(name: String): Any? {
    return T::class.declaredMemberProperties
        .single { it.name == name }
        .also { it.isAccessible = true }
        .get(this)
}
