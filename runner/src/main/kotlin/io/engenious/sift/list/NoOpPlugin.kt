package io.engenious.sift.list

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import io.engenious.sift.Conveyor
import java.util.concurrent.atomic.AtomicBoolean

object NoOpPlugin : Conveyor.Plugin<Any, Unit>(), TestCaseRuleFactory<TestCaseRule> {
    override fun initStorage() = Unit

    override fun testCaseRules(context: TestCaseRuleContext): Array<out TestCaseRule> {
        return arrayOf(object : TestCaseRule {
            val shouldAdvance = AtomicBoolean(true)

            override fun filter(testCaseEvent: TestCaseEvent): Boolean {
                if (shouldAdvance.getAndSet(false)) {
                    this@NoOpPlugin.finalizeAndAdvanceConveyor<Unit>()
                }

                return false
            }
        })
    }
}
