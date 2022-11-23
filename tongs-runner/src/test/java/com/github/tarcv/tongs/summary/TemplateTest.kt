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
package com.github.tarcv.tongs.summary

import com.github.tarcv.tongs.api.devices.Device.TEST_DEVICE
import com.github.tarcv.tongs.api.devices.Pool.Builder.aDevicePool
import com.github.tarcv.tongs.api.result.FileType
import com.github.tarcv.tongs.api.result.LinkedFileReportData
import com.github.tarcv.tongs.api.result.SimpleTableReportData
import com.github.tarcv.tongs.api.result.StackTrace
import com.github.tarcv.tongs.api.result.StandardFileTypes.JSON_LOG
import com.github.tarcv.tongs.api.result.StandardFileTypes.RAW_LOG
import com.github.tarcv.tongs.api.result.StandardFileTypes.SCREENRECORD
import com.github.tarcv.tongs.api.result.TestCaseFile
import com.github.tarcv.tongs.api.result.TestCaseFileManager
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.result.TestReportData
import com.github.tarcv.tongs.api.result.VideoReportData
import com.github.tarcv.tongs.api.result.tableOf
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.testcases.aTestCase
import com.github.tarcv.tongs.io.HtmlGenerator
import com.github.tarcv.tongs.koinRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.core.context.KoinContextHandler
import java.io.File
import java.time.Instant

class TemplateTest {
    val pool = aDevicePool()
            .addDevice(TEST_DEVICE)
            .withName("TestPool")
            .build()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val koinRule = koinRule()

    @Test
    fun fullTestResultPageIsCorrect() {
        val manager = object : TestCaseFileManager {
            override fun createFile(fileType: FileType): File {
                TODO("not implemented")
            }

            override fun createFile(fileType: FileType, sequenceNumber: Int): File {
                TODO("not implemented")
            }

            override fun createFile(fileType: FileType, suffix: String): File {
                TODO("not implemented")
            }

            override fun getFile(fileType: FileType, suffix: String): File {
                return File("${fileType.directory}/file$suffix${fileType.suffix}")
            }

            override fun getRelativeFile(fileType: FileType, suffix: String): File {
                return File("${fileType.directory}/file$suffix${fileType.suffix}")
            }

        }
        val model = aTestCaseRunResult(
            datas = listOf(
                SimpleTableReportData(
                    "Logcat", tableOf(
                        listOf(
                            "appName",
                            "logLevel",
                            "message",
                            "pid",
                            "tag",
                            "tid",
                            "time"
                        ),
                        listOf("App", "Warn", "message1", "1234", "TAG", "5678", "12:12:12 12-12-2019"),
                        listOf("App", "Warn", "message2", "1234", "TAG", "5678", "12:12:13 12-12-2019")
                    )
                ),
                LinkedFileReportData("Logcat", TestCaseFile(manager, RAW_LOG, "")),
                LinkedFileReportData("Logcat as JSON", TestCaseFile(manager, JSON_LOG, "")),
                VideoReportData("Screen recording", TestCaseFile(manager, SCREENRECORD, ""))
            )
        )
        val htmlGenerator by KoinContextHandler.get().inject<HtmlGenerator>()
        htmlGenerator.generateHtml(
            "tongspages/pooltest.html",
            temporaryFolder.root, "test.html", model
        )
    }

    @Test
    fun tableDataIsCorrectlyDisplayed() {
        val model = aTestCaseRunResult(datas = listOf(SimpleTableReportData("Table-title",
                tableOf(
                        listOf("foo", "bar"),
                        listOf("1", "2"),
                        listOf("3", "4")
                )
        )))
        val htmlGenerator by KoinContextHandler.get().inject<HtmlGenerator>()
        val result = htmlGenerator.generateHtmlFromInline(
     """
            {{#data}}
                - title: {{title}}
                {{#table}}
                    {{#headers}}|{{title}}{{/headers}}|
                    {{#rows}}
                    {{#cells}}{{.}}-{{/cells}}
                    {{/rows}}
                {{/table}}
            {{/data}}
            """.trimIndent(), model
 )
     .lines()
     .filter { it.isNotBlank() }
                .joinToString("\n")

        Assert.assertEquals("""
            |    - title: Table-title
            |        |foo|bar|
            |        1-2-
            |        3-4-"""
                .trimMargin("|"), result)
    }

    private fun aTestCaseRunResult(datas: List<TestReportData>): TestCaseRunResult {
        return TestCaseRunResult(
                pool, TEST_DEVICE,
                aTestCase("Class", "method"),
                ResultStatus.FAIL, listOf(StackTrace("", "stackTrace", "stackTrace\n\ttrace")),
                Instant.now(), Instant.now().plusMillis(10),
                Instant.now(), Instant.now().plusMillis(10),
                3, mapOf("metric" to "value"),
                null,
                datas
        )
    }
}