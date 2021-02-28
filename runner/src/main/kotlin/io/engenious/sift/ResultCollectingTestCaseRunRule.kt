package io.engenious.sift

import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseRunRule
import com.github.tarcv.tongs.api.run.TestCaseRunRuleAfterArguments
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.run.TestCaseRunRuleFactory

class ResultCollectingPlugin :
    Conveyor.Plugin<Set<TestIdentifier>, MutableMap<TestIdentifier, Boolean>>(),
    TestCaseRunRuleFactory<TestCaseRunRule> {
    // This plugin should not advance the conveyor by itself,
    // instead the conveyor is advanced automatically once the current test run is complete

    override fun testCaseRunRules(context: TestCaseRunRuleContext): Array<out TestCaseRunRule> {
        return arrayOf(ResultCollectingTestCaseRunRule(storage))
    }

    override fun initStorage(): MutableMap<TestIdentifier, Boolean> = HashMap()
}

open class ResultCollectingTestCaseRunRule(
    private val testResults: MutableMap<TestIdentifier, Boolean>
) : TestCaseRunRule {
    override fun after(arguments: TestCaseRunRuleAfterArguments) {
        val result = when (arguments.result.status) {
            ResultStatus.PASS -> true
            ResultStatus.FAIL, ResultStatus.ERROR -> false
            ResultStatus.IGNORED, ResultStatus.ASSUMPTION_FAILED -> null
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
