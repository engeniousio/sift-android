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
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OutcomeAggregator {
    private static final Logger logger = LoggerFactory.getLogger(OutcomeAggregator.class);

    public boolean aggregate(Summary summary) {
        if (summary == null || summary.getPoolSummaries().isEmpty() || !summary.getFatalCrashedTests().isEmpty()) {
            if (summary != null && logger.isErrorEnabled() && !summary.getFatalCrashedTests().isEmpty()) {
                logger.error(String.format("There are tests left unprocessed: %s", summary.getFatalCrashedTests()));
            }
            return false;
        }

        List<PoolSummary> poolSummaries = summary.getPoolSummaries();
        Collection<Boolean> poolOutcomes = poolSummaries.stream()
                .map(toPoolOutcome())
                .collect(Collectors.toList());
        return and(poolOutcomes);
    }

    public static Function<PoolSummary, Boolean> toPoolOutcome() {
        return (input -> {
            final Collection<TestCaseRunResult> testResults = input.getTestResults();
            final Collection<Boolean> testOutcomes = testResults.stream()
                    .map(toTestOutcome())
                    .collect(Collectors.toList());

            return !testOutcomes.isEmpty() && and(testOutcomes);
        });
    }

    private static Function<TestCaseRunResult, Boolean> toTestOutcome() {
        return (input -> !ResultStatus.isFailure(input.getStatus()));
    }

    private static Boolean and(final Collection<Boolean> booleans) {
        return BooleanUtils.and(booleans.toArray(new Boolean[0]));
    }
}
