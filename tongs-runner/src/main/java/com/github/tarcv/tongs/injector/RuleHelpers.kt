/*
 * Copyright 2021 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.injector

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.api.HasConfiguration
import com.github.tarcv.tongs.api.TongsConfiguration
import com.github.tarcv.tongs.api.run.RunConfiguration
import org.slf4j.Logger
import java.lang.reflect.InvocationTargetException
import java.util.IdentityHashMap

class RuleManagerFactory(
        private val configuration: Configuration,
        private val allUserFactories: List<Any>
) {
    private val configurationSectionsMap: IdentityHashMap<HasConfiguration, Map<String, String>> = buildConfigurationSectionsMap(allUserFactories)

    @JvmOverloads
    fun <C, R, F : Any> create(
            factoryClass: Class<F>,
            predefinedFactories: List<F> = emptyList(),
            factoryInvoker: (F, C) -> Array<out R>
    ): RuleManager<C, R, F> {
        val filteredPredefinedFactories = predefinedFactories
            .filter {
                it.javaClass.name !in configuration.excludedPlugins
            }
        val userFactories = allUserFactories.filterIsInstance(factoryClass)
        return RuleManager(factoryClass, filteredPredefinedFactories, userFactories, factoryInvoker)
    }

    internal fun <F> configurationForFactory(factory: F): Configuration {
        val expectedSections: Map<String, String> = if (factory is HasConfiguration) {
            configurationSectionsMap[factory] ?: emptyMap()
        } else {
            emptyMap()
        }

        val pluginConfigurationSections = configuration.pluginConfiguration
                .map { configurationSection ->
                    val originalName = expectedSections[configurationSection.key]
                    if (originalName == null) {
                        null
                    } else {
                        originalName to configurationSection.value
                    }
                }
                .filterNotNull()
                .toMap()
        return configuration.withPluginConfiguration(pluginConfigurationSections)
    }

    private fun buildConfigurationSectionsMap(allUserFactories: List<Any>): IdentityHashMap<HasConfiguration, Map<String, String>> {
        return allUserFactories
                .asSequence()
                .flatMap { factory ->
                    if (factory is HasConfiguration) {
                        val simpleNamesSections = factory.configurationSections
                        val simpleNamesSectionPairs = simpleNamesSections
                                .asSequence()
                                .map { section -> SectionInfo(section, section, factory) }
                        val fqNamesSectionPairs = simpleNamesSections
                                .asSequence()
                                .map { section -> SectionInfo(section, "$section/${factory.javaClass.name}", factory) }

                        (simpleNamesSectionPairs + fqNamesSectionPairs)
                    } else {
                        emptySequence()
                    }
                }
                .distinctBy { it.candidateName } // now all candidateNames are unique (and, if possible, are simple)

                .groupByTo(
                        IdentityHashMap(),
                        { it.consumingFactory }
                )
                .mapValuesTo(
                        IdentityHashMap(),
                        { (_, v) ->
                            v
                                    .associateBy(
                                            { it.candidateName },
                                            { it.originalName }
                                    )
                        }
                )
    }

    private class SectionInfo(
            val originalName: String,
            val candidateName: String,
            val consumingFactory: HasConfiguration
    ) {
        fun withConfigurationName(configurationName: String): SectionInfo {
            assert(this.candidateName.isEmpty())

            return SectionInfo(
                    originalName = this.originalName,
                    candidateName = configurationName,
                    consumingFactory = this.consumingFactory
            )
        }
    }

    companion object {
        @JvmStatic
        fun factoryInstancesForRuleNames(ruleClassNames: Collection<String>): List<Any> {
            return ruleClassNames
                    .asSequence()
                    .map { className ->
                        @Suppress("UNCHECKED_CAST")
                        Class.forName(className) as Class<Any>
                    }
                    .map { clazz ->
                        try {
                            val ctor = clazz.getConstructor()
                            ctor.newInstance()
                        } catch (e: InvocationTargetException) {
                            throw RuntimeException(e.targetException) //TODO
                        }
                    }
                    .toList()
        }
    }

    inner class RuleManager<C, out R, F> internal constructor(
            private val factoryClass: Class<F>,
            private val predefinedFactories: List<F> = emptyList(),
            private val userFactories: List<F>,
            private val factoryInvoker: (F, C) -> Array<out R>
    ) {

        fun createRulesFrom(contextProvider: (RunConfiguration) -> C): List<R> {
            val instances = ArrayList<R>()

            ruleInstancesFromFactories(predefinedFactories, contextProvider).toCollection(instances)
            ruleInstancesFromFactories(userFactories, contextProvider).toCollection(instances)

            return instances
        }

        private fun ruleInstancesFromFactories(
                factories: List<F>,
                contextProvider: (RunConfiguration) -> C
        ): Sequence<R> {
            return factories.asSequence()
                    .flatMap { factory ->
                        try {
                            val factoryConfiguration = ActualConfiguration(configurationForFactory(factory))
                            val ruleContext = contextProvider(factoryConfiguration)
                            factoryInvoker(factory, ruleContext).asSequence()
                        } catch (e: InvocationTargetException) {
                            throw RuntimeException(e.targetException) //TODO
                        }
                    }
        }
    }
}

class ActualConfiguration(configuration: Configuration)
    : RunConfiguration,
        TongsConfiguration by configuration

inline fun <R, V>withRulesWithoutAfter(
        logger: Logger,
        inRuleText: String,
        inActionText: String,
        rules: List<R>,
        beforeAction: (R) -> Unit,
        block: () -> V
): Pair<List<R>, Result<V>> {
    val (allowedAfterRules, lastException) = run {
        var lastException: Throwable? = null
        val allowedRules = rules.takeWhile {
            if (lastException == null) {
                try {
                    beforeAction(it)
                } catch (t: Throwable) {
                    logger.error("Exception $inRuleText (in before method)", t)
                    lastException = t
                }
                return@takeWhile true
            } else {
                return@takeWhile false
            }
        }

        Pair(allowedRules, lastException)
    }

    val actionResult: Result<V> = if (lastException == null) {
        try {
            Result.success(block())
        } catch (t: Throwable) {
            logger.error("Exception $inActionText")
            Result.failure<V>(t)
        }
    } else {
        Result.failure<V>(lastException)
    }
    return Pair(allowedAfterRules, actionResult)
}
inline fun <R, V>withRules(
        logger: Logger,
        inRuleText: String,
        inActionText: String,
        rules: List<R>,
        beforeAction: (R) -> Unit,
        afterAction: (R, Result<V>) -> Result<V>,
        block: () -> V
): V {
    val (allowedAfterRules, actionResult) = withRulesWithoutAfter(logger, inRuleText, inActionText, rules, beforeAction, block)

    return allowedAfterRules
            .asReversed()
            .fold(actionResult) { acc, it ->
                try {
                    afterAction(it, acc)
                            .also {
                                if (it.exceptionOrNull() != actionResult.exceptionOrNull()) {
                                    throw IllegalStateException("Rules must not change thrown exceptions")
                                }
                            }
                } catch (t: Throwable) {
                    logger.error("Exception $inRuleText (in after method)", t)
                    acc
                            .fold(
                                    { t },
                                    { it.apply { addSuppressed(t) } }
                            )
                            .let {
                                Result.failure(it)
                            }
                }
            }
            .getOrThrow()
}