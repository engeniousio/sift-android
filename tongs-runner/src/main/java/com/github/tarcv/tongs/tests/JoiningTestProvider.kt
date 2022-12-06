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
package com.github.tarcv.tongs.tests

import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.TestCaseProvider

class JoiningTestProvider(
    private val providers: List<TestCaseProvider>,
    pool: Pool
): TestCaseProvider {
    private val allDevices = pool.devices

    override fun loadTestSuite(): Collection<TestCase> {
        return providers
            .flatMap { it.loadTestSuite() }
            .asReversed() // later test cases override earlier ones
            .flatMap {
                val includedDevices = it.includedDevices ?: allDevices
                includedDevices.map { device -> Pair(it, device) }
            }
            .groupBy({ it.first }) {
                it.second
            }
            .map { (test, devices) ->
                test.copy(includedDevices = devices.toSet())
            }
            .asReversed()
    }

}
