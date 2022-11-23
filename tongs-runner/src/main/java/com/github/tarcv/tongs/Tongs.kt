/*
 * Copyright 2021 TarCV
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
package com.github.tarcv.tongs

import com.github.tarcv.tongs.api.run.RunConfiguration
import com.github.tarcv.tongs.api.run.RunRule
import com.github.tarcv.tongs.api.run.RunRuleContext
import com.github.tarcv.tongs.api.run.RunRuleFactory
import com.github.tarcv.tongs.injector.GsonInjector
import com.github.tarcv.tongs.injector.RuleManagerFactory
import com.github.tarcv.tongs.injector.accumulatorModule
import com.github.tarcv.tongs.injector.deviceGeometryModule
import com.github.tarcv.tongs.injector.deviceModule
import com.github.tarcv.tongs.injector.listenersModule
import com.github.tarcv.tongs.injector.modulesCreatedAtStart
import com.github.tarcv.tongs.injector.poolingModule
import com.github.tarcv.tongs.injector.runnerModule
import com.github.tarcv.tongs.injector.summary.htmlGeneratorSummaryModule
import com.github.tarcv.tongs.injector.summary.summaryModule
import com.github.tarcv.tongs.injector.summary.summaryPrinterModule
import com.github.tarcv.tongs.injector.systemModule
import com.github.tarcv.tongs.injector.testLoadingModule
import com.github.tarcv.tongs.injector.withRules
import com.github.tarcv.tongs.runner.AndroidDdmRunRuleFactory
import com.google.gson.Gson
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.time.DurationFormatUtils
import org.koin.core.Koin
import org.koin.core.context.KoinContextHandler
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.io.File

class Tongs(private val configuration: Configuration) {
    @JvmOverloads
    fun run(allowThrows: Boolean = false): Boolean {
        injectAll(configuration)

        val tongsRunner by KoinContextHandler.get().inject<TongsRunner>()
        try {
            val startOfTestsMs = System.nanoTime()
            val predefinedRulesFactories = listOf(
                    PrepareOutputDirectoryRuleFactory(),
                    AndroidDdmRunRuleFactory()
            )
            val ruleManagerFactory by KoinContextHandler.get().inject<RuleManagerFactory>()
            val runRules = ruleManagerFactory
                    .create<RunRuleContext, RunRule, RunRuleFactory<RunRule>>(
                            RunRuleFactory::class.java,
                            predefinedRulesFactories
                    ) { runRuleFactory: RunRuleFactory<RunRule>, runRuleContext: RunRuleContext ->
                        runRuleFactory.runRules(runRuleContext)
                    }
                    .createRulesFrom { configuration: RunConfiguration ->
                        RunRuleContext(configuration)
                    }

            return try {
                withRules(
                        logger,
                        "while executing a run rule",
                        "while running Tongs",
                        runRules,
                        { it.before() },
                        { it, ret ->
                            it.after()
                            ret
                        }
                ) {
                    if (allowThrows) {
                        tongsRunner.throwingRun()
                    } else {
                        tongsRunner.run()
                    }
                }
            } catch (e: Exception) {
                if (allowThrows) {
                    throw e
                } else {
                    false
                }
            } finally {
                val duration = Utils.millisSinceNanoTime(startOfTestsMs)
                logger.info(DurationFormatUtils.formatPeriod(0, duration,
                        "'Total time taken:' H 'hours' m 'minutes' s 'seconds'"))
            }
        } finally {
            stopKoin()
        }
    }

    class PrepareOutputDirectoryRuleFactory(): RunRuleFactory<PrepareOutputDirectoryRule> {
        override fun runRules(context: RunRuleContext): Array<out PrepareOutputDirectoryRule> {
            return arrayOf(PrepareOutputDirectoryRule(context.configuration.output))
        }
    }

    class PrepareOutputDirectoryRule(private val output: File): RunRule {
        override fun before() {
            FileUtils.deleteDirectory(output)
            //noinspection ResultOfMethodCallIgnored
            output.mkdirs()
        }

        override fun after() {
            // no-op
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Tongs::class.java)

        fun injectAll(configuration: Configuration): Koin {
            val startModule = module(createdAtStart = modulesCreatedAtStart) {
                single { configuration }
                single {
                    val conf = get<Configuration>()
                    RuleManagerFactory(conf, conf.pluginsInstances)
                }
                single<Gson> {
                    GsonInjector.gson()
                }
            }

            return startKoin {
                modules(
                    startModule,

                    htmlGeneratorSummaryModule, // needs nothing
                    accumulatorModule,
                    deviceModule, // needs Configuration
                    poolingModule, // needs Configuration, RuleManagerFactory
                    testLoadingModule, // needs RuleManagerFactory
                    deviceGeometryModule, // needs CommandOutputLogger from poolingModule
                    systemModule, // needs Configuration
                    runnerModule,
                    listenersModule,
                    summaryPrinterModule,
                    summaryModule
                )
            }.koin
        }
    }
}