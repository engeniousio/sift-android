/*
 * Copyright 2020 TarCV
 * Copyright 2014 Shazam Entertainment Limited
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
package com.github.tarcv.tongs.runner

import com.github.tarcv.tongs.TongsRunner
import com.github.tarcv.tongs.Utils
import com.github.tarcv.tongs.api.run.PoolRunRuleContext
import com.github.tarcv.tongs.api.run.PoolRunRuleFactory
import com.github.tarcv.tongs.injector.RuleManagerFactory
import com.github.tarcv.tongs.injector.withRules
import com.github.tarcv.tongs.model.TestCaseEventQueue
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService

class PoolTestRunner(
        private val deviceTestRunnerFactory: DeviceTestRunnerFactory,
        private val poolTask: TongsRunner.PoolTask,
        private val testCases: TestCaseEventQueue,
        private val poolCountDownLatch: CountDownLatch,
        private val progressReporter: ProgressReporter,
        private val ruleManagerFactory: RuleManagerFactory
) : Runnable {
    override fun run() {
        val poolName = poolTask.pool.name
        val devicesInPool = poolTask.pool.size()
        val concurrentDeviceExecutor: ExecutorService = Utils.namedExecutor(devicesInPool, "DeviceExecutor-%d")
        try {
            logger.info("Pool {} started", poolName)
            runTestsAndRules(devicesInPool, concurrentDeviceExecutor)
        } catch (e: InterruptedException) {
            logger.warn("Pool {} was interrupted while running", poolName)
        } finally {
            concurrentDeviceExecutor.shutdown()
            logger.info("Pool {} finished", poolName)
            poolCountDownLatch.countDown()
            logger.info("Pools remaining: {}", poolCountDownLatch.count)
        }
    }

    private fun runTestsAndRules(devicesInPool: Int, concurrentDeviceExecutor: ExecutorService) {
        val deviceCountDownLatch = CountDownLatch(devicesInPool)
        val rules = ruleManagerFactory.create(PoolRunRuleFactory::class.java,
                emptyList(),
                { factory, context: PoolRunRuleContext -> factory.poolRules(context) })
                .createRulesFrom { configuration -> PoolRunRuleContext(configuration, poolTask.pool) }

        withRules(
                logger,
                "while executing a pool run rule", "while setting up test execution on a device",
                rules,
                { it.before() },
                { it, ret ->
                    it.after()
                    ret
                }
        ) {
            for (deviceRunner in poolTask.deviceRunners) {
                concurrentDeviceExecutor.execute {
                    deviceRunner.second.run(testCases, deviceCountDownLatch, progressReporter)
                }
            }
            deviceCountDownLatch.await()
        }
    }

    companion object {
        const val DROPPED_BY = "DroppedBy-"

        private val logger = LoggerFactory.getLogger(PoolTestRunner::class.java)
    }

}