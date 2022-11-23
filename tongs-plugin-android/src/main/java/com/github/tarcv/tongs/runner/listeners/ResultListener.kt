/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.runner.listeners

import com.android.ddmlib.testrunner.TestIdentifier
import com.github.tarcv.tongs.api.run.ResultStatus
import org.slf4j.LoggerFactory
import javax.annotation.concurrent.GuardedBy
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
class ResultListener(private val runName: String) : RunListener {

    private val lock = Any()

    @field:GuardedBy("lock") public var result: ShellResult = ShellResult()
        get() = synchronized(lock) { field }
        private set

    data class ShellResult(
            val status: ResultStatus? = null,
            val output: String = "",
            val metrics: Map<String, String> = emptyMap(),
            val trace: String = "",
            val startTime: Long? = null,
            val endTime: Long? = null
    )

    override fun onRunStarted() {
        synchronized(lock) {
            result = result.copy(startTime = System.currentTimeMillis())
        }
    }

    override fun onRunFinished() {
        synchronized(lock) {
            if (result.startTime != null) {
                result = result.copy(endTime = System.currentTimeMillis())
            }
        }
    }

    override fun onTestFinished(testIdentifier: TestIdentifier, resultStatus: ResultStatus, trace: String, hasStarted: Boolean) {
        synchronized(lock) {
            val newStatus = result.status.let {
                if (it == null) {
                    resultStatus
                } else if (it.overrideCompareTo(resultStatus) >= 0) {
                    it
                } else {
                    resultStatus
                }
            }

            appendTrace(trace)

            result = result.copy(status = newStatus)
        }
    }

    override fun addTestMetrics(testIdentifier: TestIdentifier, testMetrics: Map<String, String>, hasStarted: Boolean) {
        synchronized(lock) {
            mergeMetrics(testMetrics)
        }
    }

    override fun onRunFailure(errorMessage: String) {
        synchronized(lock) {
            result = result.copy(status = ResultStatus.ERROR)

            if (errorMessage.isNotEmpty()) {
                appendOutput(errorMessage)
            }
        }
    }

    override fun addRunData(runOutput: String, runMetrics: Map<String, String>) {
        synchronized(lock) {
            appendOutput(runOutput)
            mergeMetrics(runMetrics)
        }
    }

    @GuardedBy("lock")
    private fun appendTrace(trace: String) {
        if (trace.isEmpty()) return

        val newTrace = result.trace.let {
            if (it.isNullOrBlank()) {
                trace
            } else {
                it + "\n\n" + trace
            }
        }
        result = result.copy(trace = newTrace)
    }

    @GuardedBy("lock")
    private fun appendOutput(output: String) {
        if (output.isEmpty()) return

        result = result.copy(output = result.output.appendBlock(output))
    }

    private fun String.appendBlock(block: String): String {
        val newString = let {
            if (it.isNullOrBlank()) {
                block
            } else {
                it + "\n\n" + block
            }
        }
        return newString
    }

    @GuardedBy("lock")
    private fun mergeMetrics(testMetrics: Map<String, String>) {
        val metrics = result.metrics.let {
            if (it.isNullOrEmpty()) {
                testMetrics
            } else {
                // TODO: implement merging
                it + testMetrics
            }
        }
        result = result.copy(metrics = metrics)
    }

    private enum class State {
        BEFORE_RUN,
        RUN_STARTED_OR_TEST_ENDED,
        TEST_STARTED,
        RUN_ENDED,
        RUN_FAILED
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResultListener::class.java)
    }
}