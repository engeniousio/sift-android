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

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.github.tarcv.tongs.api.TongsConfiguration;
import com.github.tarcv.tongs.api.devices.Pool;
import com.github.tarcv.tongs.api.run.TestCaseEvent;
import com.github.tarcv.tongs.injector.runner.RemoteAndroidTestRunnerFactoryInjector;
import com.github.tarcv.tongs.model.AndroidDevice;
import com.github.tarcv.tongs.runner.listeners.IResultProducer;
import com.github.tarcv.tongs.runner.listeners.ResultProducer;
import com.github.tarcv.tongs.runner.listeners.RunListener;
import com.github.tarcv.tongs.runner.listeners.RunListenerAdapter;
import com.github.tarcv.tongs.runner.listeners.TestCollectorResultProducer;
import com.github.tarcv.tongs.suite.TestCollectingListener;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AndroidTestRunFactory {

    private static final Logger logger = LoggerFactory.getLogger(AndroidTestRunFactory.class);
    private final TongsConfiguration configuration;

    public AndroidTestRunFactory(TongsConfiguration configuration) {
        this.configuration = configuration;
    }

    public AndroidInstrumentedTestRun createTestRun(AndroidRunContext testRunContext, TestCaseEvent testCase,
                                                    AndroidDevice device,
                                                    Pool pool) {
        TestRunParameters testRunParameters = createTestParameters(testCase,
                device,
                configuration,
                device.hasOnDeviceLibrary());

        IResultProducer resultProducer = createResultProducer(testRunContext);
        List<RunListener> testRunListeners = new ArrayList<>(resultProducer.requestListeners());

        return new AndroidInstrumentedTestRun(
                pool.getName(),
                testRunParameters,
                Collections.singletonList(
                        new RunListenerAdapter(testCase.toString(),
                                new TestIdentifier(testCase.getTestClass(), testCase.getTestMethod()),
                                testRunListeners)
                ),
                resultProducer,
                RemoteAndroidTestRunnerFactoryInjector.remoteAndroidTestRunnerFactory(configuration)
        );
    }

    @NotNull
    protected IResultProducer createResultProducer(AndroidRunContext testRunContext) {
        return new ResultProducer(testRunContext);
    }

    public AndroidInstrumentedTestRun createCollectingRun(AndroidDevice device,
                                                          Pool pool,
                                                          TestCollectingListener testCollectingListener,
                                                          boolean withOnDeviceLib) {
        TestRunParameters testRunParameters = createTestParameters(null,
                device,
                configuration,
                withOnDeviceLib);
        logger.info("LIST createCollectingRun testRunParameters {}", testRunParameters);
        logger.info("LIST createCollectingRun testCase {}", testRunParameters.getTest());
        List<ITestRunListener> testRunListeners = new ArrayList<>();
        testRunListeners.add(testCollectingListener);

        return new AndroidInstrumentedTestRun(
                pool.getName(),
                testRunParameters,
                testRunListeners,
                new TestCollectorResultProducer(pool, device),
                RemoteAndroidTestRunnerFactoryInjector.remoteAndroidTestRunnerFactory(configuration)
        );
    }

    private static TestRunParameters createTestParameters(TestCaseEvent testCase, AndroidDevice device, TongsConfiguration configuration, boolean withOnDeviceLib) {
        return TestRunParameters.Builder.testRunParameters()
                .withDeviceInterface(device.getDeviceInterface())
                .withTest(testCase)
                .withTestPackage(configuration.getInstrumentationPackage())
                .withApplicationPackage(configuration.getApplicationPackage())
                .withTestRunner(configuration.getTestRunnerClass())
                .withTestRunnerArguments(configuration.getTestRunnerArguments())
                .withTestOutputTimeout((int) configuration.getTestOutputTimeout())
                .withOnDeviceLibrary(withOnDeviceLib)
                .withCoverageEnabled(configuration.isCoverageEnabled())
                .withExcludedAnnotation(configuration.getExcludedAnnotation())
                .build();
    }

}
