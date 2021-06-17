package io.engenious.sift.node.remote.hooks

import com.github.tarcv.tongs.TongsRunner
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.injector.RuleManagerFactory
import com.github.tarcv.tongs.model.TestCaseEventQueue
import com.github.tarcv.tongs.runner.DeviceTestRunnerFactory
import com.github.tarcv.tongs.runner.PoolTestRunner
import com.github.tarcv.tongs.runner.PoolTestRunnerFactory
import com.github.tarcv.tongs.runner.ProgressReporter
import io.engenious.sift.node.extractProperty
import java.util.concurrent.CountDownLatch

class CollectingPoolTestRunnerFactory(
    deviceTestRunnerFactory: DeviceTestRunnerFactory,
    ruleManagerFactory: RuleManagerFactory,
    private val testQueueConsumer: (TestCaseEventQueue) -> Unit
) : PoolTestRunnerFactory(deviceTestRunnerFactory, ruleManagerFactory) {
    override fun createPoolTestRunner(
        poolTask: TongsRunner.PoolTask,
        testCaseResults: MutableList<TestCaseRunResult>,
        poolCountDownLatch: CountDownLatch,
        progressReporter: ProgressReporter
    ): Runnable {
        val runner = super.createPoolTestRunner(poolTask, testCaseResults, poolCountDownLatch, progressReporter)
            as PoolTestRunner
        val queue = runner.extractProperty("testCases") as TestCaseEventQueue
        testQueueConsumer(queue)
        return runner
    }
}
