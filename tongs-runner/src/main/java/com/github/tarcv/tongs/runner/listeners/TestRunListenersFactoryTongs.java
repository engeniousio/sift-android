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

import com.github.tarcv.tongs.Configuration;
import com.github.tarcv.tongs.api.TongsConfiguration;
import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.api.devices.Pool;
import com.github.tarcv.tongs.api.run.TestCaseEvent;
import com.github.tarcv.tongs.api.testcases.TestCase;
import com.github.tarcv.tongs.device.DeviceTestFilesCleanerImpl;
import com.github.tarcv.tongs.model.TestCaseEventQueue;
import com.github.tarcv.tongs.runner.ProgressReporter;
import com.github.tarcv.tongs.runner.TestRetryerImpl;
import com.github.tarcv.tongs.system.io.FileManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

public class TestRunListenersFactoryTongs {

    private final Configuration configuration;
    private final FileManager fileManager;

    public TestRunListenersFactoryTongs(Configuration configuration,
                                        FileManager fileManager) {
        this.configuration = configuration;
        this.fileManager = fileManager;
    }

    public List<TongsTestListener> createTongsListners(TestCaseEvent testCase,
                                                       Device device,
                                                       Pool pool,
                                                       ProgressReporter progressReporter,
                                                       TestCaseEventQueue testCaseEventQueue,
                                                       TongsConfiguration.TongsIntegrationTestRunType tongsIntegrationTestRunType) {
        TestCase testIdentifier = testCase.getTestCase();
        final List<TongsTestListener> normalListeners = asList(
                new ProgressTestRunListener(pool, progressReporter),
                new ConsoleLoggingTestRunListener(configuration.getTestPackage(), testIdentifier, device.getSerial(),
                        device.getModelName(), progressReporter),
                new SlowWarningTestRunListener(testIdentifier),
                buildRetryListener(testCase, device, pool, progressReporter, testCaseEventQueue)
        );
        if (tongsIntegrationTestRunType == TongsConfiguration.TongsIntegrationTestRunType.RECORD_LISTENER_EVENTS) {
            ArrayList<TongsTestListener> testListeners = new ArrayList<>(normalListeners);
            testListeners.add(new RecordingTestRunListener(device, testIdentifier.toString(), false));
            return Collections.unmodifiableList(testListeners);
        } else {
            return normalListeners;
        }
    }

    private TongsTestListener buildRetryListener(TestCaseEvent testCase,
                                                 Device device,
                                                 Pool pool,
                                                 ProgressReporter progressReporter,
                                                 TestCaseEventQueue testCaseEventQueue) {
        TestRetryerImpl testRetryer = new TestRetryerImpl(progressReporter, pool, testCaseEventQueue);
        DeviceTestFilesCleanerImpl deviceTestFilesCleaner = new DeviceTestFilesCleanerImpl(fileManager, pool, device);
        return new RetryListener(pool, device, testCase, testRetryer, deviceTestFilesCleaner);
    }
}
