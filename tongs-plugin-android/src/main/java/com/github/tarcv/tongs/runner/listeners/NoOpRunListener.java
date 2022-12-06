/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.runner.listeners;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.github.tarcv.tongs.api.run.ResultStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class NoOpRunListener implements RunListener {

    @Override
    public void onRunStarted() {

    }

    @Override
    public void onTestFinished(@NotNull TestIdentifier testIdentifier, @NotNull ResultStatus resultStatus, @NotNull String trace, boolean hasStarted) {

    }

    @Override
    public void onRunFailure(@NotNull String errorMessage) {

    }

    @Override
    public void onRunFinished() {

    }

    @Override
    public void addTestMetrics(@NotNull TestIdentifier testIdentifier, @NotNull Map<String, String> testMetrics, boolean hasStarted) {

    }

    @Override
    public void addRunData(@NotNull String runOutput, @NotNull Map<String, String> runMetrics) {

    }
}
