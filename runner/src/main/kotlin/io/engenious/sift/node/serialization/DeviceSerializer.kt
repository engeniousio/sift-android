@file:UseSerializers(DisplayGeometrySerializer::class)

package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Diagnostics
import com.github.tarcv.tongs.api.devices.DisplayGeometry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

object DeviceSerializer : KSerializer<Device> {
    override val descriptor: SerialDescriptor = DeviceSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Device) {
        val surrogate = DeviceSurrogate(
            serial = value.serial,
            manufacturer = value.manufacturer,
            modelName = value.modelName,
            modelNameUniqueSuffix = value.name.removePrefix(value.modelName),
            osApiLevel = value.osApiLevel,
            longName = value.longName,
            tablet = value.isTablet,
            geometry = value.geometry,
            supportedVisualDiagnostics = value.supportedVisualDiagnostics,
            id = value.deviceIdentifierAsString()
        )
        encoder.encodeSerializableValue(DeviceSurrogate.serializer(), surrogate)
    }

    private fun Device.deviceIdentifierAsString(): String {
        val identifier = Device::class.declaredMemberFunctions
            .single { it.name == "getUniqueIdentifier" }
            .also { it.isAccessible = true }
            .call(this)
        return identifier.toString()
    }

    override fun deserialize(decoder: Decoder): Device {
        return decoder.decodeSerializableValue(DeviceSurrogate.serializer()) // TODO: also check name suffix
    }

    @Serializable
    private data class DeviceSurrogate(
        private val serial: String,
        private val manufacturer: String,
        private val modelName: String,
        private val modelNameUniqueSuffix: String,
        private val osApiLevel: Int,
        private val tablet: Boolean,
        private val longName: String,
        private val supportedVisualDiagnostics: Diagnostics,
        private val id: String,
        private val geometry: DisplayGeometry?
    ) : Device() {
        init {
            setNameSuffix(modelNameUniqueSuffix)
        }

        override fun getHost(): String = "localhost"
        override fun getSerial(): String = serial
        override fun getManufacturer(): String = manufacturer
        override fun getModelName(): String = modelName
        override fun getOsApiLevel(): Int = osApiLevel
        override fun getLongName(): String = longName
        override fun getDeviceInterface(): Any = throw UnsupportedOperationException()
        override fun isTablet(): Boolean = isTablet
        override fun getGeometry(): DisplayGeometry? = geometry
        public override fun getUniqueIdentifier(): String = id
        override fun getSupportedVisualDiagnostics(): Diagnostics = supportedVisualDiagnostics
    }
}
