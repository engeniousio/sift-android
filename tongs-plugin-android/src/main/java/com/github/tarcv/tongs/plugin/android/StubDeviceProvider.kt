/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.plugin.android

import com.github.tarcv.tongs.model.AndroidDevice
import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.DisplayGeometry
import com.github.tarcv.tongs.api.devices.DeviceProvider
import com.github.tarcv.tongs.api.devices.DeviceProviderContext
import com.github.tarcv.tongs.api.devices.DeviceProviderFactory
import com.github.tarcv.tongs.pooling.StubDevice
import com.github.tarcv.tongs.runner.TestAndroidTestRunnerFactory.Companion.functionalTestTestIdentifierDuration

class StubDeviceProviderFactory: DeviceProviderFactory<StubDeviceProvider> {
    override fun deviceProviders(context: DeviceProviderContext): Array<out StubDeviceProvider> {
        return arrayOf(StubDeviceProvider())
    }
}

class StubDeviceProvider() : DeviceProvider {
    override fun provideDevices(): Set<Device> {
        val device1 = createStubDevice("tongs-5554", 25)
        val device2 = createStubDevice("tongs-5556", 22)
        return setOf(device1, device2)
    }
}

private fun createStubDevice(serial: String, api: Int): Device {
    val manufacturer = "tongs"
    val model = "Emu-$api"
    val stubDevice = StubDevice(serial, manufacturer, model, serial, api, "",
            functionalTestTestIdentifierDuration)
    return AndroidDevice.Builder()
            .withApiLevel(api.toString())
            .withDisplayGeometry(DisplayGeometry(640))
            .withManufacturer(manufacturer)
            .withModel(model)
            .withSerial(serial)
            .withDeviceInterface(stubDevice)
            .build()
}
