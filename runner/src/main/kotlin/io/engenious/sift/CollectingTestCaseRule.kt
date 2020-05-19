package io.engenious.sift

import com.github.tarcv.tongs.model.Device
import com.github.tarcv.tongs.model.TestCaseEvent
import com.github.tarcv.tongs.runner.rules.TestCaseRule

class CollectingTestCaseRule(private val noOpDevices: List<Device> = emptyList()): TestCaseRule {
    private val _testCases = HashSet<TestIdentifier>()
    val testCases: Set<TestIdentifier>
        get() = _testCases

    override fun transform(testCaseEvent: TestCaseEvent): TestCaseEvent {
        testCaseEvent.testCase
                .let { TestIdentifier(it.testClass, it.testMethod) }
                .also { _testCases.add(it) }
        return if (noOpDevices.isEmpty()) {
            testCaseEvent
        } else {
            TestCaseEvent.newTestCase(
                    testCaseEvent.testMethod,
                    testCaseEvent.testClass,
                    testCaseEvent.testCase.properties,
                    testCaseEvent.testCase.annotations,
                    testCaseEvent.excludedDevices + noOpDevices,
                    testCaseEvent.totalFailureCount
            )
        }
    }
}

data class TestIdentifier(
        val clazz: String,
        val method: String
)