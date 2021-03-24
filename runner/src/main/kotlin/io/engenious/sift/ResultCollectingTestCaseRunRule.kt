package io.engenious.sift

import com.android.ddmlib.IDevice
import com.github.tarcv.tongs.api.result.FileType
import com.github.tarcv.tongs.api.result.ImageReportData
import com.github.tarcv.tongs.api.result.TestCaseFile
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseRunRule
import com.github.tarcv.tongs.api.run.TestCaseRunRuleAfterArguments
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.run.TestCaseRunRuleFactory
import com.github.tarcv.tongs.system.adb.CollectingShellOutputReceiver
import io.engenious.sift.run.ResultData
import io.engenious.sift.run.RunData
import java.io.File

class ResultCollectingPlugin :
    Conveyor.Plugin<RunData, ResultData>(), TestCaseRunRuleFactory<TestCaseRunRule> {
    // This plugin should not advance the conveyor by itself,
    // instead the conveyor is advanced automatically once the current test run is complete

    override fun testCaseRunRules(context: TestCaseRunRuleContext): Array<out TestCaseRunRule> {
        return arrayOf(ResultCollectingTestCaseRunRule(previousStorage.enabledTests, storage.results, context))
    }

    override fun initStorage(): ResultData = ResultData(previousStorage.runId)
}

open class ResultCollectingTestCaseRunRule(
    private val identifierToIdMapping: Map<TestIdentifier, Int>,
    private val testResults: MutableMap<TestIdentifier, FilledTestResult>,
    private val context: TestCaseRunRuleContext
) : TestCaseRunRule {
    private val screenshotPath = "/sdcard/failure.png"
    private val device = context.device.deviceInterface as IDevice

    override fun after(arguments: TestCaseRunRuleAfterArguments) {
        val testIdentifier = TestIdentifier.fromTestCase(arguments.result.testCase)
        val key = identifierToIdMapping[testIdentifier]
            ?: throw IllegalStateException("Orchestrator didn't send test id for $testIdentifier")

        val status = Status.fromTestCaseRunResult(arguments.result)
        val screenshot = if (status == Status.FAILED || status == Status.ERRORED) {
            val attemptIndex = arguments.result.totalFailureCount

            pullScreenshot(attemptIndex.toString())?.let {
                addScreenshotToHtmlReport(it, arguments)
                it.toFile()
            }
        } else {
            null
        }

        testResults[testIdentifier] = FilledTestResult(
            key,
            Result.fromTestCaseRunResult(arguments.result, screenshot)
        )
    }

    private fun addScreenshotToHtmlReport(
        localFile: TestCaseFile,
        arguments: TestCaseRunRuleAfterArguments
    ) {
        val screenshotData = ImageReportData(
            "Failure screenshot",
            localFile
        )
        arguments.result = arguments.result.copy(data = listOf(screenshotData) + arguments.result.data)
    }

    private fun pullScreenshot(nameSuffix: String) = try {
        val localFile = context.fileManager.testCaseFile(
            ScreenshotFileType,
            nameSuffix
        )
        (context.device.deviceInterface as IDevice).apply {
            pullFile(screenshotPath, localFile.create().absolutePath)
        }

        localFile
    } catch (e: Exception) {
        System.err.println("Failed to pull the failure screenshot $screenshotPath:") // TODO: convert to log call
        e.printStackTrace(System.err)
        null
    }

    override fun before() {
        try {
            device.executeShellCommand(
                "rm $screenshotPath",
                CollectingShellOutputReceiver()
            )
        } catch (e: Exception) {
            System.err.println("Failed to remove old screenshot:") // TODO: convert to log call
            e.printStackTrace(System.err)
        }
    }
}

enum class Status(val serialized: String) {
    FAILED("failed"),
    PASSED("passed"),
    PASSED_AFTER_RETRYING("passed_after_rerun"),
    ERRORED("failed"),
    SKIPPED("skipped");

    companion object {
        fun fromTestCaseRunResult(result: TestCaseRunResult) =
            when (result.status) {
                ResultStatus.PASS -> {
                    if (result.totalFailureCount == 0) {
                        PASSED
                    } else {
                        PASSED_AFTER_RETRYING
                    }
                }
                ResultStatus.FAIL -> FAILED
                ResultStatus.ERROR -> ERRORED
                ResultStatus.IGNORED, ResultStatus.ASSUMPTION_FAILED -> SKIPPED
            }
    }
}
data class Result(
    val status: Status,
    val errorMessage: String,
    val screenshot: File?
) {
    companion object {
        fun fromTestCaseRunResult(result: TestCaseRunResult, screenshot: File?): Result = Result(
            Status.fromTestCaseRunResult(result),
            result.stackTraces
                .joinToString("\n\n") { it.fullTrace },
            screenshot
        )
    }
}

class FilledTestResult(
    val id: Int,
    val result: Result
)

object ScreenshotFileType : FileType {
    override fun getDirectory(): String = "screenshots"

    override fun getSuffix(): String = "png"
}
