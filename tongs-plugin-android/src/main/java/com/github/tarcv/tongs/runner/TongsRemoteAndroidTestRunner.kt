/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.runner

import com.android.ddmlib.IShellEnabledDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.InstrumentationResultParser
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner

class TongsRemoteAndroidTestRunner(
        packageName: String,
        runnerName: String,
        remoteDevice: IShellEnabledDevice
) : RemoteAndroidTestRunner(packageName, runnerName, remoteDevice) {
    override fun createParser(runName: String, listeners: MutableCollection<ITestRunListener>): InstrumentationResultParser {
        return TongsInstrumentationResultParser(runName, listeners)
    }
}