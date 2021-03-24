package io.engenious.sift

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import io.engenious.sift.run.RunData
import java.util.concurrent.atomic.AtomicBoolean

object FilteringTestCasePlugin : Conveyor.Plugin<RunData, RunData>(), TestCaseRuleFactory<TestCaseRule> {
    override fun testCaseRules(context: TestCaseRuleContext): Array<out TestCaseRule> {
        return arrayOf(object : TestCaseRule {
            val shouldAdvance = AtomicBoolean(true)

            override fun filter(testCaseEvent: TestCaseEvent): Boolean {
                if (shouldAdvance.getAndSet(false)) {
                    this@FilteringTestCasePlugin.finalizeAndAdvanceConveyor<RunData>()
                }
                val testIdentifier = TestIdentifier.fromTestCase(testCaseEvent.testCase)
                return previousStorage.enabledTests.contains(testIdentifier)
            }
        })
    }

    override fun initStorage(): RunData = previousStorage
}
