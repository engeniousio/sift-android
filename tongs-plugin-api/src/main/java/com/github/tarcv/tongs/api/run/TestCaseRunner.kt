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
import com.github.tarcv.tongs.api.result.RunTesult
import com.github.tarcv.tongs.api.result.TestCaseFileManager
import com.github.tarcv.tongs.api.testcases.TestCase
import java.time.Instant

interface TestCaseRunnerFactory<out T: TestCaseRunner> {
    fun testCaseRunners(context: TestCaseRunnerContext): Array<out T>
}

interface TestCaseRunner {
    fun supports(device: Device, testCase: TestCase): Boolean
    fun run(arguments: TestCaseRunnerArguments): RunTesult
}

// TODO: review arguments
data class TestCaseRunnerContext(
        val configuration: RunConfiguration,
        val pool: Pool,
        val device: Device
)
// TODO: review arguments
data class TestCaseRunnerArguments(
        val fileManager: TestCaseFileManager,
        val testCaseEvent: TestCaseEvent,
        val startTimestampUtc: Instant
)