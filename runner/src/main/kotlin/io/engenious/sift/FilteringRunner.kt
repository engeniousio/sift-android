package io.engenious.sift

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.result.Delegate
import com.github.tarcv.tongs.api.result.RunTesult
import com.github.tarcv.tongs.api.result.StackTrace
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseRunner
import com.github.tarcv.tongs.api.run.TestCaseRunnerArguments
import com.github.tarcv.tongs.api.run.TestCaseRunnerContext
import com.github.tarcv.tongs.api.testcases.TestCase
import java.time.Instant

class FilteringRunner(
        private val context: TestCaseRunnerContext,
        private val enabledTestCases: Set<TestIdentifier>
) : TestCaseRunner {
    override fun run(arguments: TestCaseRunnerArguments): RunTesult {
        val identifier = arguments.testCaseEvent.testCase.toTestIdentifier()
        return if (enabledTestCases.contains(identifier)) {
            Delegate()
        } else {
            TestCaseRunResult(
                    context.pool,
                    context.device,
                    arguments.testCaseEvent.testCase,
                    ResultStatus.IGNORED,
                    listOf(StackTrace(
                            "SiftIgnored",
                            "Disabled by SIFT",
                            "SiftIgnored: Disabled by SIFT"
                    )),
                    arguments.startTimestampUtc,
                    Instant.now(),
                    null, null,
                    0, emptyMap(), null, emptyList()
            )
        }
    }

    override fun supports(device: Device, testCase: TestCase): Boolean {
        return true
    }

}

private fun TestCase.toTestIdentifier(): TestIdentifier {
    return TestIdentifier(this.testClass, this.testMethod)
}
