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
package com.github.tarcv.tongs.runner

import com.android.ddmlib.AndroidDebugBridge
import com.github.tarcv.tongs.api.run.RunRule
import com.github.tarcv.tongs.api.run.RunRuleContext
import com.github.tarcv.tongs.api.run.RunRuleFactory

class AndroidDdmRunRuleFactory: RunRuleFactory<AndroidDdmRunRule> {
    override fun runRules(context: RunRuleContext): Array<out AndroidDdmRunRule> {
        return arrayOf(AndroidDdmRunRule(context.configuration.shouldTerminateDdm()));
    }
}

class AndroidDdmRunRule(private val enabled: Boolean): RunRule {

    override fun before() {
        // no op
    }

    override fun after() {
        if (enabled) {
            AndroidDebugBridge.terminate();
        }
    }

}