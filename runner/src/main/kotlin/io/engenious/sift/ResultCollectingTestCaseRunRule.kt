package io.engenious.sift

import com.android.ddmlib.IDevice
import com.github.tarcv.tongs.api.result.FileType
import com.github.tarcv.tongs.api.result.ImageReportData
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseRunRule
import com.github.tarcv.tongs.api.run.TestCaseRunRuleAfterArguments
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.run.TestCaseRunRuleFactory
import com.github.tarcv.tongs.system.adb.CollectingShellOutputReceiver

class ResultCollectingPlugin :
    Conveyor.Plugin<Set<TestIdentifier>, MutableMap<TestIdentifier, Boolean>>(),
    TestCaseRunRuleFactory<TestCaseRunRule> {
    // This plugin should not advance the conveyor by itself,
    // instead the conveyor is advanced automatically once the current test run is complete

    override fun testCaseRunRules(context: TestCaseRunRuleContext): Array<out TestCaseRunRule> {
        return arrayOf(ResultCollectingTestCaseRunRule(storage, context))
    }

    override fun initStorage(): MutableMap<TestIdentifier, Boolean> = HashMap()
}

open class ResultCollectingTestCaseRunRule(
    private val testResults: MutableMap<TestIdentifier, Boolean>,
    private val context: TestCaseRunRuleContext
) : TestCaseRunRule {
    private val screenshotPath = "/sdcard/failure.png"
    private val device = context.device.deviceInterface as IDevice

    override fun after(arguments: TestCaseRunRuleAfterArguments) {
        val result = when (arguments.result.status) {
            ResultStatus.PASS -> true
            ResultStatus.FAIL, ResultStatus.ERROR -> false
            ResultStatus.IGNORED, ResultStatus.ASSUMPTION_FAILED -> null
        }

        if (result != true) {
            val attemptIndex = arguments.result.totalFailureCount
            val localFile = pullScreenshot(attemptIndex.toString())

            localFile?.let {
                if (it.toFile().exists()) {
                    val screenshotData = ImageReportData(
                        "Failure screenshot",
                        it
                    )
                    arguments.result = arguments.result.copy(data = listOf(screenshotData) + arguments.result.data)
                }
            }
        }

        val key = TestIdentifier.fromTestCase(arguments.result.testCase)
        if (result != null) {
            testResults[key] = result
        } else {
            testResults.remove(key)
        }
    }

    private fun pullScreenshot(nameSuffix: String) = try {
        val localFile = context.fileManager.testCaseFile(
            ScreenshotFileType,
            nameSuffix
        )
        val actualFile = localFile.create()
        (context.device.deviceInterface as IDevice).apply {
            pullFile(screenshotPath, actualFile.absolutePath)
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

object ScreenshotFileType : FileType {
    override fun getDirectory(): String = "screenshots"

    override fun getSuffix(): String = "png"
}
