/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.suite

import com.android.ddmlib.testrunner.TestIdentifier
import com.github.tarcv.tongs.model.AndroidDevice
import com.github.tarcv.tongs.model.AndroidDevice.Builder.aDevice
import org.junit.Assert
import org.junit.Test

class DeviceIncludesTest {
    private val device1 = aDevice().build()
    private val device2 = aDevice().build()
    private val device3 = aDevice().build()
    private val device4 = aDevice().build()
    private val test1 = TestIdentifier("class", "test1")
    private val test2 = TestIdentifier("class", "test2")

    @Test
    fun testIncludesAll() {
        val input = listOf(
                device1 to setOf(test1, test2),
                device2 to setOf(test1, test2)
        )
        val includes = JUnitTestCaseProvider.calculateDeviceIncludes(
                input.asSequence()
        )
        Assert.assertEquals(
                mapOf(
                        test1 to listOf(device1, device2),
                        test2 to listOf(device1, device2)
                ),
                includes
        )
    }

    @Test
    fun testNothingIncluded() {
        val input = listOf(
                device1 to emptySet<TestIdentifier>(),
                device2 to emptySet()
        )
        val excludes = JUnitTestCaseProvider.calculateDeviceIncludes(
                input.asSequence()
        )
        Assert.assertEquals(
                emptyMap<TestIdentifier, Collection<AndroidDevice>>(),
                excludes
        )
    }

    @Test
    fun testMixedIncludes() {
        val input = listOf(
                device1 to setOf(test1, test2),
                device2 to emptySet(),
                device3 to setOf(test1),
                device4 to setOf(test2)
        )
        val excludes = JUnitTestCaseProvider.calculateDeviceIncludes(
                input.asSequence()
        )
        Assert.assertEquals(
                mapOf(
                        test1 to listOf(device1, device3),
                        test2 to listOf(device1, device4)
                ),
                excludes
        )
    }
}