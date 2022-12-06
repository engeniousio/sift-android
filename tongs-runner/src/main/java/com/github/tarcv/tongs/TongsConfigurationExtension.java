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
package com.github.tarcv.tongs;

import com.github.tarcv.tongs.api.TongsConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tarcv.tongs.api.TongsConfiguration.TongsIntegrationTestRunType.NONE;

/**
 * Tongs extension.
 */
public abstract class TongsConfigurationExtension {

    /**
     * Output directory for Tongs report files. If empty, the default dir will be used.
     */
    public String baseOutputDir;

    /**
     * Ignore test failures flag.
     */
    public boolean ignoreFailures;

    /**
     * Enables code coverage.
     */
    public boolean isCoverageEnabled;

    /**
     * The title of the final report
     */
    public String title;

    /**
     * The subtitle of the final report
     */
    public String subtitle;

    /**
     * The package to consider when scanning for instrumentation tests to run.
     */
    public String testPackage;

    /**
     * Maximum time in milli-seconds between ADB output during a test. Prevents tests from getting stuck.
     */
    public int testOutputTimeout;

    /**
     * The collection of serials that should be excluded from this test run
     */
    public Collection<String> excludedSerials;

    /**
     * Indicate that screenshots are allowed when videos are not supported.
     */
    public boolean fallbackToScreenshots;

    /**
     * Amount of re-executions of failing tests allowed.
     */
    public int totalAllowedRetryQuota;

    /**
     * Max number of time each testCase is attempted again before declaring it as a failure.
     */
    public int retryPerTestCaseQuota;

    /**
     * Filter test run to tests without given annotation
     */
    public String excludedAnnotation;

    /**
     * Plugins to load
     */
    public List<String> plugins = new ArrayList<>();

    /**
     * Misc. configuration options
     */
    public Map<String, Object> configuration = new HashMap<>();

    /**
     * Specifies that Tongs should run using one of "under integration test" modes
     */
    public TongsConfiguration.TongsIntegrationTestRunType tongsIntegrationTestRunType = NONE;
}
