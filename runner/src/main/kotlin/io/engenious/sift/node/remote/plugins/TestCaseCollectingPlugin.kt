package io.engenious.sift.node.remote.plugins

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import io.engenious.sift.CollectingTestCaseRule
import io.engenious.sift.node.remote.plugins.blocker.LoopingDevice
import java.util.concurrent.atomic.AtomicBoolean

class TestCaseCollectingPlugin(private val consumer: (Set<TestCase>) -> Unit) :
    TestCaseRuleFactory<TestCaseRule> {
    private val rule = object : CollectingTestCaseRule() {
        private val shouldTrigger = AtomicBoolean(true)

        override fun filter(testCaseEvent: TestCaseEvent): Boolean {
            if (shouldTrigger.getAndSet(false)) {
                consumer(testCases)
            }

            // remove all test cases except the infinite one provided by DecoratedTestSuiteLoader
            return testCaseEvent.isEnabledOn(LoopingDevice)
        }
    }

    override fun testCaseRules(context: TestCaseRuleContext): Array<out TestCaseRule> {
        return arrayOf(rule)
    }
}
