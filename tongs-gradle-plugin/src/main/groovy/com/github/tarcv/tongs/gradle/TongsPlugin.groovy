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

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.InstallableVariant
import com.android.build.gradle.api.TestVariant
import com.github.tarcv.tongs.TongsConfigurationGradleExtension
import com.github.tarcv.tongs.api.TongsConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider

import static com.github.tarcv.tongs.api.TongsConfiguration.TongsIntegrationTestRunType.STUB_PARALLEL_TESTRUN

/**
 * Gradle plugin for Tongs.
 */
class TongsPlugin implements Plugin<Project> {

    /** Task name prefix. */
    private static final String TASK_PREFIX = "tongs"

    @Override
    void apply(final Project project) {

        if (!project.plugins.findPlugin(AppPlugin) && !project.plugins.findPlugin(LibraryPlugin)) {
            throw new IllegalStateException("Android plugin is not found")
        }

        project.extensions.add "tongs", TongsConfigurationGradleExtension

        project.configurations.all {
            resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                if (details.requested.version.isEmpty() &&
                        // TODO: get module name from root gradle.properties
                        "com.github.TarCV.testingteam-operator:tongs-ondevice".equalsIgnoreCase("${details.requested.group}:${details.requested.name}")
                ) {
                    details.useVersion BuildConfig.PLUGIN_VERSION
                    details.because "Default version provided by Tongs plugin"
                }
            }
        }

        def tongsTask = project.task(TASK_PREFIX) {
            group = JavaBasePlugin.VERIFICATION_GROUP
            description = "Runs all the instrumentation test variations on all the connected devices"
        }

        BaseExtension android = project.android
        android.testVariants.all { TestVariant variant ->
            TaskProvider<TongsRunTask> tongsTaskForTestVariant = registerTask(variant, project)
            tongsTask.dependsOn tongsTaskForTestVariant
        }
    }

    private static TaskProvider<TongsRunTask> registerTask(final TestVariant variant, final Project project) {
        return project.tasks.register("${TASK_PREFIX}${variant.name.capitalize()}", TongsRunTask) { task ->
            def testedVariant = (ApkVariant) variant.testedVariant
            task.configure {
                TongsConfigurationGradleExtension config = project.tongs

                description = "Runs instrumentation tests on all the connected devices for '${variant.name}' variation and generates a report with screenshots"
                group = JavaBasePlugin.VERIFICATION_GROUP

                title = config.title
                subtitle = config.subtitle
                applicationPackage = testedVariant.applicationId
                instrumentationPackage = variant.applicationId
                testPackage = config.testPackage
                testOutputTimeout = config.testOutputTimeout
                testRunnerClass = variant.mergedFlavor.testInstrumentationRunner
                testRunnerArguments = variant.mergedFlavor.testInstrumentationRunnerArguments
                plugins = config.plugins
                pluginsConfiguration = config.configuration
                excludedSerials = config.excludedSerials
                fallbackToScreenshots = config.fallbackToScreenshots
                totalAllowedRetryQuota = config.totalAllowedRetryQuota
                retryPerTestCaseQuota = config.retryPerTestCaseQuota
                isCoverageEnabled = config.isCoverageEnabled
                poolingStrategy = config.poolingStrategy
                ignoreFailures = config.ignoreFailures
                excludedAnnotation = config.excludedAnnotation
                tongsIntegrationTestRunType =
                        TongsConfiguration.TongsIntegrationTestRunType.valueOf(config.tongsIntegrationTestRunType)

                String baseOutputDir = config.baseOutputDir
                File outputBase
                if (baseOutputDir) {
                    outputBase = new File(baseOutputDir)
                } else {
                    outputBase = new File(project.buildDir, "reports/tongs")
                }
                output = new File(outputBase, variant.name)

                if (config.tongsIntegrationTestRunType != STUB_PARALLEL_TESTRUN) {
                    dependsOn(((InstallableVariant) testedVariant).installProvider, ((InstallableVariant) variant).installProvider)
                }
            }
            task.outputs.upToDateWhen { false }
        }
    }

    private static checkTestVariants(TestVariant testVariant) {
        if (testVariant.outputs.size() > 1) {
            throw new UnsupportedOperationException("The Tongs plugin does not support abi/density splits for test APKs")
        }
    }
}
