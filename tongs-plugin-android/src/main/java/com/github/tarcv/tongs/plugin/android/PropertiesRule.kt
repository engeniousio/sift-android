/*
 * Copyright 2021 TarCV
 * Copyright 2016 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.plugin.android

import com.github.tarcv.tongs.api.HasConfiguration
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory

class PropertiesTestCaseRuleFactory
    : TestCaseRuleFactory<PropertiesTestCaseRule>, HasConfiguration {
    override val configurationSections: Array<String> = arrayOf("propertyAnnotations")

    override fun testCaseRules(context: TestCaseRuleContext): Array<out PropertiesTestCaseRule> {
        return arrayOf(PropertiesTestCaseRule(context.configuration.pluginConfiguration["package"] as? String))
    }
}

class PropertiesTestCaseRule(packagePrefix: String?) : TestCaseRule {
    private val packagePrefix = packagePrefix ?: "com.github.tarcv.tongs"

    override fun transform(testCaseEvent: TestCaseEvent): TestCaseEvent {
        val properties = HashMap<String, String>(testCaseEvent.testCase.properties)
        testCaseEvent.testCase.annotations.forEach {
            when (it.fullyQualifiedName) {
                "$packagePrefix.TestProperties" -> {
                    val keys = it.properties["keys"] as List<String>
                    val values = it.properties["values"] as List<String>
                    keyValueArraysToProperties(properties, keys, values)
                }
                "$packagePrefix.TestPropertyPairs" -> {
                    val values = it.properties["value"] as List<String>
                    keyValuePairsToProperties(properties, values)
                }
            }
        }

        return TestCaseEvent(
            TestCase(
                    testCaseEvent.testCase.typeTag,
                    testCaseEvent.testCase.testPackage,
                    testCaseEvent.testCase.testClass,
                    testCaseEvent.testCase.testMethod,
                    testCaseEvent.testCase.readablePath,
                    properties,
                    testCaseEvent.testCase.annotations,
                    testCaseEvent.testCase.includedDevices,
                    testCaseEvent.testCase.extra
            ),
            testCaseEvent.excludedDevices
        )
    }
}

private fun keyValueArraysToProperties(properties: MutableMap<String, String>, keys: List<String>, values: List<String>) {
    if (keys.size != values.size) {
        throw RuntimeException("Numbers of key and values in test properties annotations should be the same")
    }
    for (i in keys.indices) {
        properties[keys[i]] = values[i]
    }
}

private fun keyValuePairsToProperties(properties: MutableMap<String, String>, values: List<String>) {
    if (values.size != values.size / 2 * 2) {
        throw RuntimeException("Number of values in test property pairs annotations should be even")
    }
    var i = 0
    while (i < values.size) {
        properties[values[i]] = values[i + 1]
        i += 2
    }
}
