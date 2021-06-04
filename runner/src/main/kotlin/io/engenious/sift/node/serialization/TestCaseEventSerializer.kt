@file:UseSerializers(DeviceSerializer::class, TestCaseSerializer::class)

package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object TestCaseEventSerializer : KSerializer<TestCaseEvent> {
    override val descriptor: SerialDescriptor = SurrogateTestCaseEvent.serializer().descriptor

    override fun deserialize(decoder: Decoder): TestCaseEvent {
        val surrogate = decoder.decodeSerializableValue(SurrogateTestCaseEvent.serializer())
        return TestCaseEvent(
            surrogate.testCase,
            surrogate.includedDevices,
            surrogate.excludedDevices,
            surrogate.totalFailureCount
        )
    }

    override fun serialize(encoder: Encoder, value: TestCaseEvent) {
        val surrogate = SurrogateTestCaseEvent(
            value.testCase,
            value.includedDevices.toList(),
            value.excludedDevices.toList(),
            value.totalFailureCount
        )
        encoder.encodeSerializableValue(SurrogateTestCaseEvent.serializer(), surrogate)
    }

    @Serializable
    private data class SurrogateTestCaseEvent(
        val testCase: TestCase,
        val includedDevices: List<Device>,
        val excludedDevices: List<Device>,
        val totalFailureCount: Int
    )
}