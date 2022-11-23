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
package com.github.tarcv.tongs.runner

import com.android.ddmlib.*
import com.github.tarcv.tongs.api.run.TestCaseRunRule
import com.github.tarcv.tongs.api.run.TestCaseRunRuleAfterArguments
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.run.TestCaseRunRuleFactory
import com.github.tarcv.tongs.model.AndroidDevice
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.String.format

class AndroidCleanupTestCaseRunRuleFactory : TestCaseRunRuleFactory<AndroidCleanupTestCaseRunRule> {
    override fun testCaseRunRules(context: TestCaseRunRuleContext): Array<out AndroidCleanupTestCaseRunRule> {
        val device = context.device
        return if (device is AndroidDevice) {
            arrayOf(AndroidCleanupTestCaseRunRule(
                    device,
                    context.configuration.applicationPackage,
                    context.configuration.instrumentationPackage
            ))
        } else {
            emptyArray()
        }
    }
}

class AndroidCleanupTestCaseRunRule(
        device: AndroidDevice,
        private val applicationPackage: String,
        private val testPackage: String
) : TestCaseRunRule {
    private val logger = LoggerFactory.getLogger(AndroidInstrumentedTestRun::class.java)
    private val device: IDevice = device.deviceInterface

    override fun before() {
        clearPackageData(device, applicationPackage)
        clearPackageData(device, testPackage)
        resetToHomeScreen()
    }

    override fun after(arguments: TestCaseRunRuleAfterArguments) {
    }

    /**
     * Reset device to Home Screen and close soft keyboard if it is still open
     */
    private fun resetToHomeScreen() {
        val noOpReceiver = NullOutputReceiver()
        device.executeShellCommand("input keyevent 3", noOpReceiver) // HOME
        device.executeShellCommand("input keyevent 4", noOpReceiver) // BACK
    }

    private fun clearPackageData(device: IDevice, applicationPackage: String) {
        val start = System.currentTimeMillis()

        try {
            val command = format("pm clear %s", applicationPackage)
            logger.info("Cmd: $command")
            device.executeShellCommand(command, NullOutputReceiver())
        } catch (e: TimeoutException) {
            throw UnsupportedOperationException(format("Unable to clear package data (%s)", applicationPackage), e)
        } catch (e: AdbCommandRejectedException) {
            throw UnsupportedOperationException(format("Unable to clear package data (%s)", applicationPackage), e)
        } catch (e: ShellCommandUnresponsiveException) {
            throw UnsupportedOperationException(format("Unable to clear package data (%s)", applicationPackage), e)
        } catch (e: IOException) {
            throw UnsupportedOperationException(format("Unable to clear package data (%s)", applicationPackage), e)
        }

        logger.debug("Clearing application data: {} (took {}ms)", applicationPackage, System.currentTimeMillis() - start)
    }

}