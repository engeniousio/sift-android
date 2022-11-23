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

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.injector.modulesCreatedAtStart
import com.github.tarcv.tongs.summary.CompositeSummaryPrinter
import com.github.tarcv.tongs.summary.OutcomeAggregator
import com.github.tarcv.tongs.summary.Summarizer
import com.github.tarcv.tongs.summary.SummaryCompiler
import com.github.tarcv.tongs.summary.SummaryGeneratorHook
import com.github.tarcv.tongs.summary.SummaryPrinter
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

val summaryModule = module(createdAtStart = modulesCreatedAtStart) {
    factory {
        CompositeSummaryPrinter(*getAll<SummaryPrinter>().toTypedArray())
    }
    factory {
        OutcomeAggregator()
    }
    factory {
        SummaryCompiler(get<Configuration>())
    }
    factory {
        Summarizer(
            get(),
            get(),
            get<CompositeSummaryPrinter>(),
            get()
        )
    }
    factory {
        val summarizer by GlobalContext.get().inject<Summarizer>()
        SummaryGeneratorHook(summarizer)
    }
}