/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.system.io

import com.github.tarcv.tongs.api.result.FileType
import com.github.tarcv.tongs.api.testcases.TestCase
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object FileUtils {
    @JvmStatic
    @JvmOverloads
    fun createFilenameForTest(testCase: TestCase, fileType: FileType, suffix: String = ""): String {
        val testName = String.format("%s#%s", testCase.testClass, testCase.testMethod)

        // Test identifier can contain absolutely any characters, so generate safe name out of it
        // Dots are not safe because of '.', '..' and extensions
        val safeChars = testName.replace("[^A-Za-z0-9_]".toRegex(), "_")

        // Always use hash to handle edge case of test name that differ only with char case
        val hash: String = try {
            val hashBytes = MessageDigest.getInstance("MD5")
                    .digest(testName.toByteArray(StandardCharsets.UTF_8))
            (0 until hashBytes.size / 2)
                    .map { i ->
                        val index = (hashBytes.size / 2) + i
                        if (hashBytes[index] >= 0) {
                            (hashBytes[index]).toInt()
                        } else {
                            0x100 + hashBytes[index]
                        }
                    }
                    .joinToString("") { b -> String.format("%02x", b) }
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Failed to generate safe file name")
        }

        val suffixPart = if (suffix.isNotEmpty()) {
            "-$suffix"
        } else {
            ""
        }
        return String.format("%s%s-%s.%s", safeChars, suffixPart, hash, fileType.suffix)
    }


}