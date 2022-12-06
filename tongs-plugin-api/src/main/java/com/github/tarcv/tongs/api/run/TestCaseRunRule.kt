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
import com.github.tarcv.tongs.api.result.TestCaseFileManager
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import java.time.Instant

class TestCaseRunRuleContext(
        val configuration: RunConfiguration,
        val fileManager: TestCaseFileManager,
        val pool: Pool,
        val device: Device,
        val testCaseEvent: TestCaseEvent,
        val startTimestampUtc: Instant
)

class TestCaseRunRuleAfterArguments(
        var result: TestCaseRunResult
    // TODO: add indicator whether this test case will be retried
)

interface TestCaseRunRuleFactory<out T: TestCaseRunRule> {
    fun testCaseRunRules(context: TestCaseRunRuleContext): Array<out T>
}

interface TestCaseRunRule {
    fun before()
    fun after(arguments: TestCaseRunRuleAfterArguments)
    // TODO: consider adding a separate method for transforming results and making after() nonmutating
}
