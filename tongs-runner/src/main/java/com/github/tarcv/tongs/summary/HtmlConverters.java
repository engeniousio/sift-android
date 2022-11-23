/*
 * Copyright 2021 TarCV
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

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

class HtmlConverters {
	private HtmlConverters() {}

	public static HtmlSummary toHtmlSummary(Summary summary) {
		Collection<HtmlPoolSummary> poolSummaries = summary.getPoolSummaries().stream()
				.map(toHtmlPoolSummary())
				.collect(Collectors.toList());
		return new HtmlSummary(
				poolSummaries,
				summary.getTitle(),
				summary.getSubtitle(),
				summary.getIgnoredTests(),
				new OutcomeAggregator().aggregate(summary) ? "pass" : "fail",
				summary.getFlakyTests(),
				summary.getFailedTests(),
        		summary.getFatalCrashedTests(),
        		summary.getFatalErrors() // TODO: Add to template
		);
	}

	private static Function<PoolSummary, HtmlPoolSummary> toHtmlPoolSummary(
	) {
		return new Function<PoolSummary, HtmlPoolSummary> () {
			@Override
			@Nullable
			public HtmlPoolSummary apply(PoolSummary poolSummary) {
				return new HtmlPoolSummary(
						getPoolStatus(poolSummary),
						poolSummary.getTestResults(),
						poolSummary.getPoolName()
				);
			}

            private String getPoolStatus(PoolSummary poolSummary) {
                Boolean success = OutcomeAggregator.toPoolOutcome().apply(poolSummary);
                return (success != null && success? "pass" : "fail");
            }
        };
	}
}
