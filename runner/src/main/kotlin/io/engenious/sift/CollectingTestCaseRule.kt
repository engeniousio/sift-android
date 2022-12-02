package io.engenious.sift

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

object TestCaseCollectingPlugin :
    Conveyor.Plugin<Unit, MutableSet<TestIdentifier>>(), TestCaseRuleFactory<TestCaseRule> {
    override fun initStorage(): MutableSet<TestIdentifier> = Collections.synchronizedSet(HashSet<TestIdentifier>())

    override fun testCaseRules(context: TestCaseRuleContext): Array<out TestCaseRule> {
        return arrayOf(object : CollectingTestCaseRule() {
            private val shouldAdvance = AtomicBoolean(true)

            override fun filter(testCaseEvent: TestCaseEvent): Boolean {
                if (shouldAdvance.getAndSet(false)) {
                    synchronized(testCases) {
                        storage.addAll(
                            testCases.map { TestIdentifier.fromTestCase(it) }
                        )
                    }
                    this@TestCaseCollectingPlugin.finalizeAndAdvanceConveyor<MutableSet<TestIdentifier>>()
                }

                return super.filter(testCaseEvent)
            }
        })
    }
}
open class CollectingTestCaseRule(
    _testCases: MutableSet<TestCase> = HashSet()
) : TestCaseRule {
    private val _testCases = Collections.synchronizedSet(_testCases)
    val testCases: Set<TestCase>
        get() = synchronized(_testCases) {
            HashSet(_testCases)
        }

    override fun transform(testCaseEvent: TestCaseEvent): TestCaseEvent {
        _testCases.add(testCaseEvent.testCase)
        return testCaseEvent
    }
}

data class TestIdentifier(
    val `package`: String,
    val `class`: String,
    val method: String
) {
    override fun toString(): String {
        return "${`package`}/${`class`}#$method"
    }

    companion object {
        fun fromString(str: String): TestIdentifier {
            return Regex("([*])/([*])") /** All tests from apk */
                .matchEntire(str)
                ?.destructured
                ?.let { (packageName, className) ->
                    TestIdentifier(packageName, className, "*")
                } ?: Regex("(.+)/([*])") /** All tests from Package */
                .matchEntire(str)
                ?.destructured
                ?.let { (packageName, className) ->
                    TestIdentifier(packageName, className, "*")
                } ?: Regex("(.+)/(.+)#(.+)")  /** All tests from Class */
                .matchEntire(str)
                ?.destructured
                ?.let { (packageName, className, methodName) ->
                    TestIdentifier(packageName, className, methodName)
                } ?: Regex("(.+)/(.+)#(.+)")  /** One test from Class */
                .matchEntire(str)
                ?.destructured
                ?.let { (packageName, className, methodName) ->
                    TestIdentifier(packageName, className, methodName)
                }
                ?: throw RuntimeException("Invalid test identifier format: $str")
        }

        fun getTestType(strTestIdentifier: String): TestIdentifierType {
            return Regex("([*])/([*])")
                .matchEntire(strTestIdentifier)
                ?.let {
                    TestIdentifierType.ALL
                }
                ?: Regex("(.+)/([*])")
                    .matchEntire(strTestIdentifier)
                    ?.let {
                        TestIdentifierType.PACKAGE
                    }
                ?: Regex("(.+)/(.+)#([*])")
                    .matchEntire(strTestIdentifier)
                    ?.let {
                        TestIdentifierType.CLASS
                    }
                ?: Regex("(.+)/(.+)#(.+)")
                .matchEntire(strTestIdentifier)
                ?.let {
                    TestIdentifierType.TEST
                }
                ?: throw RuntimeException("Invalid test identifier format: $strTestIdentifier")
        }

        fun fromTestCase(testCase: TestCase): TestIdentifier {
            val `package` = testCase.testPackage
            val `class` = testCase.testClass.removePrefix("$`package`.")
            return TestIdentifier(`package`, `class`, testCase.testMethod)
        }
    }
}

enum class TestIdentifierType {
    TEST,
    CLASS,
    PACKAGE,
    ALL
}
