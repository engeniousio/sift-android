package io.engenious.sift

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashSet

object TestCaseCollectingPlugin :
    Conveyor.Plugin<Unit, MutableSet<TestIdentifier>>(), TestCaseRuleFactory<TestCaseRule> {
    override fun initStorage(): MutableSet<TestIdentifier> = Collections.synchronizedSet(HashSet<TestIdentifier>())

    override fun testCaseRules(context: TestCaseRuleContext): Array<out TestCaseRule> {
        return arrayOf(object : CollectingTestCaseRule(storage) {
            private val shouldAdvance = AtomicBoolean(true)

            override fun filter(testCaseEvent: TestCaseEvent): Boolean {
                if (shouldAdvance.getAndSet(false)) {
                    this@TestCaseCollectingPlugin.finalizeAndAdvanceConveyor<MutableSet<TestIdentifier>>()
                }

                return super.filter(testCaseEvent)
            }
        })
    }
}
open class CollectingTestCaseRule(
    private val _testCases: MutableSet<TestIdentifier> = Collections.synchronizedSet(HashSet<TestIdentifier>())
) : TestCaseRule {
    val testCases: Set<TestIdentifier>
        get() = synchronized(_testCases) {
            HashSet(_testCases)
        }

    override fun transform(testCaseEvent: TestCaseEvent): TestCaseEvent {
        testCaseEvent.testCase
            .let { TestIdentifier.fromTestCase(it) }
            .also { _testCases.add(it) }
        return testCaseEvent
    }
}

data class TestIdentifier(
    val `package`: String,
    val `class`: String,
    val method: String
) {
    companion object {
        fun fromTestCase(testCase: TestCase): TestIdentifier {
            val `package` = testCase.testPackage
            val `class` = testCase.testClass.removePrefix("$`package`.")
            return TestIdentifier(`package`, `class`, testCase.testMethod)
        }
    }
}
