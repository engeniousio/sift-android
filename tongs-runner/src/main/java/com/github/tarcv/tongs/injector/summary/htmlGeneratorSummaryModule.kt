/*
 * Copyright 2021 TarCV
 * Copyright 2015 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.injector.summary

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.HumanizeHelper
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache
import com.github.jknack.handlebars.helper.ConditionalHelpers
import com.github.jknack.handlebars.helper.StringHelpers
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import com.github.jknack.handlebars.io.TemplateLoader
import com.github.tarcv.tongs.injector.modulesCreatedAtStart
import com.github.tarcv.tongs.io.HtmlGenerator
import com.github.tarcv.tongs.summary.TongsHelpers.Companion.register
import org.koin.dsl.module
import java.util.Arrays
import java.util.stream.Collectors

val htmlGeneratorSummaryModule = module(createdAtStart = modulesCreatedAtStart) {
    single {
        val loader: TemplateLoader = ClassPathTemplateLoader()
        loader.suffix = ""
        val handlebars = Handlebars(loader).with(CACHE)
        registerHelpers(handlebars)

        HtmlGenerator(handlebars)
    }
}
private val CACHE = ConcurrentMapTemplateCache()

private fun registerHelpers(handlebars: Handlebars) {
    registerEnumHelpers(handlebars, ConditionalHelpers.values())
    val forcedHumanizeHelpers = arrayOf(HumanizeHelper.wordWrap)
    registerNonConflictingHelpers(handlebars, HumanizeHelper.values(), StringHelpers.values())
    registerNonConflictingHelpers(handlebars, StringHelpers.values(), forcedHumanizeHelpers)
    registerEnumHelpers(handlebars, forcedHumanizeHelpers)
    register(handlebars)
}

private fun <T1, T2> registerNonConflictingHelpers(
    handlebars: Handlebars,
    helpers: Array<T1>,
    excludeConflictsWith: Array<T2>
)
        where T1: Enum<T1>, T1: Helper<*>, T2: Enum<T2>, T2: Helper<*>
{
    val reservedNames = Arrays.stream(excludeConflictsWith)
        .map { obj: T2 -> obj.name }
        .collect(Collectors.toSet())
    helpers
        .filter { helper: T1 -> !reservedNames.contains(helper.name) }
        .forEach { helper: T1 -> registerEnumHelper(handlebars, helper) }
}

private fun <T> registerEnumHelper(handlebars: Handlebars, helper: T): Handlebars
        where T: Enum<*>, T: Helper<*>
{
    return handlebars.registerHelper(helper.name, helper as Helper<*>)
}

private fun <T> registerEnumHelpers(handlebars: Handlebars, helpers: Array<T>)
        where T: Enum<*>, T: Helper<*>
{
    for (helper in helpers) {
        registerEnumHelper(handlebars, helper)
    }
}
