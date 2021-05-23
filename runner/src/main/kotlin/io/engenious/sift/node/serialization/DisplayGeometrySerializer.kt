package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.devices.DisplayGeometry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

object DisplayGeometrySerializer : KSerializer<DisplayGeometry> {
    override val descriptor: SerialDescriptor = SurrogateDisplayGeometry.serializer().descriptor

    override fun deserialize(decoder: Decoder): DisplayGeometry {
        val surrogate = decoder.decodeSerializableValue(SurrogateDisplayGeometry.serializer())
        return DisplayGeometry(surrogate.small, surrogate.large, surrogate.density)
    }

    override fun serialize(encoder: Encoder, value: DisplayGeometry) {
        val fields = DisplayGeometry::class.declaredMemberProperties
        val surrogate = SurrogateDisplayGeometry(
            fields.single { it.name == "small" }.also { it.isAccessible = true }.get(value) as Int,
            fields.single { it.name == "large" }.also { it.isAccessible = true }.get(value) as Int,
            fields.single { it.name == "density" }.also { it.isAccessible = true }.get(value) as Double,
        )
        encoder.encodeSerializableValue(SurrogateDisplayGeometry.serializer(), surrogate)
    }

    @Serializable
    private data class SurrogateDisplayGeometry(
        val small: Int,
        val large: Int,
        val density: Double
    )
}
