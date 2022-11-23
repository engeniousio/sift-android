/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.runner

import com.android.ddmlib.testrunner.TestIdentifier
import com.android.utils.toSystemLineSeparator
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.run.aTestCaseEvent
import com.github.tarcv.tongs.api.testcases.aTestCase
import com.github.tarcv.tongs.runner.listeners.ResultListener
import com.github.tarcv.tongs.runner.listeners.RunListenerAdapter
import org.junit.Assert.assertEquals
import org.junit.Test

class TongsInstrumentationResultParserTest {
    private val testShortClassName = "ResultTest"
    private val testMethodName = "failureFromEspresso[failAfter = true]"
    private val testCase = TestCaseEvent.aTestCaseEvent(aTestCase(testShortClassName, testMethodName))

    private val listener = ResultListener(testCase.toString())
    private val parser = TongsInstrumentationResultParser(
            "unitTest",
            listOf(RunListenerAdapter(testCase.toString(), TestIdentifier(testCase.testClass,testCase.testMethod), listOf(listener)))
    )

    private val afterMethodException = """java.lang.RuntimeException: Exception from afterMethod
    at ${testCase.testClass}.afterMethod($testShortClassName.java:30)
    at java.lang.reflect.Method.invoke(Native Method)"""

    private val testException = """android.support.test.espresso.NoMatchingViewException: No views in hierarchy found matching: with res-name that is "non_existing_id"
    at dalvik.system.VMStack.getThreadStackTrace(Native Method)
    at java.lang.Thread.getStackTrace(Thread.java:1566)
    at android.support.test.espresso.base.DefaultFailureHandler.getUserFriendlyError(DefaultFailureHandler.java:88)
    at android.support.test.espresso.base.DefaultFailureHandler.handle(DefaultFailureHandler.java:51)
    at android.support.test.espresso.ViewInteraction.waitForAndHandleInteractionResults(ViewInteraction.java:312)
    at android.support.test.espresso.ViewInteraction.check(ViewInteraction.java:297)
    at ${testCase.testClass}.failureFromEspresso($testShortClassName.java:42)
    at java.lang.reflect.Method.invoke(Native Method)"""

    @Test
    fun resultListenerReceivesCorrectShellResult() {
        val stdLines = """INSTRUMENTATION_STATUS: numtests=1
INSTRUMENTATION_STATUS: stream=
${testCase.testClass}:
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: test=$testMethodName
INSTRUMENTATION_STATUS: class=${testCase.testClass}
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: numtests=1
INSTRUMENTATION_STATUS: stream=
Error in $testMethodName(${testCase.testClass}):
$afterMethodException

INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: test=$testMethodName
INSTRUMENTATION_STATUS: class=${testCase.testClass}
INSTRUMENTATION_STATUS: stack=$afterMethodException

INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS_CODE: -2
INSTRUMENTATION_RESULT: stream=

Time: 5.235
There were 2 failures:
1) $testMethodName(${testCase.testClass})
$testException
2) $testMethodName(${testCase.testClass})
$afterMethodException

FAILURES!!!
Tests run: 1,  Failures: 2


INSTRUMENTATION_CODE: -1

""".lines().toTypedArray()

        parser.processNewLines(stdLines)
        parser.done()

        val expectedOutput = """
${testCase.testClass}:
Error in $testMethodName(${testCase.testClass}):
$afterMethodException


Time: 5.235
There were 2 failures:
1) $testMethodName(${testCase.testClass})
$testException
2) $testMethodName(${testCase.testClass})
$afterMethodException

FAILURES!!!
Tests run: 1,  Failures: 2


"""

        val result = listener.result
        assertEquals(ResultStatus.FAIL, result.status)
        assertEquals(
                expectedOutput.trim().toSystemLineSeparator(),
                result.output.trim().toSystemLineSeparator()
        )
    }
}