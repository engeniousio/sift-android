package io.engenious.sift.node.plugins.blocker

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Diagnostics
import com.github.tarcv.tongs.api.devices.DisplayGeometry

object LoopingDevice : Device() {
    private val uniqueObject = Any()

    override fun getHost(): String = "localhost"

    override fun getSerial(): String = "##SIFT-INTERNAL-DEVICE##"

    override fun getManufacturer(): String = "node"

    override fun getModelName(): String = "node"

    override fun getOsApiLevel(): Int = 1

    override fun getLongName(): String = "node"

    override fun getDeviceInterface(): Any = uniqueObject

    override fun isTablet(): Boolean = false

    override fun getGeometry(): DisplayGeometry = DisplayGeometry(1)

    override fun getSupportedVisualDiagnostics(): Diagnostics = Diagnostics.NONE

    override fun getUniqueIdentifier(): Any = uniqueObject
}
