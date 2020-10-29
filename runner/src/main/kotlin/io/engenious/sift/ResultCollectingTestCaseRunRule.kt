package io.engenious.sift

import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseRunRule
import com.github.tarcv.tongs.api.run.TestCaseRunRuleAfterArguments

class ResultCollectingTestCaseRunRule(private val testResults: MutableMap<TestIdentifier, Boolean>) : TestCaseRunRule {
    override fun after(arguments: TestCaseRunRuleAfterArguments) {
        val result = when (arguments.result.status) {
            ResultStatus.PASS -> true
            ResultStatus.FAIL, ResultStatus.ERROR -> false
            ResultStatus.IGNORED, ResultStatus.ASSUMPTION_FAILED -> null // TODO: is it correct?
        }
        val key = TestIdentifier.fromTestCase(arguments.result.testCase)
        if (result != null) {
            testResults[key] = result
        } else {
            testResults.remove(key)
        }
    }

    override fun before() {
        // no op
    }
}
