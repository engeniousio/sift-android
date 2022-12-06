/*
 * Copyright 2021 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.api.devices

fun createStubDevice(serial: String): Device {
    val api = 20
    val manufacturer = "tongs"
    val model = "Emu-$api"
    return object: Device() {
        val iface = Any()

        override fun getHost(): String = "localhost"
        override fun getSerial(): String = serial
        override fun getManufacturer(): String = manufacturer
        override fun getModelName(): String = model
        override fun getOsApiLevel(): Int = api
        override fun getLongName(): String = "$serial ($model)"
        override fun getDeviceInterface(): Any = iface
        override fun isTablet(): Boolean = false
        override fun getGeometry(): DisplayGeometry = DisplayGeometry(640)
        override fun getSupportedVisualDiagnostics(): Diagnostics = Diagnostics.VIDEO
        override fun getUniqueIdentifier(): Any = getSerial()

        override fun toString(): String = getSerial()
    }
}
