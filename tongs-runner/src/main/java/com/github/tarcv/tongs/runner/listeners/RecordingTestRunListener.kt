/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.runner.listeners

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import java.io.File
import java.io.FileWriter

class RecordingTestRunListener(device: Device, val runName: String, isLogOnly: Boolean) : TongsTestListener() {
    private val deviceSerial = device.serial
    private val writer: FileWriter by lazy {
        val filename = if (isLogOnly) {
            "eventsForLogOnly.log"
        } else {
            "events.log"
        }
        val logFile = File(filename)
        FileWriter(logFile, true)
    }
    private val separator = System.lineSeparator()

    override fun onTestStarted() {
        writer.append("$deviceSerial testRunStarted $runName$separator")
    }

    override fun onTestSuccessful() {
        writer.append("$deviceSerial onTestSuccessful $runName$separator")
    }

    override fun onTestSkipped(skipResult: TestCaseRunResult) {
        writer.append("$deviceSerial onTestIgnored $runName {$skipResult}$separator")
    }

    override fun onTestAssumptionFailure(skipResult: TestCaseRunResult) {
        writer.append("$deviceSerial onTestAssumptionFailure $runName {$skipResult}$separator")
    }

    override fun onTestFailed(failureResult: TestCaseRunResult) {
        writer.append("$deviceSerial onTestFailed $runName {$failureResult}$separator")
    }
}