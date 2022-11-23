/*
 * Copyright 2021 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.api.testcases

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.run.TestTypeTag

@JvmOverloads
fun aTestCase(clazz: String, method: String, includes: List<Device>? = null): TestCase {
    assert(clazz.contains('.').not())
    val pkg = "com.example"
    return TestCase(
            TestTypeTag::class.java,
            pkg,
            "$pkg.$clazz",
            method,
            listOf("$method ($pkg.$clazz)"),
            includedDevices = includes?.toSet()
    )
}