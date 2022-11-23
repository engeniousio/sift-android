/*
 * Copyright 2020 TarCV
 * Copyright 2016 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.runner.listeners;

import com.github.tarcv.tongs.device.DeviceTestFilesCleaner;
import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.api.devices.Pool;
import com.github.tarcv.tongs.api.run.TestCaseEvent;
import com.github.tarcv.tongs.api.result.TestCaseRunResult;
import com.github.tarcv.tongs.runner.TestRetryer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;

public class RetryListener extends TongsTestListener {
    private static final Logger logger = LoggerFactory.getLogger(RetryListener.class);
    private final Device device;
    private final TestCaseEvent currentTestCaseEvent;
    private final TestRetryer testRetryer;
    private final Pool pool;
    private final DeviceTestFilesCleaner deviceTestFilesCleaner;
    private final AtomicBoolean failedTest = new AtomicBoolean();

    public RetryListener(Pool pool,
                         Device device,
                         TestCaseEvent currentTestCaseEvent,
                         TestRetryer testRetryer,
                         DeviceTestFilesCleaner deviceTestFilesCleaner) {
        checkNotNull(device);
        checkNotNull(currentTestCaseEvent);
        checkNotNull(pool);
        this.testRetryer = testRetryer;
        this.device = device;
        this.currentTestCaseEvent = currentTestCaseEvent;
        this.pool = pool;
        this.deviceTestFilesCleaner = deviceTestFilesCleaner;
    }

    @Override
    public void onTestStarted() {

    }

    @Override
    public void onTestSuccessful() {

    }

    @Override
    public void onTestSkipped(@NotNull TestCaseRunResult skipResult) {

    }

    @Override
    public void onTestAssumptionFailure(@NotNull TestCaseRunResult skipResult) {

    }

    @Override
    public void onTestFailed(@NotNull TestCaseRunResult failureResult) {
        if (testRetryer.rescheduleTestExecution(currentTestCaseEvent.withFailureCount(failureResult.getTotalFailureCount()))) {
            logger.info("Test " + currentTestCaseEvent.toString() + " enqueued again into pool:" + pool.getName());
            removeFailureTraceFiles();
        } else {
            logger.info("Test " + currentTestCaseEvent.toString() + " failed on device " + device.getSafeSerial()
                    + " but retry is not allowed.");
        }
    }

    private void removeFailureTraceFiles() {
        boolean isDeleted = deviceTestFilesCleaner.deleteTraceFiles(currentTestCaseEvent);
        if (!isDeleted) {
            logger.warn("Failed to remove a trace filed for a failed but enqueued again test");
        }
    }
}
