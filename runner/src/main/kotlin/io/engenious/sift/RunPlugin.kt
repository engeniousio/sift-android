package io.engenious.sift

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import java.util.concurrent.atomic.AtomicReference

class RunPlugin: TestCaseRuleFactory<TestCaseRule> {
    override fun testCaseRules(context: TestCaseRuleContext): Array<out TestCaseRule> {
        val collectingTestCaseRule = CollectingTestCaseRule()
        val filteringTestCaseRule = FilteringTestCaseRule {
            SiftClient(config.token).run {
                postTests(collectingTestCaseRule.testCases)
                getEnabledTests(config.testPlan, config.status)
            }
        }
        return arrayOf(collectingTestCaseRule, filteringTestCaseRule)
    }

    companion object {
        @Suppress("ObjectPropertyName")
        private val _config= AtomicReference<Config>()
        var config: Config
            get() = _config.get() as Config
            set(value) = _config.set(value)
    }

    private class FilteringTestCaseRule(
            private val enabledTestCasesProvider: () -> Set<TestIdentifier>
    ) : TestCaseRule {
        private val enabledTestCases: Set<TestIdentifier> by lazy {
            enabledTestCasesProvider()
        }

        override fun filter(testCaseEvent: TestCaseEvent): Boolean {
            val testIdentifier = TestIdentifier.fromTestCase(testCaseEvent.testCase)
            return enabledTestCases.contains(testIdentifier)
        }

    }
}
