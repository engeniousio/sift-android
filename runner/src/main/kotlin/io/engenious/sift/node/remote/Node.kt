package io.engenious.sift.node.remote

import com.android.annotations.concurrency.GuardedBy
import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.Tongs
import com.github.tarcv.tongs.TongsRunner
import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.run.TestCaseRunner
import com.github.tarcv.tongs.api.run.TestCaseRunnerContext
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.injector.TestCaseRunnerManager
import com.github.tarcv.tongs.model.TestCaseEventQueue
import io.engenious.sift.Config
import io.engenious.sift.applyLocalNodeConfiguration
import io.engenious.sift.node.changePropertyField
import io.engenious.sift.node.extractProperty
import io.engenious.sift.node.remote.hooks.CollectingPoolTestRunnerFactory
import io.engenious.sift.node.remote.plugins.DeviceCollectingPlugin
import io.engenious.sift.node.remote.plugins.ResultListeningPlugin
import io.engenious.sift.node.remote.plugins.RunListeningPlugin
import io.engenious.sift.node.remote.plugins.TestCaseCollectingPlugin
import io.engenious.sift.node.remote.plugins.blocker.LoopingDevice
import io.engenious.sift.node.remote.plugins.blocker.LoopingDeviceProvider.Companion.loopingDevices
import io.engenious.sift.node.remote.plugins.blocker.LoopingDeviceProviderFactory
import io.engenious.sift.node.remote.plugins.blocker.LoopingTestCaseProvider.Companion.loopingTestCase
import io.engenious.sift.node.remote.plugins.blocker.LoopingTestCaseProviderFactory
import io.engenious.sift.node.remote.plugins.blocker.LoopingTestCaseRunnerFactory
import io.engenious.sift.node.serialization.RemoteDevice
import io.engenious.sift.node.serialization.RemoteTestCase
import io.engenious.sift.node.serialization.RemoteTestCaseRunResult
import io.engenious.sift.node.serialization.deviceIdentifierAsString
import io.engenious.sift.nodeDevicesStrategy
import io.engenious.sift.setupCommonTongsConfiguration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.koin.core.context.KoinContextHandler
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class Node(
    private val config: Config.WithInjectedCentralAndNodeVars,
    private val nodeShutdownSignaller: CountDownLatch
) {
    private val tempOutputPath: Path
    init {
        val thisNode = config.nodes.single()
        val tempRoot = Files.createDirectories(Paths.get(thisNode.deploymentPath, "tmp")) // TODO: delete on shutdown
        tempOutputPath = Files.createTempDirectory(tempRoot, "output")
    }

    private val globalLock = Any()
    @GuardedBy("globalLock") private var operator: Thread? = null
    @GuardedBy("globalLock") private val runnerCache: MutableMap<Device, TestCaseRunner> = HashMap()
    @GuardedBy("globalLock") private lateinit var myInfo: NodeInfo
    private val looperShutdownSignaller = CountDownLatch(1)
    private val delayedExceptions = Collections.synchronizedList(mutableListOf<Throwable>())
    private val pool = CompletableDeferred<Pool>()
    private val devices = CompletableDeferred<Set<Device>>()
    private val testCases = CompletableDeferred<Set<TestCase>>()
    private val testQueue = CompletableDeferred<TestCaseEventQueue>()
    private val testTaskCounter = AtomicInteger()
    private val testCaseRunnerManager by lazy {
        @Suppress("UNCHECKED_CAST")
        KoinContextHandler.get().get<TongsRunner>()
            .extractProperty("testCaseRunnerManager") as TestCaseRunnerManager
    }
    private val testResultContainer = Collections.synchronizedMap(mutableMapOf<String, TestCaseRunResult>())
    private val originalTestCases = Collections.synchronizedMap(mutableMapOf<String, TestCase>())

    companion object {
        private const val siftEventIndexKey = "__siftEventIndexKey"
        private val logger = LoggerFactory.getLogger(Node::class.java)
    }

    @Serializable
    class NodeInfo(
        val devices: List<RemoteDevice>,
        val testCases: List<RemoteTestCase>
    )

    @ExperimentalCoroutinesApi
    fun init(): NodeInfo = synchronized(globalLock) {
        if (operator != null) {
            return myInfo
        }

        val operator = thread(start = true) {
            try {
                val configuration = Configuration.Builder().apply {
                    val thisNodeConfig = config.nodes.singleOrNull()
                        ?: throw RuntimeException("Expected to receive exactly one node configuration from the central node")

                    setupCommonTongsConfiguration(config)
                    applyLocalNodeConfiguration(config, thisNodeConfig)
                    withPoolingStrategy(
                        nodeDevicesStrategy(
                            thisNodeConfig,
                            additionalSerials = loopingDevices.map { it.serial }
                        )
                    )
                    withOutput(tempOutputPath.toFile())

                    // The central node manages retries
                    withRetryPerTestCaseQuota(0)
                    withTotalAllowedRetryQuota(0)
                }
                    .build(true)
                    .apply {
                        pluginsInstances.apply {
                            clear()
                            addAll(
                                listOf(
                                    RunListeningPlugin(
                                        onBeforeRun = {
                                            val koin = KoinContextHandler.get()
                                            val factory = CollectingPoolTestRunnerFactory(
                                                koin.get(),
                                                koin.get(),
                                                testQueue::complete
                                            )
                                            koin.get<TongsRunner>()
                                                .changePropertyField("poolTestRunnerFactory", factory)
                                        }
                                    ),
                                    TestCaseCollectingPlugin(testCases::complete),
                                    DeviceCollectingPlugin { collectedPool, collectedDevices ->
                                        pool.complete(collectedPool)
                                        devices.complete(collectedDevices)
                                    },
                                    LoopingDeviceProviderFactory(),
                                    LoopingTestCaseProviderFactory(),
                                    LoopingTestCaseRunnerFactory(looperShutdownSignaller),
                                    ResultListeningPlugin {
                                        val taskId = it.testCase.properties[siftEventIndexKey]
                                        testResultContainer[taskId] = it
                                        logger.info("Received result $taskId from a runner")
                                    }
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

        myInfo = runBlocking {
            // TODO: interrupt operator thread on cancel
            NodeInfo(
                devices.await()
                    .filter { it != LoopingDevice }
                    .map(RemoteDevice::fromLocalDevice),
                testCases.await()
                    .filter { it != loopingTestCase }
                    .map { RemoteTestCase.fromTestCase(it) }
            )
        }

        this.operator = operator
        myInfo
    }

    @Serializable
    data class RunTest(
        val deviceId: String,
        val testCase: String
    ) {
        constructor(device: Device, testCase: TestCase) : this(
            device.deviceIdentifierAsString(),
            testCase.toString()
        )
    }

    @Serializable
    data class TestRunRequestResult(val taskId: String)

    @ExperimentalCoroutinesApi
    fun runTest(params: RunTest): TestRunRequestResult {
        val taskId = synchronized(globalLock) {
            val op = operator
            requireNotNull(op) { "The node is not initialized" }

            val pool = runBlocking {
                pool.await()
            }
            val device = runBlocking {
                devices.await()
                    .singleOrNull { it.deviceIdentifierAsString() == params.deviceId }
                    ?: throw IllegalArgumentException("No such device: ${params.deviceId}")
            }
            val testCase = runBlocking {
                testCases.await()
                    .singleOrNull { it.toString() == params.testCase }
                    ?: throw IllegalArgumentException("No such test case: ${params.testCase}")
            }

            val runner = runnerCache.getOrPut(device) {
                testCaseRunnerManager
                    .createRulesFrom { configuration ->
                        TestCaseRunnerContext(
                            configuration,
                            pool,
                            device
                        )
                    }
                    .lastOrNull { it.supports(device, testCase) }
                    ?: throw IllegalArgumentException("Unknown test case type: $testCase")
            }

            val taskId = testTaskCounter.getAndIncrement().toString()
            originalTestCases[taskId] = testCase
            val testTask = createTestTask(taskId, testCase, device, runner)

            testQueue
                .getCompleted()
                .offer(testTask)

            taskId
        }

        return TestRunRequestResult(taskId)
    }

    private fun createTestTask(
        taskId: String,
        testCase: TestCase,
        device: Device,
        runner: TestCaseRunner
    ): TestCaseEvent {
        return TestCaseEvent(
            TestCase(
                testCase.typeTag,
                testCase.testPackage,
                testCase.testClass,
                testCase.testMethod,
                testCase.readablePath,
                testCase.properties + mapOf(siftEventIndexKey to taskId),
                testCase.annotations,
                setOf(device),
                testCase.extra
            ),
            emptyList(),
            0
        ).apply {
            addDeviceRunner(device, runner)
        }
    }

    @Serializable
    data class TakeRunResult(
        val taskId: String
    )

    @Serializable
    data class TakeRunResultResult(
        val result: RemoteTestCaseRunResult?
    )

    fun takeRunResult(params: TakeRunResult): TakeRunResultResult {
        val result = testResultContainer.remove(params.taskId) ?: return TakeRunResultResult(null)

        val originalTestCase = originalTestCases.remove(params.taskId) ?: throw IllegalStateException()
        return TakeRunResultResult(
            RemoteTestCaseRunResult.fromTestCaseRunResult(
                /*
                        Test case task was created with a modified test case (for proper scheduling),
                        thus this result links to that modified test case.
                        Make it link to the unmodified one instead.
                    */
                result.copy(testCase = originalTestCase)
            )
        )
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
}
