/*
 * Copyright 2021 TarCV
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

import com.github.tarcv.tongs.api.run.TestCaseRunner
import com.github.tarcv.tongs.api.run.TestCaseRunnerContext
import com.github.tarcv.tongs.api.run.TestCaseRunnerFactory
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory

typealias TestCaseRuleManager = RuleManagerFactory.RuleManager<TestCaseRuleContext, TestCaseRule, TestCaseRuleFactory<TestCaseRule>>
typealias TestCaseRunnerManager = RuleManagerFactory.RuleManager<TestCaseRunnerContext, TestCaseRunner, TestCaseRunnerFactory<TestCaseRunner>>
