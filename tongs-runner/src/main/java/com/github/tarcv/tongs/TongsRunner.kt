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
package com.github.tarcv.tongs

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.run.TestCaseRunnerContext
import com.github.tarcv.tongs.api.testcases.NoTestCasesFoundException
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.injector.RuleManagerFactory
import com.github.tarcv.tongs.injector.TestCaseRuleManager
import com.github.tarcv.tongs.injector.TestCaseRunnerManager
import com.github.tarcv.tongs.injector.TestSuiteLoaderSupplier
import com.github.tarcv.tongs.pooling.NoDevicesForPoolException
import com.github.tarcv.tongs.pooling.NoPoolLoaderConfiguredException
import com.github.tarcv.tongs.pooling.PoolLoader
import com.github.tarcv.tongs.runner.DeviceTestRunner
import com.github.tarcv.tongs.runner.DeviceTestRunnerFactory
import com.github.tarcv.tongs.runner.PoolTestRunnerFactory
import com.github.tarcv.tongs.runner.ProgressReporter
import com.github.tarcv.tongs.summary.SummaryGeneratorHook
import com.github.tarcv.tongs.tests.JoiningTestProvider
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch

class TongsRunner(private val poolLoader: PoolLoader,
                  private val poolTestRunnerFactory: PoolTestRunnerFactory,
                  private val progressReporter: ProgressReporter,
                  private val summaryGeneratorHook: SummaryGeneratorHook,
                  private val testCaseRuleManager: TestCaseRuleManager,
                  private val testCaseRunnerManager: TestCaseRunnerManager,
                  private val ruleManagerFactory: RuleManagerFactory
) {
    class PoolTask(
            val pool: Pool,
            val deviceRunners: List<Pair<Device, DeviceTestRunner>>,
            val testCases: List<TestCaseEvent>
    )

    fun run(): Boolean {
        return try {
            throwingRun()
        } catch (e: NoPoolLoaderConfiguredException) {
            logger.error("Configuring devices and pools failed", e)
            false
        } catch (e: NoDevicesForPoolException) {
            logger.error("Configuring devices and pools failed", e)
            false
        } catch (e: NoTestCasesFoundException) {
            logger.error("Error when trying to find test classes", e)
            false
        } catch (e: Exception) {
            logger.error("Error while executing a test run", e)
            false
        }
    }

    fun throwingRun(): Boolean {
        val pools = poolLoader.loadPools()
        val numberOfPools = pools.size
        val poolCountDownLatch = CountDownLatch(numberOfPools)
        val poolExecutor = Utils.namedExecutor(numberOfPools, "PoolExecutor-%d")
        return try {
            val deviceTestRunnerFactory by GlobalContext.get().inject<DeviceTestRunnerFactory>()

            val poolTestCasesMap: Map<Pool, PoolTask> = pools
                .map { pool ->
                    val deviceRunners = pool.devices.map { device ->
                        device to deviceTestRunnerFactory.createDeviceTestRunner(pool, device, ruleManagerFactory)
                    }

                    deviceRunners.forEach { it.second.runBeforeRules() }

                    val testCaseRules = testCaseRuleManager
                        .createRulesFrom { configuration ->
                            TestCaseRuleContext(configuration, pool)
                        }
                    logger.info("RUN pool ${pool.name} devices ${pool.devices}")
                    val testCases = createTestSuiteLoaderForPool(pool)
                        .also {
                            logger.info("RUN all TestCaseEvents ${it.size}")
                            if (it.isEmpty()) {
                                throw NoTestCasesFoundException("No tests cases were found")
                            }
                        }
                        .map { testCaseEvent: TestCaseEvent ->
                            testCaseRules.fold(testCaseEvent) { acc, rule -> rule.transform(acc) }
                        }
                        .filter { testCaseEvent: TestCaseEvent ->
                            testCaseRules.all { rule -> rule.filter(testCaseEvent) }
                        }
                        .also {
                            logger.info("RUN filter testCaseRules $testCaseRules")
                            logger.info("RUN testCaseEvents after filter ${it.size}")
                            if (it.isEmpty()) {
                                throw NoTestCasesFoundException(
                                    "All tests cases were filtered out by test case rules"
                                )
                            }
                        }
                    logger.info("RUN testCases $testCases")
                    logger.info("RUN testCases.size ${testCases.size}")

                    pool.devices.forEach { device ->
                        testCaseRunnerManager
                            .createRulesFrom { configuration ->
                                TestCaseRunnerContext(
                                    configuration,
                                    pool,
                                    device
                                )
                            }
                            .forEach { runner ->
                                testCases.forEach {
                                    if ((!it.isEnabledOn(device)).not() && runner.supports(device, it.testCase)) {
                                        it.addDeviceRunner(device, runner)
                                    }
                                }
                            }
                    }
                    testCases.forEach { testCase ->
                        val hasCompatibleDevice = pool.devices.any { device ->
                            testCase.isEnabledOn(device) && testCase.runnersFor(device).isNotEmpty()
                        }
                        if (!hasCompatibleDevice) {
                            throw IllegalStateException("No runner found for $testCase")
                        }
                    }

                    pool to PoolTask(pool, deviceRunners, testCases)
                }
                .toMap()

            // TODO: check that different sets of test cases in different pools doesn't fail run
            val allResults: List<TestCaseRunResult> = ArrayList()
            summaryGeneratorHook.registerHook(
                pools,
                poolTestCasesMap.mapValues { it.value.testCases },
                allResults
            )

            progressReporter.start()
            for (pool in pools) {
                val poolTask = poolTestCasesMap.getValue(pool)
                val poolTestRunner = poolTestRunnerFactory.createPoolTestRunner(
                    poolTask,
                    allResults, poolCountDownLatch,
                    progressReporter
                )
                poolExecutor.execute(poolTestRunner)
            }
            poolCountDownLatch.await()
            progressReporter.stop()

            val overallSuccess = summaryGeneratorHook.defineOutcome()
            summaryGeneratorHook.unregisterHook()
            logger.info("Overall success: $overallSuccess")

            overallSuccess
        } finally {
            poolExecutor.shutdownNow()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TongsRunner::class.java)

        // TODO: move to a separate file
        @Throws(NoTestCasesFoundException::class)
        fun createTestSuiteLoaderForPool(pool: Pool): Collection<TestCaseEvent> {
            val loaderSupplier = GlobalContext.get().get<TestSuiteLoaderSupplier>()
            return loaderSupplier
                .supply(pool)
                .let {
                    JoiningTestProvider(it, pool)
                }
                .loadTestSuite()
                .map {
                    TestCaseEvent(
                        it,
                        emptyList(),
                        0
                    )
                }
        }
    }

}