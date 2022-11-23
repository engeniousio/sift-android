/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.api.run

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Pool

class DeviceRunRuleContext(
        val configuration: RunConfiguration,
        val pool: Pool,
        val device: Device
)

interface DeviceRunRuleFactory<out T: DeviceRunRule> {
    fun deviceRules(context: DeviceRunRuleContext): Array<out T>
}

interface DeviceRunRule {
    fun before()
    fun after()
}
