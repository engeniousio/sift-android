package io.engenious.sift.node.plugins.blocker

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.DeviceProvider
import com.github.tarcv.tongs.api.devices.DeviceProviderContext
import com.github.tarcv.tongs.api.devices.DeviceProviderFactory

class LoopingDeviceProvider : DeviceProvider {
    override fun provideDevices(): Set<Device> = loopingDevices

    companion object {
        val loopingDevices = setOf(LoopingDevice)
    }
}

class LoopingDeviceProviderFactory : DeviceProviderFactory<DeviceProvider> {
    override fun deviceProviders(context: DeviceProviderContext): Array<out DeviceProvider> = arrayOf(
        LoopingDeviceProvider()
    )
}
