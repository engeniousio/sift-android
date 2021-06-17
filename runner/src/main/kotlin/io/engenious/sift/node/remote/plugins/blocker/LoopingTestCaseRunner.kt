package io.engenious.sift.node.remote.plugins.blocker

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.RunTesult
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseRunner
import com.github.tarcv.tongs.api.run.TestCaseRunnerArguments
import com.github.tarcv.tongs.api.run.TestCaseRunnerContext
import com.github.tarcv.tongs.api.run.TestCaseRunnerFactory
import com.github.tarcv.tongs.api.testcases.TestCase
import io.engenious.sift.node.remote.plugins.blocker.LoopingTestCaseProvider.Companion.loopingTestCase
import java.time.Instant
import java.util.concurrent.CountDownLatch

class LoopingTestCaseRunner(
    private val pool: Pool,
    private val device: Device,
    private val signaller: CountDownLatch
) : TestCaseRunner {

    override fun run(arguments: TestCaseRunnerArguments): RunTesult {
        signaller.await()
        return TestCaseRunResult(
            pool,
            device,
            arguments.testCaseEvent.testCase,
            ResultStatus.PASS,
            emptyList(),
            Instant.now(),
            Instant.now(),
            Instant.now(),
            Instant.now(),
            0,
            emptyMap(),
            null,
            emptyList()
        )
    }

    override fun supports(device: Device, testCase: TestCase): Boolean {
        return device == LoopingDevice && testCase == loopingTestCase
    }
}
class LoopingTestCaseRunnerFactory(private val signaller: CountDownLatch) : TestCaseRunnerFactory<TestCaseRunner> {
    override fun testCaseRunners(context: TestCaseRunnerContext): Array<out TestCaseRunner> = arrayOf(
        LoopingTestCaseRunner(context.pool, context.device, signaller)
    )
}
