package io.engenious.sift.node.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

abstract class SurrogateSerializer<T : Any, S : Any>(
    private val surrogateSerializer: KSerializer<S>
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = surrogateSerializer.descriptor
    protected abstract fun toSurrogate(value: T): S
    protected abstract fun fromSurrogate(surrogate: S): T

    final override fun deserialize(decoder: Decoder): T {
        val surrogate = decoder.decodeSerializableValue(surrogateSerializer)
        return fromSurrogate(surrogate)
    }

    final override fun serialize(encoder: Encoder, value: T) {
        val surrogate = toSurrogate(value)
        encoder.encodeSerializableValue(surrogateSerializer, surrogate)
    }
}
