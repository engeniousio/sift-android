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

import com.github.tarcv.tongs.api.TongsConfiguration
import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.RunTesult
import com.github.tarcv.tongs.api.result.TestCaseFileManager
import com.github.tarcv.tongs.api.run.*
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.injector.runner.TestRunFactoryInjector
import com.github.tarcv.tongs.model.AndroidDevice
import com.github.tarcv.tongs.suite.ApkTestCase

class AndroidInstrumentedTestCaseRunnerFactory: TestCaseRunnerFactory<AndroidInstrumentedTestCaseRunner> {
    override fun testCaseRunners(context: TestCaseRunnerContext): Array<out AndroidInstrumentedTestCaseRunner> {
        return arrayOf(AndroidInstrumentedTestCaseRunner(context))
    }
}

class AndroidInstrumentedTestCaseRunner(val context: TestCaseRunnerContext): TestCaseRunner {
    override fun supports(device: Device, testCase: TestCase): Boolean {
        return device is AndroidDevice && testCase.typeTag == ApkTestCase::class.java
    }

    override fun run(arguments: TestCaseRunnerArguments): RunTesult {
        val androidTestRunFactory = TestRunFactoryInjector.testRunFactory(context.configuration)
        val runContext = AndroidRunContext(context, arguments)
        val testRun = androidTestRunFactory.createTestRun(runContext, arguments.testCaseEvent,
                context.device as AndroidDevice,
                context.pool)
        return testRun.execute()
    }

}

class AndroidRunContext(
        private val context: TestCaseRunnerContext,
        private val arguments: TestCaseRunnerArguments
) {
    val configuration: TongsConfiguration
        get() = context.configuration

    val pool: Pool
        get() = context.pool

    val fileManager: TestCaseFileManager
        get() = arguments.fileManager

    val testCaseEvent: TestCaseEvent
        get() = arguments.testCaseEvent

    val device: AndroidDevice
        get() = context.device as AndroidDevice
}