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

package com.github.tarcv.tongs.suite

import com.android.ddmlib.testrunner.TestIdentifier
import com.github.tarcv.tongs.api.testcases.AnnotationInfo
import com.github.tarcv.tongs.runner.TestInfo
import org.junit.Assert
import org.junit.Test
import java.io.File

class ApkReaderTest {
    @Test
    fun test() {
        val apk = File("../../tests/app/app/build/outputs/apk/androidTest/f1/debug/app-f1-debug-androidTest.apk")

        val testsToCheck = listOf(
                TestIdentifier("com.github.tarcv.test.happy.GrantPermissionsForInheritedClassTest", "testPermissionGranted1"),
                TestIdentifier("com.github.tarcv.test.happy.NoPermissionsForOverridesTest", "testNoPermissionForAbstractOverrides"),
                TestIdentifier("com.github.tarcv.test.happy.NoPermissionsForOverridesTest", "testNoPermissionForNormalOverrides"),
                TestIdentifier("com.github.tarcv.test.happy.ParameterizedNamedTest", "test[param = 5]")
        )

        val expectedResult = listOf(
                TestInfo(
                        TestIdentifier(
                                "com.github.tarcv.test.happy.GrantPermissionsForInheritedClassTest",
                                "testPermissionGranted1"
                        ),
                        "com.github.tarcv.test.happy",
                        emptyList(),
                        listOf(
                                AnnotationInfo("com.github.tarcv.tongs.GrantPermission", mapOf(
                                        "value" to listOf("android.permission.WRITE_CALENDAR")
                                )),
                                AnnotationInfo("org.junit.Test", emptyMap())
                        )
                ),
                TestInfo(
                        TestIdentifier(
                                "com.github.tarcv.test.happy.NoPermissionsForOverridesTest",
                                "testNoPermissionForAbstractOverrides"
                        ),
                        "com.github.tarcv.test.happy",
                        emptyList(),
                        listOf(
                                AnnotationInfo("androidx.test.filters.SdkSuppress", mapOf(
                                        "minSdkVersion" to 23
                                )),
                                AnnotationInfo("org.junit.Test", emptyMap())
                        )
                ),
                TestInfo(
                        TestIdentifier(
                                "com.github.tarcv.test.happy.NoPermissionsForOverridesTest",
                                "testNoPermissionForNormalOverrides"
                        ),
                        "com.github.tarcv.test.happy",
                        emptyList(),
                        listOf(
                                AnnotationInfo("androidx.test.filters.SdkSuppress", mapOf(
                                        "minSdkVersion" to 23
                                )),
                                AnnotationInfo("org.junit.Test", emptyMap())
                        )
                ),
                TestInfo(
                        TestIdentifier(
                                "com.github.tarcv.test.happy.ParameterizedNamedTest",
                                "test[param = 5]"
                        ),
                        "com.github.tarcv.test.happy",
                        emptyList(),
                        listOf(
                                AnnotationInfo("org.junit.runner.RunWith", mapOf(
                                        "value" to "org.junit.runners.Parameterized"
                                )),
                                AnnotationInfo("org.junit.Test", emptyMap())
                        )
                )
        )
        val result = ApkTestInfoReader().readTestInfo(apk, testsToCheck)
        Assert.assertEquals(expectedResult, result)
    }
}
