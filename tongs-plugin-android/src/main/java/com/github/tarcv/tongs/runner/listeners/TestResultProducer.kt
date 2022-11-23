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
package com.github.tarcv.tongs.runner.listeners

import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.runner.AndroidRunContext
import com.github.tarcv.tongs.runner.TestAndroidTestRunnerFactory
import java.time.Instant

class TestResultProducer(private val context: AndroidRunContext): IResultProducer {
    private val timeStart = Instant.now()

    override fun requestListeners(): List<RunListener> {
        return emptyList()
    }

    override fun getResult(): TestCaseRunResult {
        return TestAndroidTestRunnerFactory.resultForTestCase(context, timeStart)
    }
}