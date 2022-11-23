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
package com.github.tarcv.tongs.injector.summary

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.injector.modulesCreatedAtStart
import com.github.tarcv.tongs.io.HtmlGenerator
import com.github.tarcv.tongs.summary.HtmlSummaryPrinter
import com.github.tarcv.tongs.summary.JsonSummarySerializer
import com.github.tarcv.tongs.summary.LogSummaryPrinter
import com.github.tarcv.tongs.summary.SummaryPrinter
import com.github.tarcv.tongs.summary.XmlResultWriter
import com.github.tarcv.tongs.summary.XmlSummaryPrinter
import com.github.tarcv.tongs.system.io.FileManager
import com.google.gson.Gson
import org.koin.core.context.GlobalContext
import org.koin.dsl.bind
import org.koin.dsl.module

val summaryPrinterModule = module(createdAtStart = modulesCreatedAtStart) {
    factory {
        LogSummaryPrinter()
    } bind SummaryPrinter::class

    factory {
        val htmlGenerator by GlobalContext.get().inject<HtmlGenerator>()
        HtmlSummaryPrinter(get<Configuration>().output, htmlGenerator)
    } bind SummaryPrinter::class

    factory {
        val fileManager by GlobalContext.get().inject<FileManager>()
        XmlSummaryPrinter(get<Configuration>().output, fileManager, XmlResultWriter())
    } bind SummaryPrinter::class

    factory {
        val fileManager by GlobalContext.get().inject<FileManager>()
        val gson by GlobalContext.get().inject<Gson>()
        JsonSummarySerializer(fileManager, gson)
    } bind SummaryPrinter::class
}