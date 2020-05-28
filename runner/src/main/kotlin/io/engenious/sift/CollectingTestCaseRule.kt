package io.engenious.sift

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCaseRule

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
                    testCaseEvent.testCase.typeTag,
                    testCaseEvent.testCase.testMethod,
                    testCaseEvent.testCase.testClass,
                    testCaseEvent.testCase.properties,
                    testCaseEvent.testCase.annotations,
                    testCaseEvent.testCase.extra,
                    testCaseEvent.includedDevices,
                    testCaseEvent.excludedDevices + noOpDevices,
                    testCaseEvent.totalFailureCount
            )
        }
    }
}

data class TestIdentifier(
        val `class`: String,
        val method: String
)