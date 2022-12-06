/*
 * Copyright 2021 TarCV
 * Copyright 2018 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.summary;

import com.github.tarcv.tongs.api.TongsConfiguration;
import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.api.devices.Pool;
import com.github.tarcv.tongs.api.result.StackTrace;
import com.github.tarcv.tongs.api.result.TestCaseRunResult;
import com.github.tarcv.tongs.api.run.ResultStatus;
import com.github.tarcv.tongs.api.run.TestCaseEvent;
import com.github.tarcv.tongs.api.testcases.TestCase;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tarcv.tongs.api.devices.Pool.Builder.aDevicePool;
import static com.github.tarcv.tongs.api.run.TestCaseEventExtKt.aTestCaseEvent;
import static com.github.tarcv.tongs.api.run.TestCaseEventExtKt.aTestEvent;
import static com.github.tarcv.tongs.api.run.TestCaseRunResultExtKt.aTestResult;
import static com.github.tarcv.tongs.api.testcases.TestCaseExtKt.aTestCase;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class SummaryCompilerTest {
    @Rule
    public JUnitRuleMockery mockery = new JUnitRuleMockery();
    @Mock
    private TongsConfiguration mockConfiguration;

    private SummaryCompiler summaryCompiler;

    private final Pool devicePool = aDevicePool()
            .addDevice(Device.TEST_DEVICE)
            .build();
    private final Collection<Pool> devicePools = newArrayList(
            devicePool
    );

    private final TestCaseRunResult firstCompletedTest = aTestResult(
            aTestCase("CompletedClassTest", "doesJobProperly"),
            ResultStatus.PASS,
            emptyList(),
            devicePool
    );
    private final TestCaseRunResult secondCompletedTest = aTestResult(
            aTestCase("CompletedClassTest2","doesJobProperly"),
            ResultStatus.PASS,
            emptyList(),
            devicePool
    );

    private final List<TestCaseRunResult> testResults = newArrayList(
            firstCompletedTest,
            secondCompletedTest,
            aTestResult(aTestCase("FailedClassTest", "doesJobProperly"),
                    ResultStatus.FAIL,
                    singletonList(new StackTrace("", "a failure stacktrace", "a failure stacktrace")),
                    devicePool, 9),
            aTestResult(aTestCase("IgnoredClassTest", "doesJobProperly"), ResultStatus.IGNORED, emptyList(),
                    devicePool)
    );

    private final Map<Pool, Collection<TestCaseEvent>> testCaseEvents = ImmutableMap.<Pool, Collection<TestCaseEvent>>builder()
            .put(devicePool, newArrayList(
                aTestCaseEvent(aTestCase( "CompletedClassTest", "doesJobProperly")),
                aTestCaseEvent(aTestCase( "CompletedClassTest2", "doesJobProperly")),
                aTestEvent(aTestCase("FailedClassTest", "doesJobProperly"),
                        emptyList(), 10),
                aTestCaseEvent(aTestCase("IgnoredClassTest", "doesJobProperly")),
                aTestCaseEvent(aTestCase("SkippedClassTest", "doesJobProperly"))
            )).build();

    @Before
    public void setUp() {
        summaryCompiler = new SummaryCompiler(mockConfiguration);
        mockery.checking(new Expectations() {{
            allowing(mockConfiguration);
        }});
    }

    @Test
    public void compilesSummaryWithCompletedTests() {
        Summary summary = summaryCompiler.compileSummary(devicePools, testCaseEvents, testResults);

        assertThat(summary.getPoolSummaries().get(0).getTestResults(), hasItems(
                firstCompletedTest, secondCompletedTest));
    }

    @Test
    public void compilesSummaryWithIgnoredTests() {
        Summary summary = summaryCompiler.compileSummary(devicePools, testCaseEvents, testResults);

        assertThat(summary.getIgnoredTests(), hasSize(1));
        assertThat(mapToStringList(summary.getIgnoredTests()), contains("com.example.IgnoredClassTest#doesJobProperly"));
    }

    @Test
    public void compilesSummaryWithFailedTests() {
        Summary summary = summaryCompiler.compileSummary(devicePools, testCaseEvents, testResults);

        assertThat(summary.getFailedTests(), hasSize(1));
        assertThat(summary.getFailedTests().stream()
                        .map(r -> String.format("%d times %s", r.getTotalFailureCount(), r.getTestCase().toString()))
                        .collect(Collectors.toList()),
                contains("10 times com.example.FailedClassTest#doesJobProperly"));
    }

    @Test
    public void onlyFinalAttemptIsIncluded() {
        List<TestCaseRunResult> testResultsWithAttempts = new ArrayList<>(testResults);
        TestCase testCase = aTestCase("FailedClassTest", "doesJobProperly");
        TestCaseRunResult finalAttemptResult = aTestResult(
                testCase,
                ResultStatus.FAIL,
                singletonList(new StackTrace("", "final attempt", "a failure stacktrace")),
                devicePool, 2
        );
        testResultsWithAttempts.add(finalAttemptResult);
        Assert.assertEquals(2,
                testResultsWithAttempts.stream()
                .filter(result -> result.getTestCase().equals(testCase))
                .count());

        Summary summary = summaryCompiler.compileSummary(devicePools, testCaseEvents, testResultsWithAttempts);
        List<TestCaseRunResult> poolResults = summary.getPoolSummaries().get(0).getTestResults().stream()
                .filter(result -> result.getTestCase().equals(testCase))
                .collect(Collectors.toList());
        Assert.assertEquals(1, poolResults.size());
        Assert.assertEquals(finalAttemptResult, poolResults.get(0));

        List<TestCaseRunResult> runResults = summary.getFailedTests().stream()
                .filter(result -> result.getTestCase().equals(testCase))
                .collect(Collectors.toList());
        Assert.assertEquals(1, runResults.size());
        Assert.assertEquals(finalAttemptResult, runResults.get(0));
    }

    @Test
    public void compilesSummaryWithFlakyTests() {
        List<TestCaseRunResult> testResultsWithAttempts = new ArrayList<>(testResults);
        TestCase testCase = aTestCase("FailedClassTest", "doesJobProperly");
        TestCaseRunResult finalAttemptResult = aTestResult(
                testCase,
                ResultStatus.PASS,
                singletonList(new StackTrace("", "final attempt", "a failure stacktrace")),
                devicePool, 2
        );
        testResultsWithAttempts.add(finalAttemptResult);
        Assert.assertEquals(2,
                testResultsWithAttempts.stream()
                .filter(result -> result.getTestCase().equals(testCase))
                .count());

        Summary summary = summaryCompiler.compileSummary(devicePools, testCaseEvents, testResultsWithAttempts);

        List<TestCaseRunResult> runResults = summary.getFlakyTests().stream()
                .filter(result -> result.getTestCase().equals(testCase))
                .collect(Collectors.toList());
        Assert.assertEquals(1, runResults.size());
        Assert.assertEquals(finalAttemptResult, runResults.get(0));
    }

    @Test
    public void compilesSummaryWithFatalCrashedTestsIfTheyAreNotFoundInPassedOrFailedOrIgnored() {
        Summary summary = summaryCompiler.compileSummary(devicePools, testCaseEvents, testResults);

        assertThat(summary.getFatalCrashedTests(), hasSize(1));
        assertThat(mapToStringList(summary.getFatalCrashedTests()),
                contains("com.example.SkippedClassTest#doesJobProperly"));
    }

    @NotNull
    private static List<String> mapToStringList(List<TestCaseRunResult> resultList) {
        return resultList.stream()
                .map(testCaseRunResult -> testCaseRunResult.getTestCase().toString())
                .collect(Collectors.toList());
    }
}