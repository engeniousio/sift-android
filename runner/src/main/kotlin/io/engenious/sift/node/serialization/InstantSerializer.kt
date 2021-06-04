package io.engenious.sift.node.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        InstantSerializer::class.java.simpleName,
        PrimitiveKind.LONG
    )

    override fun deserialize(decoder: Decoder): Instant {
        val epochMilli = decoder.decodeLong()
        return Instant.ofEpochMilli(epochMilli)
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        val epochMilli = value.toEpochMilli()
        encoder.encodeLong(epochMilli)
    }
}
