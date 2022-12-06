/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.api.run

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.StackTrace
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.aTestCase
import java.time.Instant

private val pool = Pool.Builder.aDevicePool().addDevice(Device.TEST_DEVICE).build()

@JvmOverloads
fun aTestResult(testCase: TestCase, status: ResultStatus, traces: List<StackTrace>,
                customPool: Pool = pool, failureCount: Int = 0): TestCaseRunResult {
    return TestCaseRunResult.Companion.aTestResult(testCase, status, traces, customPool, failureCount)
}

fun TestCaseRunResult.Companion.aTestResult(testCase: TestCase, status: ResultStatus, traces: List<StackTrace>,
                                            customPool: Pool = pool, failureCount: Int = 0): TestCaseRunResult {
    return TestCaseRunResult(customPool, Device.TEST_DEVICE, testCase, status, traces,
            Instant.now(), Instant.now().plusMillis(15), Instant.now(), Instant.now().plusMillis(15),
            failureCount, emptyMap(), null, emptyList())
}

fun aTestResult(testClass: String, testMethod: String, status: ResultStatus, traces: List<StackTrace>): TestCaseRunResult {
    return TestCaseRunResult.Companion.aTestResult(testClass, testMethod, status, traces)
}
fun TestCaseRunResult.Companion.aTestResult(testClass: String, testMethod: String, status: ResultStatus, traces: List<StackTrace>): TestCaseRunResult {
    val testCase = aTestCase(testClass, testMethod, null)
    return TestCaseRunResult.Companion.aTestResult(testCase, status, traces, pool)
}

fun anErrorTrace(): List<StackTrace> {
    return listOf(StackTrace("Error", "error-message", "Error: error-message"))
}