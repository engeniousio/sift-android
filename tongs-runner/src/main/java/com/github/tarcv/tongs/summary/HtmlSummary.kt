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
package com.github.tarcv.tongs.summary

import com.github.tarcv.tongs.api.result.TestCaseRunResult

/**
 * Plain bean class, to feed to Moustache markup files.
 */
class HtmlSummary(
        val pools: Collection<HtmlPoolSummary>,
        val title: String,
        val subtitle: String,
        val ignoredTests: List<TestCaseRunResult>,
        val overallStatus: String, // TODO: replace strings with appropriate objects
        val flakyTests: List<TestCaseRunResult>,
        val failedTests: List<TestCaseRunResult>,
        val fatalCrashedTests: List<TestCaseRunResult>,
        val fatalErrors: List<String>
)
