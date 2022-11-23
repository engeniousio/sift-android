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
package com.github.tarcv.tongs.gradle

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.PoolingStrategy
import com.github.tarcv.tongs.Tongs
import com.github.tarcv.tongs.api.TongsConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.Paths

import static com.github.tarcv.tongs.Configuration.Builder.configuration

/**
 * Task for using Tongs.
 */
class TongsRunTask extends DefaultTask implements VerificationTask {

    /** Logger. */
    private static final Logger LOG = LoggerFactory.getLogger(TongsRunTask.class)

    /** If true then test failures do not cause a build failure. */
    boolean ignoreFailures

    /** Output directory. */
    @OutputDirectory
    File output

    String applicationPackage

    String instrumentationPackage

    String title

    String subtitle

    String testPackage

    String testRunnerClass

    Map<String, String> testRunnerArguments
    List<String> plugins

    Map<String, Object> pluginsConfiguration

    boolean isCoverageEnabled

    int testOutputTimeout

    Collection<String> excludedSerials

    boolean fallbackToScreenshots

    int totalAllowedRetryQuota

    int retryPerTestCaseQuota

    PoolingStrategy poolingStrategy

    String excludedAnnotation

    TongsConfiguration.TongsIntegrationTestRunType tongsIntegrationTestRunType

    @TaskAction
    void runTongs() {
        LOG.debug("Output: $output")
        LOG.debug("Ignore failures: $ignoreFailures")

        Configuration configuration = configuration()
                .withAndroidSdk(project.android.sdkDirectory)
                .withApplicationApk(null)
                .withApplicationPackage(applicationPackage)
                .withInstrumentationApk(null)
                .withInstrumentationPackage(instrumentationPackage)
                .withOutput(output)
                .withTitle(title)
                .withSubtitle(subtitle)
                .withTestPackage(testPackage)
                .withTestRunnerClass(testRunnerClass)
                .withTestRunnerArguments(testRunnerArguments)
                .withPlugins(plugins)
                .withPluginConfiguration(pluginsConfiguration)
                .withTestOutputTimeout(testOutputTimeout)
                .withExcludedSerials(excludedSerials)
                .withFallbackToScreenshots(fallbackToScreenshots)
                .withTotalAllowedRetryQuota(totalAllowedRetryQuota)
                .withRetryPerTestCaseQuota(retryPerTestCaseQuota)
                .withCoverageEnabled(isCoverageEnabled)
                .withPoolingStrategy(poolingStrategy)
                .withExcludedAnnotation(excludedAnnotation)
                .withTongsIntegrationTestRunType(tongsIntegrationTestRunType)
                .withDdmTermination(false) // AGP doesn't terminate DdmLib, neither should Tongs
                .build(true);

        boolean success = new Tongs(configuration).run()
        if (!success && !ignoreFailures) {
            throw new GradleException("Tests failed! See ${Paths.get(output.absolutePath, 'html', 'index.html')}")
        }
    }
}
