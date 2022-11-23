/*
 * Copyright 2021 TarCV
 * Copyright 2018 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.api.run

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.testcases.TestCase
import com.google.common.base.Objects
import java.util.Collections
import java.util.Collections.emptyList

class TestCaseEvent private constructor( // TODO: avoid creating objects of this class in plugins
        val testCase: TestCase,
        excludedDevices: Collection<Device>,
        val totalFailureCount: Int = 0,
        private val deviceRunners: MutableMap<Device, MutableList<TestCaseRunner>> = HashMap()
) {
    constructor(
        testCase: TestCase,
        excludedDevices: Collection<Device>,
        totalFailureCount: Int = 0
    ) : this(testCase, excludedDevices, totalFailureCount, HashMap())

    val testMethod: String
        get() = testCase.testMethod
    val testClass: String
        get() = testCase.testClass

    val excludedDevices: Set<Device>
        get() = Collections.unmodifiableSet(_excludedDevices)

    private val _excludedDevices = HashSet(excludedDevices)

    fun isEnabledOn(device: Device): Boolean {
        val included = testCase.includedDevices?.contains(device) ?: true
        val excluded = _excludedDevices.contains(device)
        return included && !excluded
    }

    override fun hashCode(): Int {
        return Objects.hashCode(this.testCase)
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as TestCaseEvent?
        return Objects.equal(this.testCase, other!!.testCase)
    }

    override fun toString(): String {
        return this.testClass + "#" + this.testMethod
    }

    fun withFailureCount(totalFailureCount: Int): TestCaseEvent {
        return TestCaseEvent(testCase, excludedDevices, totalFailureCount, deviceRunners)
    }

    fun addDeviceRunner(device: Device, runner: TestCaseRunner) {
        deviceRunners.computeIfAbsent(device) { ArrayList() }
                .add(runner)
    }

    fun runnersFor(device: Device): List<TestCaseRunner> = deviceRunners[device] ?: emptyList()

    companion object {
        @JvmField
        val TEST_TYPE_TAG = TestTypeTag::class.java
    }
}

class TestTypeTag
