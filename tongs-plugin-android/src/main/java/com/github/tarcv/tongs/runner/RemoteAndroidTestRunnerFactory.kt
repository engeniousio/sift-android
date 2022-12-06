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

import com.android.ddmlib.IDevice
import com.android.ddmlib.NullOutputReceiver
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner
import com.android.ddmlib.testrunner.TestIdentifier
import com.github.tarcv.tongs.api.result.SimpleMonoTextReportData
import com.github.tarcv.tongs.api.result.StackTrace
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.runner.listeners.BroadcastingListener
import com.github.tarcv.tongs.system.DdmsUtils
import org.apache.commons.text.StringEscapeUtils
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList

// TODO: Refactor this file

interface IRemoteAndroidTestRunnerFactory {
    fun createRemoteAndroidTestRunner(testPackage: String, testRunner: String, device: IDevice): RemoteAndroidTestRunner
    fun properlyAddInstrumentationArg(runner: RemoteAndroidTestRunner, name: String, value: String)
    fun encodeTestName(name: String): String
}

class RemoteAndroidTestRunnerFactory : IRemoteAndroidTestRunnerFactory {
    override fun createRemoteAndroidTestRunner(testPackage: String, testRunner: String, device: IDevice): RemoteAndroidTestRunner {
        return TongsRemoteAndroidTestRunner(
                testPackage,
                testRunner,
                device)
    }

    override fun properlyAddInstrumentationArg(runner: RemoteAndroidTestRunner, name: String, value: String) {
        DdmsUtils.properlyAddInstrumentationArg(runner, name, value)
    }

    override fun encodeTestName(name: String): String {
        return Base64.getEncoder()
                .encodeToString(name.toByteArray(StandardCharsets.UTF_8))
                .replace("=".toRegex(), "_")
                .replace("\\r|\\n".toRegex(), "")
    }
}

class TestAndroidTestRunnerFactory : IRemoteAndroidTestRunnerFactory {
    override fun createRemoteAndroidTestRunner(testPackage: String, testRunner: String, device: IDevice): RemoteAndroidTestRunner {
        val shouldIncludeApi22Only = device.getProperty("ro.build.version.sdk") == "22"
        return object : RemoteAndroidTestRunner(
                testPackage,
                testRunner,
                device) {
            override fun run(listenersCollection: Collection<ITestRunListener>) {
                // Do not call super.run to avoid compile errors cause by SDK tools version incompatibilities
                device.executeShellCommand(amInstrumentCommand, NullOutputReceiver())
                stubbedRun(listenersCollection)
            }

            private fun stubbedRun(listenersCollection: Collection<ITestRunListener>) {
                val listeners = BroadcastingListener(listenersCollection)

                operator fun Regex.contains(other: String): Boolean = this.matches(other)
                when (val command = withSortedInstrumentedArguments(amInstrumentCommand)) {
                    in logOnlyCommandPattern -> {
                        val filters = logOnlyCommandPattern.matchEntire(command)!!.groupValues[1]
                        val withF2Filter = filters.split(",").contains("com.github.tarcv.test.F2Filter")

                        val testCount: Int
                        val filteredTestIdentifiers: List<String> = testIdentifiers.asSequence()
                                .filter { shouldIncludeApi22Only || !it.contains("#api22Only") }
                                .filter { !withF2Filter || !it.contains("#filteredByF2Filter")}
                                .toList()
                        testCount = filteredTestIdentifiers.size
                        listeners.testRunStarted("emulators", testCount)
                        filteredTestIdentifiers.forEach {
                            listeners.fireTest(it)
                        }
                        listeners.testRunEnded(100, emptyMap())
                    }
                    in TestIdentifierCommandPattern -> {
                        val (filter, testClass, testMethod) =
                                TestIdentifierCommandPattern.matchEntire(command)
                                        ?.groupValues
                                        ?.drop(1) // group 0 is the entire match
                                        ?.map { StringEscapeUtils.unescapeXSI(it) }
                                        ?: throw IllegalStateException()
                        listeners.testRunStarted("emulators", 1)
                        listeners.fireTest("$testClass#$testMethod", functionalTestTestIdentifierDuration)
                        listeners.testRunEnded(functionalTestTestIdentifierDuration, emptyMap())
                    }
                    else -> throw IllegalStateException(
                            "Unexpected command (sorted): $command. R1='$logOnlyCommandPattern', R2='$TestIdentifierCommandPattern'")
                }
            }

            private fun withSortedInstrumentedArguments(command: String): String {
                var matched = false
                return command
                        .replace(Regex("(?:\\s+-e\\s\\S+\\s.+?(?=\\s+-e|\\s+com))+")) { match ->
                            if (matched) throw IllegalStateException("Unexpected command: $command")
                            matched = true
                            match.groupValues[0]
                                    .split(Regex("-e\\s+")).asSequence()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .sorted()
                                    .joinToString("") { " -e $it" }
                        }
            }
        }
    }

    override fun properlyAddInstrumentationArg(runner: RemoteAndroidTestRunner, name: String, value: String) {
        // proper escaping really complicates RemoteAndroidTestRunner#stubbedRun implementation
        //  so skip it here
        runner.addInstrumentationArg(name, value)
    }

    override fun encodeTestName(name: String): String = name

    private fun ITestRunListener.fireTest(TestIdentifier: String, delayMillis: Long = 0) {
        val (className, testName) = TestIdentifier.split("#", limit = 2)
        testStarted(TestIdentifier(className, testName))
        if (delayMillis > 0) {
            Thread.sleep(delayMillis)
        }
        testEnded(TestIdentifier(className, testName), emptyMap())
    }

    companion object {
        private const val testPackageRoot = """com.github.tarcv.test"""

        fun resultForTestCase(testCaseContext: AndroidRunContext, timeStart: Instant): TestCaseRunResult {
            fun MutableList<StackTrace>.addTrace() {
                this += StackTrace(
                        "Exception",
                        "message ${this.size}",
                        "trace"
                )
            }

            val event = testCaseContext.testCaseEvent

            // Conditions are from FunctionalXmlTest#testNegativeXmlsHaveExpectedContent
            val isResultTest = event.testClass == "$testPackageRoot.ResultTest"
            val shouldFail = isResultTest && event.testMethod.startsWith("failure")
            val shouldFailureInAfter = isResultTest && event.testMethod.contains("failAfter = true")
            val shouldSkip = isResultTest && event.testMethod.startsWith("assumptionFailure")

            var status = ResultStatus.PASS
            val stackTraces = ArrayList<StackTrace>()

            if (shouldFail) {
                stackTraces.addTrace()
                status = ResultStatus.FAIL
            }
            if (shouldSkip) {
                stackTraces.addTrace()
                status = ResultStatus.ASSUMPTION_FAILED
            }
            if (shouldFailureInAfter) {
                stackTraces.addTrace()
                status = ResultStatus.FAIL
            }

            val failuresOut = if (stackTraces.isNotEmpty()) {
                "There were ${stackTraces.size} failures:\n" +
                        stackTraces
                                .mapIndexed { index, trace ->
                                    "${index + 1}) ${event.testMethod}(${event.testClass})\n$trace"
                                }
                                .joinToString("\n") +
                        "\n\nFAILURES!!"
            } else if (stackTraces.size == 1) {
                "There was 1 failure:\n" +
                        "1) ${event.testMethod}(${event.testClass})\n" +
                        stackTraces[0].fullTrace +
                        "\n\nFAILURES!!"
            } else {
                "OK"
            }
            val data = listOf(SimpleMonoTextReportData(
                    "System Out",
                    SimpleMonoTextReportData.Type.STDOUT,
                    failuresOut
            ))

            val timeEnd = Instant.now()
            return TestCaseRunResult(
                    testCaseContext.pool,
                    testCaseContext.device,
                    testCaseContext.testCaseEvent.testCase,
                    status,
                    stackTraces.lastOrNull()?.let { listOf(it) } ?: emptyList(),
                    Instant.EPOCH,
                    Instant.EPOCH,
                    timeStart,
                    timeEnd,
                    0, // TODO
                    emptyMap(),
                    null,
                    data
            )
        }

        const val functionalTestTestIdentifierDuration = 2345L

        private const val expectedTestPackage = "com.github.tarcv.tongstestapp.f[12].test"
        private const val expectedTestRunner = "(?:android.support.test.runner.AndroidJUnitRunner|$testPackageRoot.f2.TestRunner)"
        val logOnlyCommandPattern =
                ("am\\s+instrument\\s+-w\\s+-r" +
                        " -e filter ((?:\\S+,)?com.github.tarcv.tongs.ondevice.AnnontationReadingFilter(,?:\\S+)?)" +
                        " -e log true" +
                        " -e test_argument \\S+" +
                        """\s+$expectedTestPackage\/$expectedTestRunner""")
                        .replace(".", "\\.")
                        .replace(" -", "\\s+-")
                        .toRegex()
        val TestIdentifierCommandPattern =
                ("am\\s+instrument\\s+-w\\s+-r" +
                        " -e filter ()" +
                        " -e test_argument \\S+" +
                        " -e tongs_filterClass ()" +
                        " -e tongs_filterMethod ()" +
                        """\s+$expectedTestPackage\/$expectedTestRunner""")
                        .replace(".", "\\.")
                        .replace(" -", "\\s+-")
                        .replace("()", "(.+?)")
                        .toRegex()

        private val timeLine = "[ LOGCAT_TIME 1234: 5678 I/Tongs.TestInfo ]"
        private val parameterizedTestSuffix = """"annotations":[{"annotationType":"org.junit.runner.RunWith","value":"class org.junit.runners.Parameterized"},{"annotationType":"org.junit.runner.RunWith","value":"class org.junit.runners.Parameterized"},{"annotationType":"org.junit.Test","expected":"class org.junit.Test${'$'}None","timeout":0}]},"""
        private val standardTestSuffix = """"annotations":[{"annotationType":"org.junit.Test","expected":"class org.junit.Test${'$'}None","timeout":0}]},"""
        private val tests = listOf(
                parameterizedTest("happy.DangerousNamesTest","test[param = ${'$'}THIS_IS_NOT_A_VAR]"),
                parameterizedTest("happy.DangerousNamesTest","test[param =        1       ]"),
                parameterizedTest("happy.DangerousNamesTest","test[param = #######]"),
                parameterizedTest("happy.DangerousNamesTest","test[param = !!!!!!!]"),
                parameterizedTest("happy.DangerousNamesTest","test[param = ''''''']"),
                parameterizedTest("happy.DangerousNamesTest","test[param = \"\"\"\"\"\"\"\"]"),
                parameterizedTest("happy.DangerousNamesTest","test[param = ()${'$'}(echo)`echo`()${'$'}(echo)`echo`()${'$'}(echo)`echo`()${'$'}(echo)`echo`()${'$'}(echo)`echo`()${'$'}(echo)`echo`()${'$'}(echo)`echo`]"),
                parameterizedTest("happy.DangerousNamesTest","test[param = * *.* * *.* * *.* * *.* * *.* * *.* * *.* * *.* *]"),
                parameterizedTest("happy.DangerousNamesTest","test[param = . .. . .. . .. . .. . .. . .. . .. . .. . .. . ..]"),
                parameterizedTest("happy.DangerousNamesTest","test[param = |&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%|&;<>()${'$'}`?[]#~=%]"),
                parameterizedTest("happy.DangerousNamesTest","test[param = Non-ASCII: ° © ± ¶ ½ » ѱ ∆]"),
                parameterizedTest("happy.DangerousNamesTest","test[param = ; function {}; while {}; for {}; do {}; done {}; exit]"),
                parameterizedTest("happy.FilteredTest","api22Only[1]"),
                parameterizedTest("happy.FilteredTest","api22Only[2]"),
                parameterizedTest("happy.FilteredTest","api22Only[3]"),
                parameterizedTest("happy.FilteredTest","api22Only[4]"),
                parameterizedTest("happy.FilteredTest","filteredByF2Filter[1]"),
                parameterizedTest("happy.FilteredTest","filteredByF2Filter[2]"),
                parameterizedTest("happy.FilteredTest","filteredByF2Filter[3]"),
                parameterizedTest("happy.FilteredTest","filteredByF2Filter[4]"),
                test("happy.GrantPermissionsForClassTest","testPermissionGranted1","""{"annotationType":"com.github.tarcv.tongs.GrantPermission","value":["android.permission.WRITE_CALENDAR"]}"""),
                test("happy.GrantPermissionsForClassTest","testPermissionGranted2","""{"annotationType":"com.github.tarcv.tongs.GrantPermission","value":["android.permission.WRITE_CALENDAR"]}"""),
                test("happy.GrantPermissionsForInheritedClassTest","testPermissionGranted1","""{"annotationType":"com.github.tarcv.tongs.GrantPermission","value":["android.permission.WRITE_CALENDAR"]}"""),
                test("happy.GrantPermissionsForInheritedClassTest","testPermissionGranted2","""{"annotationType":"com.github.tarcv.tongs.GrantPermission","value":["android.permission.WRITE_CALENDAR"]}"""),
                test("happy.GrantPermissionsTest","testPermissionGranted","""{"annotationType":"com.github.tarcv.tongs.GrantPermission","value":["android.permission.WRITE_CALENDAR"]}"""),
                test("happy.GrantPermissionsTest","testNoPermissionByDefault"),
                test("happy.NoPermissionsForOverridesTest","testNoPermissionForAbstractOverrides"),
                test("happy.NoPermissionsForOverridesTest","testNoPermissionForNormalOverrides"),
                test("happy.NormalTest", "test"),
                parameterizedTest("happy.ParameterizedNamedTest", "test[param = 1]"),
                parameterizedTest("happy.ParameterizedNamedTest","test[param = 2]"),
                parameterizedTest("happy.ParameterizedNamedTest","test[param = 3]"),
                parameterizedTest("happy.ParameterizedNamedTest","test[param = 4]"),
                parameterizedTest("happy.ParameterizedNamedTest","test[param = 5]"),
                parameterizedTest("happy.ParameterizedNamedTest","test[param = 6]"),
                parameterizedTest("happy.ParameterizedNamedTest","test[param = 7]"),
                parameterizedTest("happy.ParameterizedNamedTest","test[param = 8]"),
                parameterizedTest("happy.ParameterizedTest","test[0]"),
                parameterizedTest("happy.ParameterizedTest","test[1]"),
                parameterizedTest("happy.ParameterizedTest","test[2]"),
                parameterizedTest("happy.ParameterizedTest","test[3]"),
                parameterizedTest("happy.ParameterizedTest","test[4]"),
                parameterizedTest("happy.ParameterizedTest","test[5]"),
                parameterizedTest("happy.ParameterizedTest","test[6]"),
                parameterizedTest("happy.ParameterizedTest","test[7]"),
                test("happy.PropertiesTest","normalPropertiesTest","""{"annotationType":"com.github.tarcv.tongs.TestProperties","keys":["x","y"],"values":["1","2"]}"""),
                test("happy.PropertiesTest","normalPropertyPairsTest","""{"annotationType":"com.github.tarcv.tongs.TestPropertyPairs","value":["v","1","w","2"]}"""),
                parameterizedTest("happy.ResetPrefsTest","testPrefsAreClearedBetweenTests[0]"),
                parameterizedTest("happy.ResetPrefsTest","testPrefsAreClearedBetweenTests[1]"),
                parameterizedTest("happy.ResetPrefsTest","testPrefsAreClearedBetweenTests[2]"),
                parameterizedTest("happy.ResetPrefsTest","testPrefsAreClearedBetweenTests[3]"),
                parameterizedTest("ResultTest","successful[failAfter = true]"),
                parameterizedTest("ResultTest","successful[failAfter = false]"),
                parameterizedTest("ResultTest","failureFromEspresso[failAfter = true]"),
                parameterizedTest("ResultTest","failureFromEspresso[failAfter = false]"),
                parameterizedTest("ResultTest","assumptionFailure[failAfter = true]"),
                parameterizedTest("ResultTest","assumptionFailure[failAfter = false]")
            )

        val testIdentifiers: List<String> = tests.map { it.toStringIdentifier() }
        val logcatLines: List<String> = tests.flatMap { it.toLines() }

        private fun test(shortenedTestClass: String, testMethod: String, additionalAnnotations: String = ""): TestInfo {
            return TestInfo(shortenedTestClass, testMethod, additionalAnnotations)
        }

        private fun parameterizedTest(shortenedTestClass: String, testMethodWithVariant: String): TestInfo {
            return test(
                    shortenedTestClass,
                    testMethodWithVariant,
                    """{"annotationType":"org.junit.runner.RunWith","value":"class org.junit.runners.Parameterized"},{"annotationType":"org.junit.runner.RunWith","value":"class org.junit.runners.Parameterized"}"""
            )
        }

        private class TestInfo(
                val shortenedTestClass: String,
                val testMethod: String,
                private val additionalAnnotations: String = ""
        ) {
            val fullTestClass: String
                get() = """$testPackageRoot.$shortenedTestClass"""

            fun toLines(): List<String> {
                val additionalAnnotationsPart = if (additionalAnnotations.isNotBlank()) {
                    "$additionalAnnotations,"
                } else {
                    ""
                }
                val escapedMethod = testMethod.replace("\"", "\\\"")
                return listOf(
                        timeLine,
                        """0b2d3157-LOGCAT_INDEX:{"testClass":"$fullTestClass","testMethod":"$escapedMethod","annotations":[$additionalAnnotationsPart{"annotationType":"org.junit.Test","expected":"class org.junit.Test${'$'}None","timeout":0}]},""",
                        ""
                )
            }

            fun toStringIdentifier(): String {
                return "${fullTestClass}#${testMethod}"
            }
        }
    }
}

