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
package com.github.tarcv.tongs.api.testcases

import com.github.tarcv.tongs.api.devices.Device
import java.util.Collections.emptyMap

data class TestCase @JvmOverloads constructor( // TODO: consider splitting into TestIdentifier and TestCase classes
    val typeTag: Class<*>, // TODO: consider changing to :Enum<*>

/**
     * The package of the class containing the test case.
     * It might not be provided by some test providers, in which case it is an empty string.
     */
    val testPackage: String,

    val testClass: String,
    val testMethod: String, // TODO: consider adding 'variation' property
    val readablePath: List<String>,
    val properties: Map<String, String> = emptyMap(), // TODO: consider changing key type to Enum or Class
    val annotations: List<AnnotationInfo> = emptyList(), // TODO: consider replacing with properties

    /**
     * List of devices in a pool that can run this test case. Null means all devices.
     */
    val includedDevices: Set<Device>?,
    val extra: Any = Any()
) {
    init {
        require (!(testMethod.isEmpty() || testClass.isEmpty())) {
            "Test identifiers must be specified"
        }
        require(includedDevices == null || includedDevices.isNotEmpty()) {
            "includedDevice should be either null or non-empty"
        }
    }

    val displayName: String
        get() = readablePath.last()

    /**
     * Returns a readable string uniquely identifying a test case for use in logs and file names.
     */
    // TODO: update to include typeTag
    override fun toString(): String {
        return "$testClass#$testMethod"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TestCase

        if (typeTag.name != other.typeTag.name) return false
        if (testClass != other.testClass) return false
        if (testMethod != other.testMethod) return false

        return true
    }

    override fun hashCode(): Int {
        var result = testMethod.hashCode()
        result = 31 * result + testClass.hashCode()
        result = 31 * result + typeTag.name.hashCode()
        return result
    }
}
