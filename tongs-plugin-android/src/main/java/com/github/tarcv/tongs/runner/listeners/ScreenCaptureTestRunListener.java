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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.github.tarcv.tongs.api.result.TestCaseFile;
import com.github.tarcv.tongs.api.result.TestCaseFileManager;
import com.github.tarcv.tongs.model.AndroidDevice;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

class ScreenCaptureTestRunListener extends BaseCaptureTestRunListener {
    private final TestCaseFileManager fileManager;
    private final IDevice deviceInterface;

    private ScreenCapturer screenCapturer;

    public ScreenCaptureTestRunListener(TestCaseFileManager fileManager, AndroidDevice device) {
        this.fileManager = fileManager;
        this.deviceInterface = device.getDeviceInterface();
    }

    @Override
    public void onRunStarted() {
        screenCapturer = new ScreenCapturer(deviceInterface, fileManager);
        new Thread(screenCapturer, "ScreenCapturer").start();
    }

    @Override
    public void onRunFinished() {
        screenCapturer.stopCapturing(isHasFailed());
    }

    @NotNull
    public TestCaseFile getFile() {
        return screenCapturer.getFile();
    }

    @Override
    public void addTestMetrics(@NotNull TestIdentifier testIdentifier, @NotNull Map<String, String> testMetrics, boolean hasStarted) {
        // nothing to do when metrics are added
    }

    @Override
    public void addRunData(@NotNull String runOutput, @NotNull Map<String, String> runMetrics) {
        // nothing to do when run data is added
    }
}
