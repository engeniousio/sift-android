/*
 * Copyright 2020 TarCV
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
package com.github.tarcv.tongs.runner.listeners;

import com.github.tarcv.tongs.api.result.StackTrace;
import com.github.tarcv.tongs.api.result.TestCaseRunResult;
import com.github.tarcv.tongs.api.testcases.TestCase;
import com.github.tarcv.tongs.runner.ProgressReporter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Collectors;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
class ConsoleLoggingTestRunListener extends TongsTestListener {
    private static final PrintStream consolePrinter = System.out;
    private static final Logger logger = LoggerFactory.getLogger(ConsoleLoggingTestRunListener.class);
    private static final String PERCENT = "%02d%%";
    private final SimpleDateFormat testTimeFormat = new SimpleDateFormat("mm.ss", Locale.ROOT); // SDF cannot be static
    private final String serial;
    private final String modelName;
    private final ProgressReporter progressReporter;
    private final String testPackage;
    private final TestCase test;

    ConsoleLoggingTestRunListener(String testPackage,
                                  TestCase startedTest, String serial,
                                  String modelName,
                                  ProgressReporter progressReporter) {
        this.test = startedTest;
        this.serial = serial;
        this.modelName = modelName;
        this.progressReporter = progressReporter;
        this.testPackage = testPackage;
    }

    @Override
    public void onTestStarted() {
        consolePrinter.printf("%s %s %s %s [%s] %s%n", runningTime(), progress(), failures(), modelName,
                serial, testCase(test));
    }

    @Override
    public void onTestFailed(@NotNull TestCaseRunResult failureResult) {
        consolePrinter.printf("%s %s %s %s [%s] Failed %s%n %s%n", runningTime(), progress(), failures(), modelName,
                serial, testCase(test), joinStackTraces(failureResult));
    }

    @Override
    public void onTestAssumptionFailure(@NotNull TestCaseRunResult skipped) {
        if (logger.isDebugEnabled()) {
            logger.debug("test={}", testCase(test));
            logger.debug("assumption failure {}", joinStackTraces(skipped));
        }
    }

    @Override
    public void onTestSkipped(@NotNull TestCaseRunResult skipped) {
        if (logger.isDebugEnabled()) {
            logger.debug("ignored test {} {}", testCase(test), joinStackTraces(skipped));
        }
    }


    @Override
    public void onTestSuccessful() {
        // nothing to report when a test is successful
    }

    private String runningTime() {
        return testTimeFormat.format(new Date(progressReporter.millisSinceTestsStarted()));
    }

    private String testCase(TestCase test) {
        return String.valueOf(test).replaceAll(testPackage, "");
    }

    private String progress() {
        int progress = (int) (progressReporter.getProgress() * 100.0);
        return String.format(PERCENT, progress);
    }

    private int failures() {
        return progressReporter.getFailures();
    }

    @NotNull
    private static String joinStackTraces(TestCaseRunResult failureResult) {
        return failureResult.getStackTraces().stream()
                .map(StackTrace::getFullTrace)
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    }
}
