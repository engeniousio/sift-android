/*
 * Copyright 2020 TarCV
 * Copyright 2014 Shazam Entertainment Limited
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
package com.github.tarcv.tongs.runner

import com.android.ddmlib.DdmPreferences
import com.github.tarcv.tongs.api.run.DeviceRunRule
import com.github.tarcv.tongs.api.run.DeviceRunRuleContext
import com.github.tarcv.tongs.api.run.DeviceRunRuleFactory
import com.github.tarcv.tongs.device.clearLogcat
import com.github.tarcv.tongs.injector.system.InstallerInjector.installer
import com.github.tarcv.tongs.model.AndroidDevice
import com.github.tarcv.tongs.system.adb.PackageInstaller
import com.github.tarcv.tongs.system.io.RemoteFileManager

class AndroidSetupDeviceRuleFactory : DeviceRunRuleFactory<AndroidSetupDeviceRule> {
    override fun deviceRules(context: DeviceRunRuleContext): Array<out AndroidSetupDeviceRule> {
        val device = context.device
        if (device is AndroidDevice) {
            return arrayOf(
                    AndroidSetupDeviceRule(device, installer(context.configuration))
            )
        } else {
            return emptyArray()
        }
    }
}

class AndroidSetupDeviceRule(private val device: AndroidDevice, private val installer: PackageInstaller) : DeviceRunRule {
    override fun before() {
        DdmPreferences.setTimeOut(30000)
        installer.resetInstallation(device)

        // For when previous run crashed/disconnected and left files behind
        val deviceInterface = device.deviceInterface
        RemoteFileManager.removeRemoteDirectory(deviceInterface)
        RemoteFileManager.createRemoteDirectory(deviceInterface)

        clearLogcat(deviceInterface)
    }

    override fun after() {
        // no-op
    }
}