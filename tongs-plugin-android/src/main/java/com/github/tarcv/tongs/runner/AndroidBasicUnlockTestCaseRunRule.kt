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

import com.android.ddmlib.IDevice
import com.android.ddmlib.NullOutputReceiver
import com.github.tarcv.tongs.api.run.TestCaseRunRule
import com.github.tarcv.tongs.api.run.TestCaseRunRuleAfterArguments
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.run.TestCaseRunRuleFactory
import com.github.tarcv.tongs.model.AndroidDevice

class AndroidBasicUnlockTestCaseRunRuleFactory : TestCaseRunRuleFactory<AndroidBasicUnlockTestCaseRunRule> {
    override fun testCaseRunRules(context: TestCaseRunRuleContext): Array<out AndroidBasicUnlockTestCaseRunRule> {
        val device = context.device
        return if (device is AndroidDevice) {
            arrayOf(AndroidBasicUnlockTestCaseRunRule(device))
        } else {
            emptyArray()
        }
    }
}

class AndroidBasicUnlockTestCaseRunRule(
        device: AndroidDevice
) : TestCaseRunRule {
    private val device: IDevice = device.deviceInterface

    override fun before() {
        unlockDeviceUsingMenuButton()
    }

    override fun after(arguments: TestCaseRunRuleAfterArguments) {
        // nothing to do after running a test
    }

    private fun unlockDeviceUsingMenuButton() {
        val noOpReceiver = NullOutputReceiver()
        repeat (2) { // some devices require pressing MENU key twice
            device.executeShellCommand("input keyevent 82", noOpReceiver)
        }
    }

}