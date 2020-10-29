package io.engenious.sift

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import java.util.Collections

class CollectingTestCaseRule : TestCaseRule {
    private val _testCases = Collections.synchronizedSet(HashSet<TestIdentifier>())
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
