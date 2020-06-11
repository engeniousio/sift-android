package io.engenious.sift

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.run.TestCaseRunRule
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.run.TestCaseRunRuleFactory
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.HashMap

class RunPlugin: TestCaseRuleFactory<TestCaseRule>, TestCaseRunRuleFactory<TestCaseRunRule> {
    override fun testCaseRules(context: TestCaseRuleContext): Array<out TestCaseRule> {
        val collectingTestCaseRule = CollectingTestCaseRule()
        val filteringTestCaseRule = FilteringTestCaseRule {
            siftClient.run {
                postTests(collectingTestCaseRule.testCases)
                getEnabledTests(config.testPlan, config.status)
            }
        }
        return arrayOf(collectingTestCaseRule, filteringTestCaseRule)
    }

    override fun testCaseRunRules(context: TestCaseRunRuleContext): Array<out TestCaseRunRule> {
        return arrayOf(ResultCollectingTestCaseRunRule(testResults))
    }

    companion object {
        @Suppress("ObjectPropertyName")
        private val _config= AtomicReference<Config>()
        var config: Config
            get() = _config.get() as Config
            set(value) = _config.set(value)

        val siftClient by lazy { SiftClient(config.token) }

        val testResults: MutableMap<TestIdentifier, Boolean> =
                Collections.synchronizedMap(HashMap<TestIdentifier, Boolean>())

        fun postResults() {
            siftClient.postResults(testResults.toMap())
        }
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
