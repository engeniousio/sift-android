package io.engenious.sift

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import java.util.concurrent.atomic.AtomicBoolean

object FilteringTestCasePlugin : Conveyor.Plugin<Set<TestIdentifier>, Set<TestIdentifier>>(), TestCaseRuleFactory<TestCaseRule> {
    override fun testCaseRules(context: TestCaseRuleContext): Array<out TestCaseRule> {
        return arrayOf(object : TestCaseRule {
            val shouldAdvance = AtomicBoolean(true)

            override fun filter(testCaseEvent: TestCaseEvent): Boolean {
                if (shouldAdvance.getAndSet(false)) {
                    this@FilteringTestCasePlugin.finalizeAndAdvanceConveyor<Set<TestIdentifier>>()
                }
                val testIdentifier = TestIdentifier.fromTestCase(testCaseEvent.testCase)
                return previousStorage.contains(testIdentifier)
            }
        })
    }

    override fun initStorage(): Set<TestIdentifier> = previousStorage
}
