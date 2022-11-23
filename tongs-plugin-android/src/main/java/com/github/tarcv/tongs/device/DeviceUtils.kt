/*
 * Copyright 2020 TarCV
 * Copyright 2016 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.device

import com.android.ddmlib.*
import com.github.tarcv.tongs.api.devices.Diagnostics
import org.slf4j.LoggerFactory
import java.io.IOException

fun clearLogcat(device: IDevice) {
    try {
        device.executeShellCommand("logcat -c", NullOutputReceiver())
    } catch (e: TimeoutException) {
        logger.warn("Could not clear logcat on device: " + device.serialNumber, e)
    } catch (e: AdbCommandRejectedException) {
        logger.warn("Could not clear logcat on device: " + device.serialNumber, e)
    } catch (e: ShellCommandUnresponsiveException) {
        logger.warn("Could not clear logcat on device: " + device.serialNumber, e)
    } catch (e: IOException) {
        logger.warn("Could not clear logcat on device: " + device.serialNumber, e)
    }
}

fun computeDiagnostics(deviceInterface: IDevice?, apiLevel: Int): Diagnostics {
    if (deviceInterface == null) {
        return Diagnostics.NONE
    }

    val supportsScreenRecord = deviceInterface.supportsFeature(IDevice.Feature.SCREEN_RECORD) &&
            "Genymotion" != deviceInterface.getProperty("ro.product.manufacturer")
    if (supportsScreenRecord) {
        return Diagnostics.VIDEO
    }

    return if (apiLevel >= 16) {
        Diagnostics.SCREENSHOTS
    } else Diagnostics.NONE

}

private val logger = LoggerFactory.getLogger(" com.github.tarcv.tongs.device.DeviceUtils")