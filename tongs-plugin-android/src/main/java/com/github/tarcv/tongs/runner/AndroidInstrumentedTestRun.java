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
package com.github.tarcv.tongs.runner;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.github.tarcv.tongs.api.result.TestCaseRunResult;
import com.github.tarcv.tongs.api.run.TestCaseEvent;
import com.github.tarcv.tongs.api.testcases.TestCase;
import com.github.tarcv.tongs.runner.listeners.IResultProducer;
import com.github.tarcv.tongs.runner.listeners.RunListenerAdapter;
import com.github.tarcv.tongs.system.io.RemoteFileManager;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

public class AndroidInstrumentedTestRun {
	private static final Logger logger = LoggerFactory.getLogger(AndroidInstrumentedTestRun.class);
	private static final String TESTCASE_FILTER = "com.github.tarcv.tongs.ondevice.ClassMethodFilter";
	public static final String COLLECTING_RUN_FILTER = "com.github.tarcv.tongs.ondevice.AnnontationReadingFilter";
	private static final String FILTER_ARGUMENT = "filter";
	private final String poolName;
	private final TestRunParameters testRunParameters;
	private final List<? extends ITestRunListener> testRunListeners;
	private final IRemoteAndroidTestRunnerFactory remoteAndroidTestRunnerFactory;
	private final IResultProducer resultProducer;

	public AndroidInstrumentedTestRun(String poolName,
                                      TestRunParameters testRunParameters,
                                      List<? extends ITestRunListener> testRunListeners,
                                      IResultProducer resultProducer,
                                      IRemoteAndroidTestRunnerFactory remoteAndroidTestRunnerFactory) {
        this.poolName = poolName;
		this.testRunParameters = testRunParameters;
		this.testRunListeners = testRunListeners;
		this.resultProducer = resultProducer;
		this.remoteAndroidTestRunnerFactory = remoteAndroidTestRunnerFactory;
	}

	public TestCaseRunResult execute() {
		final String testPackage = testRunParameters.getTestPackage();
		final IDevice device = testRunParameters.getDeviceInterface();

		final RemoteAndroidTestRunner runner =
				remoteAndroidTestRunnerFactory.createRemoteAndroidTestRunner(testPackage, testRunParameters.getTestRunner(), device);

		runner.setRunName(poolName);
		runner.setMaxtimeToOutputResponse(testRunParameters.getTestOutputTimeout());

		// Custom filter is required to support Parameterized tests with default names
		final TestCaseEvent test = testRunParameters.getTest();
		final String testClassName;
		final String testMethodName;
		final String specialFilter;
		if (test != null) {
			final TestCase testCase = test.getTestCase();
			testClassName = test.getTestClass();
			testMethodName = test.getTestMethod();
			specialFilter = TESTCASE_FILTER;

			if (testRunParameters.isWithOnDeviceLibrary()) {
				String encodedClassName = remoteAndroidTestRunnerFactory.encodeTestName(testClassName);
				String encodedMethodName = remoteAndroidTestRunnerFactory.encodeTestName(testMethodName);

				remoteAndroidTestRunnerFactory.properlyAddInstrumentationArg(runner, "tongs_filterClass", encodedClassName);
				remoteAndroidTestRunnerFactory.properlyAddInstrumentationArg(runner, "tongs_filterMethod", encodedMethodName);
			} else {
				remoteAndroidTestRunnerFactory.properlyAddInstrumentationArg(runner, "class",
						testClassName + "#" + testMethodName);
			}

			if (testRunParameters.isCoverageEnabled()) {
				runner.setCoverage(true);
				runner.addInstrumentationArg("coverageFile", RemoteFileManager.getCoverageFileName(testCase));
			}
		} else {
			testClassName = "Test case collection";
			testMethodName = "";
			specialFilter = COLLECTING_RUN_FILTER;

			runner.addBooleanArg("log", true);
		}

		addFilterAndCustomArgs(
				runner,
				testRunParameters.isWithOnDeviceLibrary() ? specialFilter : null);

		String excludedAnnotation = testRunParameters.getExcludedAnnotation();
		if (!Strings.isNullOrEmpty(excludedAnnotation)) {
			logger.info("Tests annotated with {} will be excluded", excludedAnnotation);
			runner.addInstrumentationArg("notAnnotation", excludedAnnotation);
		} else {
			logger.info("No excluding any test based on annotations");
		}

		try {
			for (ITestRunListener testRunListener : testRunListeners) { // TODO: refactor this
				if (testRunListener instanceof RunListenerAdapter) {
					((RunListenerAdapter) testRunListener).onBeforeTestRunStarted();
				}
			}

			logger.info("Cmd: " + runner.getAmInstrumentCommand());
			runner.run(testRunListeners.toArray(new ITestRunListener[0]));
		} catch (ShellCommandUnresponsiveException | TimeoutException e) {
			logger.warn("Test: " + testClassName + " got stuck. You can increase the timeout in settings if it's too strict");
		} catch (AdbCommandRejectedException | IOException e) {
			throw new RuntimeException(format("Error while running test %s %s", testClassName, testMethodName), e);
		} finally {
			for (ITestRunListener testRunListener : testRunListeners) { // TODO: refactor this
				if (testRunListener instanceof RunListenerAdapter) {
					((RunListenerAdapter) testRunListener).onAfterTestRunEnded();
				}
			}
		}

        return resultProducer.getResult();
    }

	private void addFilterAndCustomArgs(RemoteAndroidTestRunner runner, @Nullable String collectingRunFilter) {
		testRunParameters.getTestRunnerArguments().entrySet().stream()
				.filter(nameValue -> !FILTER_ARGUMENT.equals(nameValue.getKey()))
				.filter(nameValue -> !nameValue.getKey().startsWith("tongs_"))
				.forEach(nameValue -> remoteAndroidTestRunnerFactory.properlyAddInstrumentationArg(
						runner,
						nameValue.getKey(),
						nameValue.getValue()
				));

		@Nullable String customFilters = testRunParameters.getTestRunnerArguments().get(FILTER_ARGUMENT);
		String filters = Stream.of(customFilters, collectingRunFilter)
				.filter(Objects::nonNull)
				.collect(Collectors.joining(","));
		if (!filters.isEmpty()) {
			runner.addInstrumentationArg(FILTER_ARGUMENT, filters);
		}
	}
}
