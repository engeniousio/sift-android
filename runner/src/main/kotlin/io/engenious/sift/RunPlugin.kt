package io.engenious.sift

import com.github.tarcv.tongs.api.run.PoolRunRuleContext
import com.github.tarcv.tongs.api.run.PoolRunRuleFactory
import com.github.tarcv.tongs.api.run.TestCaseRunnerContext
import com.github.tarcv.tongs.api.run.TestCaseRunnerFactory
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import java.util.*


class RunPlugin:
        TestCaseRuleFactory<CollectingTestCaseRule>, PoolRunRuleFactory<RunListenerRule>, TestCaseRunnerFactory<FilteringRunner> {
    override fun testCaseRules(context: TestCaseRuleContext): Array<out CollectingTestCaseRule> {
        return arrayOf(collectingTestCaseRule)
    }

    override fun poolRules(context: PoolRunRuleContext): Array<out RunListenerRule> {
        // TODO: support more than 1 pool
        return arrayOf(RunListenerRule {
            client.postTests(collectingTestCaseRule.testCases)
            synchronized(enabledTestCases) {
                enabledTestCases.clear()
                enabledTestCases.addAll(client.getEnabledTests(testPlan, status))
            }
        })
    }

    override fun testCaseRunners(context: TestCaseRunnerContext): Array<out FilteringRunner> {
        // TODO: support more than 1 pool
        return arrayOf(FilteringRunner(context, enabledTestCases))
    }

    companion object {
        lateinit var token: String
        lateinit var testPlan: String
        lateinit var status: Config.TestStatus
        private val client by lazy { SiftClient(token) }
        private val collectingTestCaseRule by lazy { CollectingTestCaseRule() }
        private val enabledTestCases = Collections.synchronizedSet(HashSet<TestIdentifier>())
    }
}
