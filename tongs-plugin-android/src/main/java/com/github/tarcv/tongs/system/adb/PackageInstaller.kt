/*
 * Copyright 2021 TarCV
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
package com.github.tarcv.tongs.system.adb

import com.android.ddmlib.InstallException
import com.github.tarcv.tongs.model.AndroidDevice
import com.github.tarcv.tongs.util.repeatUntilSuccessful
import org.slf4j.LoggerFactory
import java.io.File

class PackageInstaller(
    private val appApk: File?,
    private val appPackage: String,
    private val testApk: File?,
    private val testPackage: String
) {
    companion object {
        val logger = LoggerFactory.getLogger(PackageInstaller::class.java)
    }

    fun resetInstallation(device: AndroidDevice) {
        if (appApk != null) {
            doResetInstallation(device, appApk, appPackage)
        }
        if (testApk != null) {
            doResetInstallation(device, testApk, testPackage)
        }
    }

    private fun doResetInstallation(device: AndroidDevice, apk: File, packageId: String) {
        val deviceInterface = device.deviceInterface
        try {
            deviceInterface.uninstallPackage(packageId)
        } catch (e: InstallException) {
            logger.warn("Failed to remove package $packageId from ${deviceInterface.name}", e)
        }

        repeatUntilSuccessful(
            onError = { e: InstallException -> logger.warn("Failed to install $apk to ${deviceInterface.name}", e) })
            {
                // -d -- allow downgrading debug packages. It helps when uninstallation failed, but installation of
                //      an older app is requested
                // -t -- allow installing test-only packages. It is required as often builds for UI-testing are often
                //      marked as test-only ones.
                deviceInterface.installPackage(apk.absolutePath, true, "-t", "-d")
            }
    }
}