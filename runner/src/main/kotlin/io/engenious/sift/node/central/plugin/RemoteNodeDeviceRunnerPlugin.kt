package io.engenious.sift.node.central.plugin

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.result.RunTesult
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.TestCaseRunner
import com.github.tarcv.tongs.api.run.TestCaseRunnerArguments
import com.github.tarcv.tongs.api.run.TestCaseRunnerContext
import com.github.tarcv.tongs.api.run.TestCaseRunnerFactory
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.suite.ApkTestCase

class RemoteNodeDeviceRunnerPlugin : TestCaseRunnerFactory<TestCaseRunner> {
    override fun testCaseRunners(context: TestCaseRunnerContext): Array<out TestCaseRunner> {
        return arrayOf(object : TestCaseRunner {
            override fun run(arguments: TestCaseRunnerArguments): RunTesult {
                val device = context.device as RemoteNodeDevice
                val resultTimeoutMs = context.configuration.testOutputTimeout + 60_000
                return device.runTest(context.pool, arguments.testCaseEvent.testCase, resultTimeoutMs)
                    .let { it as TestCaseRunResult }
                    .copy(
                        pool = context.pool,
                        device = context.device,
                        testCase = arguments.testCaseEvent.testCase,
                        baseTotalFailureCount = arguments.testCaseEvent.totalFailureCount,
                        startTimestampUtc = arguments.startTimestampUtc
                    )
            }

            override fun supports(device: Device, testCase: TestCase): Boolean {
                return device is RemoteNodeDevice && testCase.typeTag == ApkTestCase::class.java
            }
        })
    }
}
