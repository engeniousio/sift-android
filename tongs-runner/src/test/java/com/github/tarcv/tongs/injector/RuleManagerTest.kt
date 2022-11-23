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
package com.github.tarcv.tongs.injector

import com.github.tarcv.tongs.aConfigurationBuilder
import com.github.tarcv.tongs.api.HasConfiguration
import com.github.tarcv.tongs.api.run.RunConfiguration
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Test
import kotlin.reflect.jvm.jvmName

class RuleManagerTest {
    @Test
    fun example() {
        abstract class ActualRule

        class PredefinedActualRule : ActualRule()

        class DefaultActualRule : ActualRule()

        @Suppress("UNUSED_PARAMETER")
        class ActualRuleContext(someDependency: Int)

        abstract class ActualRuleFactory<out T : ActualRule> {
            abstract fun actualRules(context: ActualRuleContext): Array<out T>
        }

        class DefaultActualRuleFactory : ActualRuleFactory<DefaultActualRule>() {
            override fun actualRules(context: ActualRuleContext): Array<out DefaultActualRule> {
                return arrayOf(DefaultActualRule())
            }
        }

        class PredefinedActualRuleFactory : ActualRuleFactory<PredefinedActualRule>() {
            override fun actualRules(context: ActualRuleContext): Array<out PredefinedActualRule> {
                return arrayOf(PredefinedActualRule())
            }
        }

        val ruleManagerFactory = RuleManagerFactory(
                aConfigurationBuilder().build(true),
                listOf(DefaultActualRuleFactory())
        )

        (0..1).forEach { num ->
            val ruleManager = ruleManagerFactory.create(
                    ActualRuleFactory::class.java,
                    listOf(PredefinedActualRuleFactory()),
                    { factory, context: ActualRuleContext -> factory.actualRules(context) }
            )

            val ruleNames = ruleManager
                    .createRulesFrom { ActualRuleContext(num) }
                    .map {
                        it.javaClass.name
                    }
            Assert.assertEquals(
                    listOf(
                            PredefinedActualRule::class.java.name,
                            DefaultActualRule::class.java.name),
                    ruleNames)
        }
    }

    @Test
    fun factoriesGetExpectedConfigurations() {
        val uniqueSectionName = "UniqueSection"
        val conflictingName = "ConflictingSection"

        class RuleContext(val configuration: RunConfiguration)

        class Rule(val factoryName: String, context: RuleContext) {
            val pluginConfiguration = context.configuration.pluginConfiguration
        }

        open class RuleFactory {
            fun create(context: RuleContext) = Rule(this.javaClass.simpleName, context)
        }

        class RuleFactoryWithEmptyConfiguration : RuleFactory(), HasConfiguration {
            override val configurationSections: Array<String> = emptyArray()
        }

        class RuleFactoryWithNoConfiguration : RuleFactory(), HasConfiguration {
            override val configurationSections: Array<String> = emptyArray()
        }

        class RuleFactoryWithUniqueConfigurationSection : RuleFactory(), HasConfiguration {
            override val configurationSections: Array<String> = arrayOf(uniqueSectionName)
        }

        class RuleFactoryWithConflictingConfigurationSection1 : RuleFactory(), HasConfiguration {
            override val configurationSections: Array<String> = arrayOf(conflictingName)
        }

        class RuleFactoryWithConflictingConfigurationSection2 : RuleFactory(), HasConfiguration {
            override val configurationSections: Array<String> = arrayOf(conflictingName)
        }

        val globalConfiguration = aConfigurationBuilder()
                .withPluginConfiguration(mapOf(
                        uniqueSectionName to "${uniqueSectionName}Value",
                        conflictingName to "${conflictingName}Value1",
                        "${conflictingName}/${RuleFactoryWithConflictingConfigurationSection1::class.jvmName}" to
                                "${conflictingName}Value1 under qualified section",
                        "${conflictingName}/${RuleFactoryWithConflictingConfigurationSection2::class.jvmName}" to
                                "${conflictingName}Value2"
                ))
                .build(true)

        val ruleManagerFactory = RuleManagerFactory(
                globalConfiguration,
                listOf(
                        RuleFactoryWithEmptyConfiguration(),
                        RuleFactoryWithNoConfiguration(),
                        RuleFactoryWithUniqueConfigurationSection(),
                        RuleFactoryWithConflictingConfigurationSection1(),
                        RuleFactoryWithConflictingConfigurationSection2()
                ))
        val rules = ruleManagerFactory
                .create(
                        RuleFactory::class.java,
                        emptyList(),
                        { factory, context: RuleContext ->
                            arrayOf(factory.create(context))
                        }
                ).createRulesFrom { configuration ->
                    RuleContext(configuration)
                }

        fun ruleFromFactory(name: String): Rule {
            return rules.single {
                val factoryName = it.factoryName.takeLastWhile { it != '$' }
                factoryName == name
            }
        }

        assertThat(ruleFromFactory("RuleFactoryWithEmptyConfiguration").pluginConfiguration, `is`(emptyMap()))
        assertThat(ruleFromFactory("RuleFactoryWithNoConfiguration").pluginConfiguration, `is`(emptyMap()))
        assertThat(ruleFromFactory("RuleFactoryWithUniqueConfigurationSection").pluginConfiguration.entries,
                `is`(mapOf<String, Any>(uniqueSectionName to "${uniqueSectionName}Value").entries))
        assertThat(ruleFromFactory("RuleFactoryWithConflictingConfigurationSection1").pluginConfiguration.entries,
                `is`(mapOf<String, Any>(
                        conflictingName to "${conflictingName}Value1",
                        conflictingName to "${conflictingName}Value1 under qualified section"
                ).entries))
        assertThat(ruleFromFactory("RuleFactoryWithConflictingConfigurationSection2").pluginConfiguration.entries,
                `is`(mapOf<String, Any>(conflictingName to "${conflictingName}Value2").entries))
    }
}
