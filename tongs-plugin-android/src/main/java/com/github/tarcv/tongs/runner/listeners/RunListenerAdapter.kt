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

import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.TestIdentifier
import com.github.tarcv.tongs.api.run.ResultStatus
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.concurrent.GuardedBy
import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.withLock

@ThreadSafe
class RunListenerAdapter(private val runName: String,
                         private val expectedTest: TestIdentifier,
                         private val listeners: List<RunListener>) : ITestRunListener, FullTestRunListener {

    private val lock = ReentrantLock()

    @GuardedBy("lock")
    private var instrumentationRunGotMetrics = false
        get() = assertSynchronized(field)

    @GuardedBy("lock")
    private var instrumentationRunActive = true
        get() = assertSynchronized(field)

    @GuardedBy("lock")
    private var testState: TestState = TestState.BEFORE_START
        get() = assertSynchronized(field)

    @GuardedBy("lock")
    private var gotExpectedTestResult = false
        get() = assertSynchronized(field)

    private inline fun <T>assertSynchronized(field: T): T {
        assert(lock.isHeldByCurrentThread)
        return field
    }

    fun onBeforeTestRunStarted() {
        lock.withLock {
            if (!checkRunActive()) return

            fireEvent(RunListener::onRunStarted)
        }
    }

    override fun testRunStarted(runName: String?, testCount: Int) {
        // no op, onBeforeTestRunStarted is used instead
    }

    override fun testRunFailed(errorMessage: String) {
        lock.withLock {
            if (!checkRunActive()) return

            fireEvent { it.onRunFailure(errorMessage) }
        }
    }

    override fun testRunStopped(elapsedTime: Long) {}

    override fun testRunEnded(elapsedTime: Long, runMetrics: Map<String, String>?) {
        testRunEnded(elapsedTime, "", runMetrics)
    }

    override fun testRunEnded(elapsedTime: Long, output: String, runMetrics: Map<String, String>?) {
        lock.withLock {
            if (!checkRunActive()) return

            if (instrumentationRunGotMetrics) {
                testRunFailed("Got instrumentation run metrics twice (or more times)")
            } else {
                fireEvent { it.addRunData(output, runMetrics ?: emptyMap()) }
                instrumentationRunGotMetrics = true
            }
        }
    }

    fun onAfterTestRunEnded() {
        lock.withLock {
            if (!checkRunActive()) return

            if (!gotExpectedTestResult) {
                testRunFailed("No expected tests were found")
            }
            fireEvent(RunListener::onRunFinished)

            instrumentationRunActive = false
        }
    }

    override fun testStarted(test: TestIdentifier) {
        handleTestEvent(TestEvent.TestStarted(test))
    }

    override fun testFailed(test: TestIdentifier, trace: String) {
        handleTestEvent(TestEvent.TestFailed(test, ResultStatus.FAIL, trace))
    }

    override fun testAssumptionFailure(test: TestIdentifier, trace: String) {
        handleTestEvent(TestEvent.TestFailed(test, ResultStatus.ASSUMPTION_FAILED, trace))
    }

    override fun testIgnored(test: TestIdentifier) {
        handleTestEvent(TestEvent.TestFailed(test, ResultStatus.IGNORED, ""))
    }

    override fun testEnded(test: TestIdentifier, testMetrics: Map<String, String>) {
        handleTestEvent(TestEvent.TestEnded(test, testMetrics))
    }

    @GuardedBy("lock")
    private inline fun fireEvent(block: (RunListener) -> Unit) {
        if (!checkRunActive()) return

        listeners.forEach {
            try {
                block(it)
            } catch (e: Exception) {
                logger.warn("${it.javaClass.name} failure", e)
            }
        }
    }

    @GuardedBy("lock")
    private fun checkRunActive(): Boolean {
        if (!instrumentationRunActive) {
            logger.warn("Got instrumentation run event after the run was finished")
            return false
        }
        return true
    }

    private fun handleTestEvent(newEvent: TestEvent) {
        lock.withLock {
            if (!checkRunActive()) return

            fun handleUnexpectedTest(event: TestEvent) {
                testRunFailed("Test execution got into the unexpected state ($event when $testState)")
            }

            val eventTest = when (newEvent) {
                is TestEvent.TestStarted -> newEvent.testIdentifier
                is TestEvent.TestFailed -> newEvent.testIdentifier
                is TestEvent.TestEnded -> newEvent.testIdentifier
                else -> return
            }

            val event = if (eventTest != expectedTest) {
                TestEvent.OtherTest
            } else {
                newEvent
            }

            testState = testState.let { state ->
                when (event) {
                    is TestEvent.OtherTest -> {
                        if (state != TestState.BEFORE_START) {
                            handleUnexpectedTest(event)
                        }
                        TestState.BEFORE_START
                    }
                    is TestEvent.TestStarted -> {
                        if (state != TestState.BEFORE_START) {
                            handleUnexpectedTest(event)
                        }
                        TestState.TEST_STARTED
                    }
                    is TestEvent.TestFailed -> {
                        (state is TestState.TEST_STARTED).let { gotStart ->
                            if (!gotStart) {
                                handleUnexpectedTest(event)
                            }

                            gotExpectedTestResult = true
                            fireEvent {
                                it.onTestFinished(event.testIdentifier, event.resultStatus, event.trace, gotStart)
                            }
                            TestState.TEST_FAILED(gotStart)
                        }
                    }
                    is TestEvent.TestEnded -> {
                        val gotStart = when (state) {
                            is TestState.TEST_STARTED -> true
                            is TestState.TEST_FAILED -> state.gotStart
                            is TestState.BEFORE_START -> false
                        }
                        if (!gotStart) {
                            handleUnexpectedTest(event)
                        }

                        if (state is TestState.TEST_STARTED) {
                            gotExpectedTestResult = true
                            fireEvent {
                                it.onTestFinished(event.testIdentifier, ResultStatus.PASS, "", gotStart)
                            }
                        }
                        fireEvent {
                            it.addTestMetrics(event.testIdentifier, event.testMetrics, gotStart)
                        }

                        TestState.BEFORE_START
                    }
                }
            }
        }
    }

    private sealed class TestState {
        object TEST_STARTED: TestState()
        object BEFORE_START: TestState()
        class TEST_FAILED(val gotStart: Boolean): TestState()
    }

    private sealed class TestEvent {
        class TestStarted(val testIdentifier: TestIdentifier) : TestEvent()
        class TestFailed(
                val testIdentifier: TestIdentifier,
                val resultStatus: ResultStatus,
                val trace: String
        ) : TestEvent()

        class TestEnded(
                val testIdentifier: TestIdentifier,
                val testMetrics: Map<String, String>
        ) : TestEvent()

        object OtherTest : TestEvent()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RunListenerAdapter::class.java)
    }
}

interface RunListener {
    fun onRunStarted()

    fun onTestFinished(testIdentifier: TestIdentifier, resultStatus: ResultStatus, trace: String, hasStarted: Boolean)

    fun onRunFailure(errorMessage: String)
    fun onRunFinished()

    fun addTestMetrics(testIdentifier: TestIdentifier, testMetrics: Map<String, String>, hasStarted: Boolean)
    fun addRunData(runOutput: String, runMetrics: Map<String, String>)

}