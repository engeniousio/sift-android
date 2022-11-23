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

package com.github.tarcv.tongs.runner;

import com.github.tarcv.tongs.TongsRunner;
import com.github.tarcv.tongs.api.result.TestCaseRunResult;
import com.github.tarcv.tongs.injector.RuleManagerFactory;
import com.github.tarcv.tongs.model.TestCaseEventQueue;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class PoolTestRunnerFactory {
    private final DeviceTestRunnerFactory deviceTestRunnerFactory;
    private final RuleManagerFactory ruleManagerFactory;

    public PoolTestRunnerFactory(DeviceTestRunnerFactory deviceTestRunnerFactory,
                                 RuleManagerFactory ruleManagerFactory) {
        this.deviceTestRunnerFactory = deviceTestRunnerFactory;
        this.ruleManagerFactory = ruleManagerFactory;
    }

    public Runnable createPoolTestRunner(TongsRunner.PoolTask poolTask,
                                         List<TestCaseRunResult> testCaseResults, CountDownLatch poolCountDownLatch,
                                         ProgressReporter progressReporter) {

        int totalTests = poolTask.getTestCases().size();
        progressReporter.addPoolProgress(poolTask.getPool(), new PoolProgressTrackerImpl(totalTests));

        return new PoolTestRunner(
                deviceTestRunnerFactory,
                poolTask,
                new TestCaseEventQueue(poolTask.getTestCases(), testCaseResults),
                poolCountDownLatch,
                progressReporter,
                ruleManagerFactory);
    }
}
