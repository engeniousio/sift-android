@file:UseSerializers(DisplayGeometrySerializer::class)
package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Diagnostics
import com.github.tarcv.tongs.api.devices.DisplayGeometry
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

@Serializable
data class RemoteDevice(
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

    companion object {
        private val deviceRepository = mutableMapOf<String, Device>()

        fun fromLocalDevice(device: Device): RemoteDevice = synchronized(this) {
            deviceRepository[device.deviceIdentifierAsString()] = device

            return RemoteDevice(
                serial = device.serial,
                manufacturer = device.manufacturer,
                modelName = device.modelName,
                modelNameUniqueSuffix = device.name.removePrefix(device.modelName),
                osApiLevel = device.osApiLevel,
                longName = device.longName,
                tablet = device.isTablet,
                geometry = device.geometry,
                supportedVisualDiagnostics = device.supportedVisualDiagnostics,
                id = device.deviceIdentifierAsString()
            )
        }

        fun RemoteDevice.toLocalDevice(): Device = synchronized(Companion) {
            deviceRepository[this.uniqueIdentifier]
                ?: throw IllegalStateException("Can't deserialize a device that wasn't serialized before")
        }
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

fun Device.deviceIdentifierAsString(): String {
    val identifier = Device::class.declaredMemberFunctions
        .single { it.name == "getUniqueIdentifier" }
        .also { it.isAccessible = true }
        .call(this)
    return identifier.toString()
}
