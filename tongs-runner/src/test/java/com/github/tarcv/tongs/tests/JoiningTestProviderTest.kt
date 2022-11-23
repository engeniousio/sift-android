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
import com.github.tarcv.tongs.api.devices.createStubDevice
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.TestCaseProvider
import com.github.tarcv.tongs.api.testcases.aTestCase
import org.junit.Assert
import org.junit.Test

class JoiningTestProviderTest {
    private val device1 = createStubDevice("device1")
    private val device2 = createStubDevice("device2")
    private val device3 = createStubDevice("device3")
    private val device4 = createStubDevice("device4")

    private val pool = Pool.Builder()
        .withName("test")
        .addDevice(device1)
        .addDevice(device2)
        .addDevice(device3)
        .addDevice(device4)
        .build()

    private val test1 = aTestCase("class", "test1")
    private val test2 = aTestCase("class", "test2")

    @Test
    fun testIncludesAll() {
        val includes = JoiningTestProvider(
            listOf(
                testCaseProvider(
                    test1.copy(includedDevices = setOf(device1))
                ),
                testCaseProvider(
                    test1.copy(includedDevices = setOf(device2))
                ),
                testCaseProvider(
                    test2.copy(includedDevices = setOf(device1))
                ),
                testCaseProvider(
                    test2.copy(includedDevices = setOf(device2))
                )
            ),
            pool
        )
            .loadTestSuite()
            .map { it to it.includedDevices }

        Assert.assertEquals(
            listOf(
                test1 to setOf(device1, device2),
                test2 to setOf(device1, device2)
            ),
            includes
        )
    }

    @Test
    fun testIncludesAllWithNulls() {
        val includes = JoiningTestProvider(
            listOf(
                testCaseProvider(
                    test1.copy(includedDevices = null)
                ),
                testCaseProvider(
                    test2.copy(includedDevices = null)
                )
            ),
            pool
        )
            .loadTestSuite()
            .map { it to it.includedDevices }

        Assert.assertEquals(
            listOf(
                test1 to setOf(device1, device2, device3, device4),
                test2 to setOf(device1, device2, device3, device4)
            ),
            includes
        )
    }

    @Test
    fun testIncludesAllMixed() {
        val includes = JoiningTestProvider(
            listOf(
                testCaseProvider(
                    test1.copy(includedDevices = null)
                ),
                testCaseProvider(
                    test2.copy(includedDevices = setOf(device1))
                ),
                testCaseProvider(
                    test2.copy(includedDevices = setOf(device2))
                )
            ),
            pool
        )
            .loadTestSuite()
            .map { it to it.includedDevices }

        Assert.assertEquals(
            listOf(
                test1 to setOf(device1, device2, device3, device4),
                test2 to setOf(device1, device2)
            ),
            includes
        )
    }

    private fun testCaseProvider(vararg cases: TestCase) = object : TestCaseProvider {
        override fun loadTestSuite(): Collection<TestCase> {
            return cases.toList()
        }
    }

    @Test
    fun testMixedIncludes() {
        val excludes = JoiningTestProvider(
            listOf(
                testCaseProvider(
                    test1.copy(includedDevices = setOf(device1))
                ),
                testCaseProvider(
                    test1.copy(includedDevices = setOf(device3))
                ),
                testCaseProvider(
                    test2.copy(includedDevices = setOf(device1))
                ),
                testCaseProvider(
                    test2.copy(includedDevices = setOf(device4))
                )
            ),
            pool
        )
            .loadTestSuite()
            .map { it to it.includedDevices }

        Assert.assertEquals(
            listOf(
                test1 to setOf(device1, device3),
                test2 to setOf(device1, device4)
            ),
            excludes
        )
    }

    @Test
    fun testMixedConflictingIncludes() {
        val excludes = JoiningTestProvider(
            listOf(
                testCaseProvider(
                    test1.copy(includedDevices = setOf(device1), properties = mapOf("a" to "b"))
                ),
                testCaseProvider(
                    test1.copy(includedDevices = setOf(device1, device3), properties = mapOf("c" to "d"))
                ),
                testCaseProvider(
                    test2.copy(includedDevices = setOf(device1, device4), properties = mapOf("e" to "f"))
                ),
                testCaseProvider(
                    test2.copy(includedDevices = setOf(device4), properties = mapOf("e" to "g"))
                )
            ),
            pool
        )
            .loadTestSuite()
            .map { it to it.includedDevices }

        Assert.assertEquals(
            listOf(
                test1 to setOf(device1, device3),
                test2 to setOf(device1, device4)
            ),
            excludes
        )
        Assert.assertEquals(
            excludes[0].first.properties,
            mapOf("c" to "d")
        )
        Assert.assertEquals(
            excludes[1].first.properties,
            mapOf("e" to "g")
        )
    }
}