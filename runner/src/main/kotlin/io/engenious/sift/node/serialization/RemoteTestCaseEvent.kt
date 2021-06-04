@file:UseSerializers(TestCaseSerializer::class)

package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class RemoteTestCaseEvent(
    val testCase: TestCase,
    val includedDevices: List<RemoteDevice>,
    val excludedDevices: List<RemoteDevice>,
    val totalFailureCount: Int
) {
    companion object {
        fun fromTestCaseEvent(value: TestCaseEvent) = RemoteTestCaseEvent(
            value.testCase,
            value.includedDevices.map(RemoteDevice.Companion::fromLocalDevice),
            value.excludedDevices.map(RemoteDevice.Companion::fromLocalDevice),
            value.totalFailureCount
        )

        fun RemoteTestCaseEvent.toTestCaseEvent() = TestCaseEvent(
            this.testCase,
            this.includedDevices,
            this.excludedDevices,
            this.totalFailureCount
        )
    }
}
