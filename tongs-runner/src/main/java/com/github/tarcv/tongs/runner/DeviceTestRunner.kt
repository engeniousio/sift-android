/*
 * Copyright 2021 TarCV
 * Copyright 2014 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.runner

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.Delegate
import com.github.tarcv.tongs.api.result.StackTrace
import com.github.tarcv.tongs.api.result.TestCaseFileManager
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.DeviceRunRuleContext
import com.github.tarcv.tongs.api.run.DeviceRunRuleFactory
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.run.TestCaseRunRuleAfterArguments
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.run.TestCaseRunRuleFactory
import com.github.tarcv.tongs.api.run.TestCaseRunnerArguments
import com.github.tarcv.tongs.injector.ActualConfiguration
import com.github.tarcv.tongs.injector.RuleManagerFactory
import com.github.tarcv.tongs.injector.withRulesWithoutAfter
import com.github.tarcv.tongs.model.TestCaseEventQueue
import com.github.tarcv.tongs.runner.listeners.TestRunListenersFactoryTongs
import com.github.tarcv.tongs.system.io.FileManager
import com.github.tarcv.tongs.system.io.TestCaseFileManagerImpl
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Instant
import java.util.concurrent.CountDownLatch

class DeviceTestRunner(private val pool: Pool,
                       private val device: Device,
                       private val ruleManagerFactory: RuleManagerFactory
) {
    private val rules = ruleManagerFactory.create(DeviceRunRuleFactory::class.java,
            listOf(AndroidSetupDeviceRuleFactory()),
            { factory, context: DeviceRunRuleContext -> factory.deviceRules(context) }
    ).createRulesFrom { configuration -> DeviceRunRuleContext(configuration, pool, device) }

    fun run(
            queueOfTestsInPool: TestCaseEventQueue,
            deviceCountDownLatch: CountDownLatch,
            progressReporter: ProgressReporter
    ) {
        try {
            try {
                while (true) {
                    val testCaseTask = queueOfTestsInPool.pollForDevice(device, 10)
                    if (testCaseTask != null) {
                        testCaseTask.doWork { testCaseEvent: TestCaseEvent ->
                            val startTimestampUtc = Instant.now()
                            try {
                                runEvent(testCaseEvent, startTimestampUtc, progressReporter, queueOfTestsInPool)
                                        .fixRunResult(testCaseEvent, startTimestampUtc)
                                        .copy(endTimestampUtc = Instant.now())
                            } catch (e: Exception) {
                                fatalErrorResult(testCaseEvent, e, startTimestampUtc)
                            }
                        }
                    } else if (queueOfTestsInPool.hasNoPotentialEventsFor(device)) {
                        break
                    }
                }
            } finally {
                runAfterRules()
            }
        } finally {
            logger.info("Device {} from pool {} finished", device.serial, pool.name)
            deviceCountDownLatch.countDown()
        }
    }

    private fun runEvent(
            testCaseEvent: TestCaseEvent,
            startTimestampUtc: Instant,
            progressReporter: ProgressReporter,
            queueOfTestsInPool: TestCaseEventQueue
    ): TestCaseRunResult {
        val fileManager by GlobalContext.get().inject<FileManager>()
        val testCaseFileManager: TestCaseFileManager =
     TestCaseFileManagerImpl(fileManager, pool, device, testCaseEvent.testCase)
        val configuration = get<Configuration>(Configuration::class.java)

        val testRunListenersTongsFactory by GlobalContext.get().inject<TestRunListenersFactoryTongs>()
        val testRunListeners = testRunListenersTongsFactory.createTongsListners(
            testCaseEvent,
            device,
            pool,
            progressReporter,
            queueOfTestsInPool,
            configuration.tongsIntegrationTestRunType
        )
            .toList()

        val ruleManager = ruleManagerFactory.create(
                TestCaseRunRuleFactory::class.java,
                listOf(
                        AndroidBasicUnlockTestCaseRunRuleFactory(), // must be executed BEFORE any UI actions
                        AndroidCleanupTestCaseRunRuleFactory(),
                        AndroidPermissionGrantingTestCaseRunRuleFactory() // must be executed AFTER the clean rule
                ),
                { factory, context: TestCaseRunRuleContext -> factory.testCaseRunRules(context) }
        )
        val testCaseRunRules = ruleManager.createRulesFrom { pluginConfiguration ->
            TestCaseRunRuleContext(
                    pluginConfiguration, testCaseFileManager,
                    pool, device, testCaseEvent, startTimestampUtc)
        }

        val inRuleText = "while executing a test case run rule"

        val (allowedAfterRules, eitherResult) = withRulesWithoutAfter(
                logger,
                inRuleText,
                "while executing a test case",
                (testRunListeners + testCaseRunRules),
                { it.before() },
                {
                    val executeContext = TestCaseRunRuleContext(
                            ActualConfiguration(configuration), testCaseFileManager,
                            pool, device, testCaseEvent, startTimestampUtc)

                    runUntilResult(executeContext)
                            .let {
                                it.copy(
                                        startTimestampUtc = executeContext.startTimestampUtc,
                                        baseTotalFailureCount = executeContext.testCaseEvent.totalFailureCount,
                                        additionalProperties = combineProperties(executeContext.testCaseEvent, it.additionalProperties)
                                )
                            }
                            .fixRunResult(testCaseEvent, startTimestampUtc)
                }
        )

        val fixedResult = eitherResult
                .getOrElse { e ->
                    logger.error("Exception while executing a test case", e)
                    fatalErrorResult(testCaseEvent, e, startTimestampUtc)
                }

        return allowedAfterRules
                .asReversed()
                .fold(fixedResult) { acc, rule ->
                    try {
                        val args = TestCaseRunRuleAfterArguments(acc)

                        rule.after(args)
                        args.result
                                .fixRunResult(
                                    testCaseEvent,
                                    startTimestampUtc
                                )
                    } catch (e: Exception) {
                        val header = "Exception ${inRuleText} (after)"
                        val newStackTrace = "$header: ${traceAsString(e)}"
                        acc.copy(
                                status = ResultStatus.ERROR,
                                stackTraces = acc.stackTraces + StackTrace("RuleException", header, newStackTrace)
                        )
                    }
                }
    }

    private fun fatalErrorResult(testCaseEvent: TestCaseEvent, error: Throwable, startTimestampUtc: Instant): TestCaseRunResult {
        return TestCaseRunResult(
                pool, device,
                testCaseEvent.testCase, ResultStatus.ERROR,
                listOf(StackTrace(error.javaClass.typeName, error.message
                        ?: "", traceAsString(error))),
                startTimestampUtc,
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                0,
                combineProperties(testCaseEvent, emptyMap()),
                null,
                emptyList())
    }

    private fun TestCaseRunResult.fixRunResult(
        testCaseEvent: TestCaseEvent,
        startTimestampUtc: Instant
    ): TestCaseRunResult = copy(
        pool = this@DeviceTestRunner.pool,
        device = this@DeviceTestRunner.device,
        testCase = testCaseEvent.testCase,
        startTimestampUtc = startTimestampUtc,
        baseTotalFailureCount = baseTotalFailureCount.coerceAtLeast(testCaseEvent.totalFailureCount)
    )

    private fun runUntilResult(context: TestCaseRunRuleContext): TestCaseRunResult {
        return try {
            context.testCaseEvent.runnersFor(context.device)
                    .asReversed()
                    .forEach {
                        val result = it.run(TestCaseRunnerArguments(
                                context.fileManager,
                                context.testCaseEvent,
                                context.startTimestampUtc
                        ))
                        when (result) {
                            is Delegate -> { /* continue */
                            }
                            is TestCaseRunResult -> return result
                            else -> throw IllegalArgumentException("Unexpected test run result: $result")
                        }
                    }
            throw IllegalStateException("All runners delegated running the test case (no runner to actually execute it")
        } catch (e: Exception) {
            fatalErrorResult(context.testCaseEvent, e, context.startTimestampUtc)
        }
    }

    fun runBeforeRules() {
        rules.forEach { it.before() }
    }

    private fun runAfterRules() {
        // TODO: execute only successful rules
        rules
                .asReversed()
                .forEach { it.after() }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeviceTestRunner::class.java)

        private fun combineProperties(
                testCaseEvent: TestCaseEvent,
                additionalProperties: Map<String, String>
        ): Map<String, String> {
            return testCaseEvent.testCase.properties + additionalProperties
        }

        private fun traceAsString(e: Throwable): String {
            val byteStream = ByteArrayOutputStream()
            PrintStream(BufferedOutputStream(byteStream), false, Charsets.UTF_8.name()).use { printStream ->
                e.printStackTrace(printStream)
            }
            return byteStream.toString()
        }
    }

}
