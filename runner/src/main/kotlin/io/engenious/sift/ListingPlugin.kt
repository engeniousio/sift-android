package io.engenious.sift

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED

class ListingPlugin : TestCaseRuleFactory<TestCaseRule> {
    override fun testCaseRules(context: TestCaseRuleContext): Array<out TestCaseRule> {
        return arrayOf(collectingTestCaseRule, SkipAllTestCaseRule())
    }

    companion object {
        val collectedTests
            get() = collectingTestCaseRule.testCases

        private val collectingTestCaseRule by lazy(SYNCHRONIZED) { CollectingTestCaseRule() }
    }

    private class SkipAllTestCaseRule : TestCaseRule {
        override fun filter(testCaseEvent: TestCaseEvent): Boolean = false
    }
}
