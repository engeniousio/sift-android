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
package com.github.tarcv.tongs.runner.listeners

import com.github.tarcv.tongs.api.TongsConfiguration
import com.github.tarcv.tongs.api.devices.Diagnostics
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.FileTableReportData
import com.github.tarcv.tongs.api.result.ImageReportData
import com.github.tarcv.tongs.api.result.LinkedFileReportData
import com.github.tarcv.tongs.api.result.SimpleMonoTextReportData
import com.github.tarcv.tongs.api.result.SimpleMonoTextReportData.Type
import com.github.tarcv.tongs.api.result.StackTrace
import com.github.tarcv.tongs.api.result.Table
import com.github.tarcv.tongs.api.result.TestCaseFileManager
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.result.TestReportData
import com.github.tarcv.tongs.api.result.VideoReportData
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.run.TestCaseEvent.Companion.TEST_TYPE_TAG
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.injector.GsonInjector.gson
import com.github.tarcv.tongs.model.AndroidDevice
import com.github.tarcv.tongs.runner.AndroidRunContext
import com.github.tarcv.tongs.util.parseJavaTrace
import java.time.Instant

interface IResultProducer {
    fun requestListeners(): List<RunListener>
    fun getResult(): TestCaseRunResult
}

class TestCollectorResultProducer(private val pool: Pool, private val device: AndroidDevice): IResultProducer {
    override fun requestListeners(): List<RunListener> = emptyList()

    override fun getResult(): TestCaseRunResult {
        return TestCaseRunResult(
                pool, device, TestCase(TEST_TYPE_TAG, "dummy", "dummy.Dummy", "dummy", listOf("dummy"), includedDevices = null),
                ResultStatus.PASS, emptyList(),
                Instant.now(), Instant.now(), Instant.now(), Instant.now(),
                0,
                emptyMap(), null, emptyList()
        )
    }

}

class ResultProducer(
        private val context: AndroidRunContext
) : IResultProducer {
    private val androidDevice = context.device
    private val resultListener = ResultListener(context.testCaseEvent.testCase.toString())
    private val logCatListener = LogCatTestRunListener(gson(), context.fileManager, context.pool, androidDevice,
            context.testCaseEvent.testCase)
    private val screenTraceListener = getScreenTraceTestRunListener(context.fileManager, androidDevice)
    private val coverageListener = getCoverageTestRunListener(context.configuration, androidDevice, context.fileManager, context.pool, context.testCaseEvent)

    override fun requestListeners(): List<RunListener> {
        return listOf(
                resultListener,
                logCatListener,
                screenTraceListener,
                coverageListener)
    }

    override fun getResult(): TestCaseRunResult {
        val shellResult = resultListener.result

        val gson = gson()
        val reportBlocks = listOfNotNull(
                addOutput(shellResult.output),
                addTraceReport(screenTraceListener),
                FileTableReportData("Logcat", logCatListener.tableFile, { tableFile ->
                    tableFile
                            .bufferedReader(Charsets.UTF_8)
                            .use { reader ->
                                gson.fromJson(reader, Table.TableJson::class.java)
                            }
                }),
                LinkedFileReportData("Logcat", logCatListener.rawFile),
                LinkedFileReportData("Logcat as JSON", logCatListener.tableFile)
        )

        val coverageReport = if (coverageListener is CoverageListener) {
            coverageListener.coverageFile
        } else {
            null
        }

        val stackTrace = if (shellResult.status == null) {
            StackTrace(
                    "RunError", "Failed to get the test result",
                    "Failed to get the test result" + (System.lineSeparator().repeat(2)) + shellResult.trace
            )
        } else {
            parseJavaTrace(shellResult.trace)
        }
        return TestCaseRunResult(
                context.pool, androidDevice,
                context.testCaseEvent.testCase,
                shellResult.status ?: ResultStatus.ERROR,
                listOf(stackTrace),
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.ofEpochMilli(shellResult.startTime ?: 0),
                Instant.ofEpochMilli(shellResult.endTime ?: 0),
                0, // TODO
                shellResult.metrics,
                coverageReport,
                reportBlocks)
    }

    private fun addOutput(output: String): SimpleMonoTextReportData? {
        return if (output.isEmpty()) {
            null
        } else {
            SimpleMonoTextReportData("Shell output", Type.STDOUT, output)
        }
    }

    private fun addTraceReport(screenTraceListener: RunListener): TestReportData? {
        val dataTitle = "Screen recording"
        return if (screenTraceListener is ScreenRecorderTestRunListener) {
            VideoReportData(dataTitle, screenTraceListener.file)
        } else if (screenTraceListener is ScreenCaptureTestRunListener) {
            ImageReportData(dataTitle, screenTraceListener.file)
        } else {
            null
        }
    }

    private fun getScreenTraceTestRunListener(fileManager: TestCaseFileManager, device: AndroidDevice): RunListener {
        return if (Diagnostics.VIDEO == device.supportedVisualDiagnostics) {
            ScreenRecorderTestRunListener(fileManager, device)
        } else if (Diagnostics.SCREENSHOTS == device.supportedVisualDiagnostics && context.configuration.canFallbackToScreenshots()) {
            ScreenCaptureTestRunListener(fileManager, device)
        } else {
            NoOpRunListener()
        }
    }

    private fun getCoverageTestRunListener(configuration: TongsConfiguration,
                                           device: AndroidDevice,
                                           fileManager: TestCaseFileManager,
                                           pool: Pool,
                                           testCase: TestCaseEvent): RunListener {
        return if (configuration.isCoverageEnabled) {
            CoverageListener(device, fileManager, pool, testCase)
        } else {
            NoOpRunListener()
        }
    }
}
