/*
 * Copyright 2021 TarCV
 * Copyright 2018 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.injector

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.TongsRunner
import com.github.tarcv.tongs.Utils
import com.github.tarcv.tongs.api.run.TestCaseRunnerContext
import com.github.tarcv.tongs.api.run.TestCaseRunnerFactory
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import com.github.tarcv.tongs.plugin.android.PropertiesTestCaseRuleFactory
import com.github.tarcv.tongs.runner.AndroidInstrumentedTestCaseRunnerFactory
import com.github.tarcv.tongs.runner.DeviceTestRunnerFactory
import com.github.tarcv.tongs.runner.OverallProgressReporter
import com.github.tarcv.tongs.runner.PoolProgressTrackers
import com.github.tarcv.tongs.runner.PoolTestRunnerFactory
import com.github.tarcv.tongs.runner.ProgressReporter
import org.koin.dsl.module
import org.slf4j.LoggerFactory

val runnerModule = module(createdAtStart = modulesCreatedAtStart) {
    factory { PoolProgressTrackers(mutableMapOf()) }

    factory {
        PoolTestRunnerFactory(get(), get())
    }

    factory<ProgressReporter> {
        OverallProgressReporter(
            get<Configuration>().totalAllowedRetryQuota,
            get<Configuration>().retryPerTestCaseQuota,
            get(),
            get()
        )
    }
    factory {
        DeviceTestRunnerFactory()
    }

    single {
        val startNanos = System.nanoTime()

        val ruleManagerFactory = get<RuleManagerFactory>()
        val ruleManager: TestCaseRuleManager = ruleManagerFactory.create(
            TestCaseRuleFactory::class.java,
            listOf(PropertiesTestCaseRuleFactory())
        ) { factory, context: TestCaseRuleContext -> factory.testCaseRules(context) }
        val runnerManager: TestCaseRunnerManager = ruleManagerFactory.create(
            TestCaseRunnerFactory::class.java,
            listOf(AndroidInstrumentedTestCaseRunnerFactory())
        ) { factory, context: TestCaseRunnerContext -> factory.testCaseRunners(context) }
        val tongsRunner = TongsRunner(
            get(),
            get(),
            get(),
            get(),
            ruleManager,
            runnerManager,
            get()
        )

        LoggerFactory.getLogger(TongsRunner::class.java)
            .debug("Bootstrap of TongsRunner took: {} milliseconds", Utils.millisSinceNanoTime(startNanos))
        tongsRunner
    }
}
