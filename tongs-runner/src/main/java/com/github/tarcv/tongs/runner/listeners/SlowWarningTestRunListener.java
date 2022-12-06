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

import com.github.tarcv.tongs.Utils;
import com.github.tarcv.tongs.api.result.TestCaseRunResult;
import com.github.tarcv.tongs.api.testcases.TestCase;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.System.nanoTime;

class SlowWarningTestRunListener extends TongsTestListener {
    private static final Logger logger = LoggerFactory.getLogger(SlowWarningTestRunListener.class);
    private static final long TEST_LENGTH_THRESHOLD_MILLIS = 30 * 1000L;
    private long startTime;
    private final TestCase test;

    public SlowWarningTestRunListener(TestCase test) {
        this.test = test;
    }

    @Override
    public void onTestStarted() {
        startTime = nanoTime();
    }

    public void onTestEnded() {
        long testDuration = Utils.millisSinceNanoTime(startTime);
        if (testDuration > TEST_LENGTH_THRESHOLD_MILLIS) {
            logger.warn("Slow test ({}ms): {} {}" , testDuration, test.getTestClass(), test.getTestMethod());

        }
    }

    @Override
    public void onTestSuccessful() {
        onTestEnded();
    }

    @Override
    public void onTestFailed(@NotNull TestCaseRunResult failureResult) {
        onTestEnded();
    }

    @Override
    public void onTestSkipped(@NotNull TestCaseRunResult skipResult) {
        onTestEnded();
    }

    @Override
    public void onTestAssumptionFailure(@NotNull TestCaseRunResult skipResult) {
        onTestEnded();
    }
}
