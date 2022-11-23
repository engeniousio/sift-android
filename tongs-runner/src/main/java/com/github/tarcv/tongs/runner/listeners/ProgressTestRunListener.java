/*
 * Copyright 2020 TarCV
 * Copyright 2015 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.runner.listeners;

import com.github.tarcv.tongs.api.devices.Pool;
import com.github.tarcv.tongs.api.result.TestCaseRunResult;
import com.github.tarcv.tongs.runner.PoolProgressTracker;
import com.github.tarcv.tongs.runner.ProgressReporter;
import org.jetbrains.annotations.NotNull;

class ProgressTestRunListener extends TongsTestListener {

    private final PoolProgressTracker poolProgressTracker;

    ProgressTestRunListener(Pool pool, ProgressReporter progressReporter) {
        poolProgressTracker = progressReporter.getProgressTrackerFor(pool);
    }

    @Override
    public void onTestFailed(@NotNull TestCaseRunResult failureResult) {
        poolProgressTracker.failedTest();
        poolProgressTracker.completedTest();
    }

    @Override
    public void onTestStarted() {

    }

    @Override
    public void onTestSuccessful() {
        poolProgressTracker.completedTest();
    }

    @Override
    public void onTestSkipped(@NotNull TestCaseRunResult skipResult) {
        poolProgressTracker.completedTest();
    }

    @Override
    public void onTestAssumptionFailure(@NotNull TestCaseRunResult skipResult) {
        poolProgressTracker.completedTest();
    }
}
