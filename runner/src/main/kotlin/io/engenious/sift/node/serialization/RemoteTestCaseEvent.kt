package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.run.TestCaseEvent
import io.engenious.sift.node.serialization.RemoteTestCase.Companion.toTestCase
import kotlinx.serialization.Serializable

@Serializable
data class RemoteTestCaseEvent(
    val testCase: RemoteTestCase,
    val excludedDevices: List<RemoteDevice>,
    val totalFailureCount: Int
) {
    companion object {
        fun fromTestCaseEvent(value: TestCaseEvent) = RemoteTestCaseEvent(
            RemoteTestCase.fromTestCase(value.testCase),
            value.excludedDevices.map(RemoteDevice.Companion::fromLocalDevice),
            value.totalFailureCount
        )

        fun RemoteTestCaseEvent.toTestCaseEvent() = TestCaseEvent(
            this.testCase.toTestCase(),
            this.excludedDevices,
            this.totalFailureCount
        )
    }
}
