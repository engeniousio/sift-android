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
import com.android.utils.toSystemLineSeparator
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.suite.ApkTestCase
import org.junit.Test
import kotlin.test.asserter

class ResultListenerTest {
    private val event = TestCaseEvent(
        TestCase(
                ApkTestCase::class.java,
                "com.test",
                "com.test.Test",
                "test",
                emptyList(),
                emptyMap(),
                emptyList(),
                null,
                Any()
        ),
        emptyList(),
        0
    )
    private val test = TestIdentifier(event.testClass, event.testMethod)
    private val resultListener = ResultListener("runName")
    private val listener = RunListenerAdapter("runName", test, listOf(resultListener))

    private val trace = "error trace"

    @Test
    fun normalSuccess() {
        listener.onBeforeTestRunStarted()
        listener.testRunStarted("run", 1)
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testRunEnded(1000, emptyMap())
        listener.onAfterTestRunEnded()

        val result = resultListener.result
        asserter.assertEquals(null, ResultStatus.PASS, result.status)
        asserter.assertEquals(null, "", result.output)
        asserter.assertEquals(null, "", result.trace)
    }

    @Test
    fun normalFailure() {
        listener.onBeforeTestRunStarted()
        listener.testRunStarted("run", 1)
        listener.testStarted(test)
        listener.testFailed(test, trace)
        listener.testEnded(test, emptyMap())
        listener.testRunEnded(1000, emptyMap())
        listener.onAfterTestRunEnded()

        val result = resultListener.result
        asserter.assertEquals(null, ResultStatus.FAIL, result.status)
        asserter.assertEquals(null, "", result.output)
        asserter.assertEquals(null, trace, result.trace)
    }

    @Test
    fun normalError() {
        listener.onBeforeTestRunStarted()
        listener.testRunStarted("run", 1)
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testRunFailed("runFailure")
        listener.testRunEnded(1000, emptyMap())
        listener.onAfterTestRunEnded()

        val result = resultListener.result
        asserter.assertEquals(null, ResultStatus.ERROR, result.status)
        asserter.assertEquals(null, "runFailure", result.output)
        asserter.assertEquals(null, "", result.trace)
    }

    @Test
    fun emptyRun() {
        listener.onBeforeTestRunStarted()
        listener.testRunStarted("run", 0)
        listener.testRunEnded(1000, emptyMap())
        listener.onAfterTestRunEnded()

        val result = resultListener.result
        asserter.assertEquals(null, ResultStatus.ERROR, result.status)
        asserter.assertEquals(null, "No expected tests were found", result.output)
        asserter.assertEquals(null, "", result.trace)
    }

    @Test
    fun normalMultipleSuccess() {
        listener.onBeforeTestRunStarted()
        listener.testRunStarted("run", 3)
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testRunEnded(1000, emptyMap())
        listener.onAfterTestRunEnded()

        val result = resultListener.result
        asserter.assertEquals(null, ResultStatus.PASS, result.status)
        asserter.assertEquals(null, "", result.output)
        asserter.assertEquals(null, "", result.trace)
    }

    @Test
    fun singleFailureInMultipleTests() {
        listener.onBeforeTestRunStarted()
        listener.testRunStarted("run", 5)
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testFailed(test, trace)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testRunEnded(1000, emptyMap())
        listener.onAfterTestRunEnded()

        val result = resultListener.result
        asserter.assertEquals(null, ResultStatus.FAIL, result.status)
        asserter.assertEquals(null, "", result.output)
        asserter.assertEquals(null, trace, result.trace)
    }

    @Test
    fun multipleFailuresInMultipleTests() {
        listener.onBeforeTestRunStarted()
        listener.testRunStarted("run", 5)
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testFailed(test, trace)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testFailed(test, trace)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testRunEnded(1000, emptyMap())
        listener.onAfterTestRunEnded()

        val result = resultListener.result
        asserter.assertEquals(null, ResultStatus.FAIL, result.status)
        asserter.assertEquals(null, "", result.output)
        asserter.assertEquals(
                null,
                trace + System.lineSeparator().repeat(2) + trace,
                result.trace.toSystemLineSeparator()
        )
    }

    @Test
    fun differentFailuresInMultipleTests1() {
        listener.onBeforeTestRunStarted()
        listener.testRunStarted("run", 2)
        listener.testStarted(test)
        listener.testAssumptionFailure(test, trace)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testIgnored(test)
        listener.testEnded(test, emptyMap())
        listener.testRunEnded(1000, emptyMap())
        listener.onAfterTestRunEnded()

        val result = resultListener.result
        asserter.assertEquals(null, ResultStatus.ASSUMPTION_FAILED, result.status)
        asserter.assertEquals(null, "", result.output)
        asserter.assertEquals(null, trace, result.trace)
    }

    @Test
    fun differentFailuresInMultipleTests2() {
        listener.onBeforeTestRunStarted()
        listener.testRunStarted("run", 5)
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testAssumptionFailure(test, trace)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testFailed(test, trace)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testIgnored(test)
        listener.testEnded(test, emptyMap())
        listener.testStarted(test)
        listener.testEnded(test, emptyMap())
        listener.testRunEnded(1000, emptyMap())
        listener.onAfterTestRunEnded()

        val result = resultListener.result
        asserter.assertEquals(null, ResultStatus.FAIL, result.status)
        asserter.assertEquals(null, "", result.output)
        asserter.assertEquals(
                null,
                trace + System.lineSeparator().repeat(2) + trace,
                result.trace.toSystemLineSeparator()
        )
    }
}