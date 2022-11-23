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
package com.github.tarcv.tongs.system.io

import com.github.tarcv.tongs.aConfigurationBuilder
import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.FileType
import com.github.tarcv.tongs.api.result.TestCaseFileManager
import com.github.tarcv.tongs.api.testcases.aTestCase
import com.github.tarcv.tongs.koinRule
import org.apache.commons.lang3.RandomStringUtils
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.koin.core.context.KoinContextHandler
import java.io.File
import kotlin.random.Random

class TestCaseFileManagerImplTest(
) {
    private val poolName = RandomStringUtils.randomAlphanumeric(10)
    private val testDevice = Device.TEST_DEVICE
    private val testCase = aTestCase(
        RandomStringUtils.randomAlphanumeric(10),
        RandomStringUtils.randomAlphanumeric(10)
    )
    private val testCaseFixedClass = testCase.testClass.replace(".", "_")

    private val tempFileRule = TemporaryFolder()

    @get:Rule
    val rules: RuleChain = RuleChain.emptyRuleChain()
        .around(tempFileRule)
        .around(run {
            koinRule {
                aConfigurationBuilder()
                    .withOutput(tempFileRule.newFolder("output"))
                    .build(true)
            }
        })

    private val fileManager: TestCaseFileManager by lazy {
        val pool = Pool.Builder()
            .withName(poolName)
            .addDevice(testDevice)
            .build()
        val fileManager1 by KoinContextHandler.get().inject<FileManager>()
        TestCaseFileManagerImpl(fileManager1, pool, testDevice, testCase)
    }

    private val typeDirectory = "dirIHJ"
    private val typeSuffix = "suffixKLM"
    private val fileType = object : FileType {
        override fun getDirectory(): String = typeDirectory

        override fun getSuffix(): String = typeSuffix

    }

    @Test
    fun testCaseFile() {
        val suffix = RandomStringUtils.randomAlphanumeric(10)

        val testCaseFile = fileManager.testCaseFile(fileType, suffix)

        val file = testCaseFile.toFile()
        assertFileNotCreated(file)
        assertParentCreated(file, false)
        assertFullFileQualification(file, suffix)
    }

    @Test
    fun testGetFileFromFileType() {
        val suffix = RandomStringUtils.randomAlphanumeric(10)

        val file = fileManager.getFile(fileType, suffix)

        assertFileNotCreated(file)
        assertParentCreated(file, false)
        assertFullFileQualification(file, suffix)
    }

    @Test
    fun getRelativeFile() {
        val suffix = RandomStringUtils.randomAlphanumeric(10)

        val file = fileManager.getRelativeFile(fileType, suffix)

        assertFileNotCreated(file)
        assertParentCreated(file, false)
        assertFullFileQualification(file, suffix)
    }

    @Test
    fun testCreateFileBasic() {
        val file = fileManager.createFile(fileType)

        assertFileNotCreated(file)
        assertParentCreated(file, true)
        assertFullFileQualification(file)
    }

    @Test
    fun testCreateFileWithSuffix() {
        val suffix = RandomStringUtils.randomAlphanumeric(10)

        val file = fileManager.createFile(fileType, suffix)

        assertFileNotCreated(file)
        assertParentCreated(file, true)
        assertFullFileQualification(file, suffix)
    }

    @Test
    fun testCreateFileWithSequenceNumber() {
        val sequenceNum: Int = Random.Default.nextInt()

        val file = fileManager.createFile(fileType, sequenceNum)

        assertFileNotCreated(file)
        assertParentCreated(file, true)
        assertFullFileQualification(file, sequenceNum.toString())
    }

    private fun assertParentCreated(file: File, state: Boolean) {
        val notStr = if (state) { "" } else { "not " }
        Assert.assertTrue(
            "The file parent directory should ${notStr}be created",
            file.parentFile.exists() == state
        )
    }

    private fun assertFileNotCreated(file: File) {
        Assert.assertTrue(
            "The file should not be created",
            !file.exists()
        )
    }

    private fun assertFullFileQualification(file: File, suffix: String = "") {
        if (suffix.isNotEmpty()) {
            Assert.assertTrue(
                "'${file.path}' should contain with the passed suffix '$suffix'",
                file.path.contains(suffix)
            )
        }
        Assert.assertTrue(
            "'${file.path}' should end with the file type suffix '${fileType.suffix}'",
            file.path.endsWith(".${fileType.suffix}")
        )
        Assert.assertTrue(
            "'${file.path}' should contain the current pool name '$poolName'",
            file.path.contains(poolName)
        )

        val deviceName = testDevice.safeSerial
        Assert.assertTrue(
            "'${file.path}' should contain the current device name '$deviceName'",
            file.path.contains(deviceName)
        )

        Assert.assertTrue(
            "'${file.path}' should contain the current test class name '$testCaseFixedClass'",
            file.path.contains(testCaseFixedClass)
        )
        Assert.assertTrue(
            "'${file.path}' should contain the current test method name '${testCase.testMethod}'",
            file.path.contains(testCase.testMethod)
        )
    }
}