@file:UseSerializers(TestCaseRunResultSerializer::class)
package io.engenious.sift.node

import com.android.annotations.concurrency.GuardedBy
import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.Tongs
import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.testcases.TestCase
import io.engenious.sift.MergedConfigWithInjectedVars
import io.engenious.sift.OrchestratorConfig
import io.engenious.sift.applyLocalNodeConfiguration
import io.engenious.sift.node.plugins.DeviceCollector
import io.engenious.sift.node.plugins.TestCaseCollectingPlugin
import io.engenious.sift.node.plugins.blocker.LoopingDeviceProvider.Companion.loopingDevices
import io.engenious.sift.node.plugins.blocker.LoopingDeviceProviderFactory
import io.engenious.sift.node.plugins.blocker.LoopingTestCaseProviderFactory
import io.engenious.sift.node.plugins.blocker.LoopingTestCaseRunnerFactory
import io.engenious.sift.node.serialization.RemoteDevice
import io.engenious.sift.node.serialization.RemoteTestCase
import io.engenious.sift.node.serialization.TestCaseRunResultSerializer
import io.engenious.sift.nodeDevicesStrategy
import io.engenious.sift.setupCommonTongsConfiguration
import io.engenious.sift.singleLocalNode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

class Node(
    private val config: MergedConfigWithInjectedVars,
    private val nodeShutdownSignaller: CountDownLatch
) {
    private val tempOutputPath: Path
    init {
        val thisNode = config.mergedConfigWithInjectedVars.nodes.singleLocalNode() as OrchestratorConfig.Node.RemoteNode
        val tempRoot = Files.createDirectories(Paths.get(thisNode.deploymentPath, "tmp")) // TODO: delete on shutdown
        tempOutputPath = Files.createTempDirectory(tempRoot, "output")
    }

    private val globalLock = Any()
    @GuardedBy("globalLock") private var operator: Thread? = null
    private val looperShutdownSignaller = CountDownLatch(1)
    private val delayedExceptions = Collections.synchronizedList(mutableListOf<Throwable>())

    @Serializable
    class NodeInfo(
        val devices: List<RemoteDevice>,
        val testCases: List<RemoteTestCase>
    )

    fun init(): NodeInfo = synchronized(globalLock) {
        require(operator == null) { "The node is already initialized" }

        val devices = CompletableDeferred<Set<Device>>()
        val testCases = CompletableDeferred<Set<TestCase>>()

        operator = thread(start = true) {
            try {
                val configuration = Configuration.Builder().apply {
                    setupCommonTongsConfiguration(config)
                    applyLocalNodeConfiguration(config)
                    withPoolingStrategy(
                        nodeDevicesStrategy(
                            config.mergedConfigWithInjectedVars.nodes,
                            additionalSerials = loopingDevices.map { it.serial }
                        )
                    )
                    withOutput(tempOutputPath.toFile())
                }
                    .build(true)
                    .apply {
                        pluginsInstances.apply {
                            clear()
                            addAll(
                                listOf(
                                    TestCaseCollectingPlugin(testCases::complete),
                                    DeviceCollector(devices::complete),
                                    LoopingDeviceProviderFactory(),
                                    LoopingTestCaseProviderFactory(),
                                    LoopingTestCaseRunnerFactory(looperShutdownSignaller)
                                )
                            )
                        }
                    }
                Tongs(configuration).run(allowThrows = true)
            } catch (e: Throwable) {
                devices.completeExceptionally(e) ||
                    testCases.completeExceptionally(e) ||
                    delayedExceptions.add(e)
            }
        }

        return@synchronized runBlocking {
            // TODO: interrupt operator thread on cancel

            NodeInfo(
                devices.await()
//                    .filter { it != LoopingDevice }
                    .map(RemoteDevice::fromLocalDevice),
                testCases.await().map { RemoteTestCase.fromTestCase(it) }
            )
        }
    }

    fun shutdown(): Unit = synchronized(globalLock) {
        try {
            looperShutdownSignaller.countDown()
            operator?.apply {
                join(15_000)
                if (isAlive) {
                    interrupt()
                    join(15_000)
                }
            }
        } finally {
            nodeShutdownSignaller.countDown()
        }
    }

    @Serializable
    data class RunTest(
        val deviceId: String,
        val testCase: String
    )

    fun runTest(params: RunTest): TestRunResult {
        TODO()
    }
}

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
