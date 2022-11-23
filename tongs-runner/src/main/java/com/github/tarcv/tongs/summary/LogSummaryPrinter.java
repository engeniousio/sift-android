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
package com.github.tarcv.tongs.summary;

import com.github.tarcv.tongs.api.result.TestCaseRunResult;
import com.github.tarcv.tongs.api.run.ResultStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.tarcv.tongs.api.run.ResultStatus.*;

public class LogSummaryPrinter implements SummaryPrinter {

    private static final Logger logger = LoggerFactory.getLogger(LogSummaryPrinter.class);

    @Override
    public void print(Summary summary) {
        if (!logger.isInfoEnabled()) {
            return;
        }

        for (ResultStatus resultStatus : new ResultStatus[]{FAIL, ERROR}) {
            for (PoolSummary poolSummary : summary.getPoolSummaries()) {
                StringBuilder out = getPoolSummary(poolSummary, resultStatus);
                if (out.length() != 0 && logger.isInfoEnabled()) {
                    logger.info(out.toString());
                }
            }
        }
        for (PoolSummary poolSummary : summary.getPoolSummaries()) {
            printMiniSummary(poolSummary);
        }

        printSuppressedTestsList(summary);
    }

    private static void printSuppressedTestsList(Summary summary) {
        if (logger.isInfoEnabled()) {
            List<TestCaseRunResult> suppressedTests = summary.getIgnoredTests();
            if (suppressedTests.isEmpty()) {
                logger.info("No suppressed tests.");
            } else {
                logger.info("Suppressed tests:");
                for (TestCaseRunResult s : suppressedTests) {
                    logger.info(s.getTestCase().toString());
                }
            }
        }
    }

    private static void printMiniSummary(PoolSummary poolSummary) {
        if (logger.isInfoEnabled()) {
            logger.info(String.format("% 3d E  % 3d F  % 3d P: %s",
                    getResultsWithStatus(poolSummary.getTestResults(), ERROR).size(),
                    getResultsWithStatus(poolSummary.getTestResults(), FAIL).size(),
                    getResultsWithStatus(poolSummary.getTestResults(), PASS).size(),
                    poolSummary.getPoolName()
            ));
        }
    }

    private static StringBuilder getPoolSummary(PoolSummary poolSummary, ResultStatus resultStatus) {
        StringBuilder summary = printTestsWithStatus(poolSummary, resultStatus);
        if (summary.length() > 0) {
            final String poolName = poolSummary.getPoolName();
            summary.insert(0, String.format("%s Results for device pool: %s%n", resultStatus, poolName));
            summary.insert(0, "____________________________________________________________________________________\n");
        }
        return summary;
    }

    private static StringBuilder printTestsWithStatus(PoolSummary poolSummary,
                                                      ResultStatus status) {
        StringBuilder summary = new StringBuilder();
        final Collection<TestCaseRunResult> resultsWithStatus = getResultsWithStatus(
                poolSummary.getTestResults(), status);
        if (!resultsWithStatus.isEmpty()) {
            for (TestCaseRunResult testResult : resultsWithStatus) {
                summary.append(String.format("%s %s#%s on %s %s%n",
                        testResult.getStatus(),
                        testResult.getTestCase().getTestClass(),
                        testResult.getTestCase().getTestMethod(),
                        testResult.getDevice().getManufacturer(),
                        testResult.getDevice().getModelName()));
            }
        }
        return summary;
    }

    private static Collection<TestCaseRunResult> getResultsWithStatus(Collection<TestCaseRunResult> testResults,
                                                                      final ResultStatus resultStatus) {
        return testResults.stream()
                .filter(testResult -> testResult.getStatus().equals(resultStatus))
                .collect(Collectors.toList());
    }
}
