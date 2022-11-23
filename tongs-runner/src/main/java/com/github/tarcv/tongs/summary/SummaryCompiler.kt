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
package com.github.tarcv.tongs.summary

import com.github.tarcv.tongs.api.TongsConfiguration
import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Diagnostics
import com.github.tarcv.tongs.api.devices.DisplayGeometry
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.StackTrace
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.ResultStatus.Companion.isFailure
import com.github.tarcv.tongs.api.run.ResultStatus.Companion.isIgnored
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.summary.Summary.Builder.Companion.aSummary
import java.time.Instant

class SummaryCompiler(private val configuration: TongsConfiguration) {
    fun compileSummary(pools: Collection<Pool>, testCasesPerPool: Map<Pool, Collection<TestCaseEvent>>, results: List<TestCaseRunResult>): Summary {
        val summaryBuilder = aSummary()
        summaryBuilder.addResults(results)

        results
                .groupBy(TestCaseRunResult::pool)
                .forEach { pool, testResultsForPool ->
                    // keep results only for final attempts
                    val finalResults = testResultsForPool
                            .asReversed()
                            .distinctBy { it.testCase }
                            .reversed()

                    val poolSummary = PoolSummary.Builder.aPoolSummary()
                            .withPoolName(pool.name)
                            .addTestResults(finalResults)
                            .build()
                    summaryBuilder.addPoolSummary(poolSummary)

                    val testCasesForPool = testCasesPerPool.getValue(pool)
                    addFatalCrashedTests(pool, testCasesForPool, finalResults, summaryBuilder)
                    addFailedOrCrashedTests(finalResults, summaryBuilder)
                    addIgnoredTests(finalResults, summaryBuilder)
                }
        addFatalCrashedPools(pools, testCasesPerPool, summaryBuilder)

        summaryBuilder.withTitle(configuration.title)
        summaryBuilder.withSubtitle(configuration.subtitle)
        return summaryBuilder.build()
    }

    companion object {
        private fun addFatalCrashedPools(pools: Collection<Pool>, testCases: Map<Pool, Collection<TestCaseEvent>>, summaryBuilder: Summary.Builder) {
            (pools.toSet() - testCases.keys)
                    .forEach({ pool: Pool -> summaryBuilder.addFatalError("Pool " + pool.name + " not executed") })
        }

        private fun addFailedOrCrashedTests(testResultsForPool: Collection<TestCaseRunResult>, summaryBuilder: Summary.Builder) {
            for (testResult in testResultsForPool) {
                val totalFailureCount = testResult.totalFailureCount
                if (totalFailureCount > 0) {
                    if (isFailure(testResult.status)) {
                        summaryBuilder.addFailedTests(testResult)
                    } else {
                        summaryBuilder.addFlakyTest(testResult)
                    }
                } else if (isFailure(testResult.status)) {
                    // totalFailureCount of 0 here means something went wrong and this is actually a fatal crash
                    // TODO: handle this in a way that makes sure testResult.status == ERROR from plugins POV
                    summaryBuilder.addFatalCrashedTest(testResult)
                }
            }
        }

        private fun addFatalCrashedTests(pool: Pool, testCasesForPool: Collection<TestCaseEvent>, testResultsForPool: Collection<TestCaseRunResult>, summaryBuilder: Summary.Builder) {
            val processedTests = testResultsForPool
                    .map(TestCaseRunResult::testCase)
                    .toSet()
            val allTests = testCasesForPool
                    .map(TestCaseEvent::testCase)
                    .toSet()
            (allTests - processedTests)
                    .map { testResultItem: TestCase? ->
                        TestCaseRunResult(pool, NO_DEVICE,
                                testResultItem!!, ResultStatus.ERROR, listOf(StackTrace("FatalError", "Fatally crashed", "Fatally crashed")),
                                Instant.now(), Instant.EPOCH, Instant.now(), Instant.EPOCH,
                                0, emptyMap(), null, emptyList())
                    }
                    .let { summaryBuilder.addFatalCrashedTests(it) }
        }

        private fun addIgnoredTests(ignoredTestResults: Collection<TestCaseRunResult>, summaryBuilder: Summary.Builder) {
            ignoredTestResults
                    .filter { (_, _, _, status) ->
                        // TODO: check ASSUMPTION_FAILED eventually executed on some device are not considered skipped
                        isIgnored(status)
                    }
                    .let { summaryBuilder.addIgnoredTests(it) }
        }

        private val NO_DEVICE: Device = object : Device() {
            private val uniqueIdentifier = Any()
            override fun getHost(): String {
                return "N/A"
            }

            override fun getSerial(): String {
                return "N/A"
            }

            override fun getManufacturer(): String {
                return "-"
            }

            override fun getModelName(): String {
                return "No Device"
            }

            override fun getOsApiLevel(): Int {
                return 0
            }

            override fun getLongName(): String {
                return "No Device"
            }

            override fun getDeviceInterface(): Any {
                return Any()
            }

            override fun isTablet(): Boolean {
                return false
            }

            override fun getGeometry(): DisplayGeometry? {
                return DisplayGeometry(300)
            }

            override fun getSupportedVisualDiagnostics(): Diagnostics {
                return Diagnostics.NONE
            }

            override fun getUniqueIdentifier(): Any {
                return uniqueIdentifier
            }
        }
    }
}