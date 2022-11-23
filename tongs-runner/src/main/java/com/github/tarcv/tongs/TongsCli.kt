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
package com.github.tarcv.tongs

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.github.tarcv.tongs.injector.GsonInjector
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import kotlin.system.exitProcess

object TongsCli {
    private val logger = LoggerFactory.getLogger(TongsCli::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val parsedArgs = CommandLineArgs()
        JCommander(parsedArgs).let { jc ->
            try {
                jc.parse(*args)
            } catch (e: ParameterException) {
                val out = StringBuilder(e.localizedMessage).append("\n\n")
                jc.usage(out)
                logger.error(out.toString())
                exitProcess(1)
            }
            if (parsedArgs.help) {
                jc.usage()
                return
            }
        }

        try {
            val tongsConfiguration = parsedArgs.configurationFile
                    .bufferedReader(Charsets.UTF_8)
                    .use { configFileReader ->
                        GsonInjector.gson().fromJson(configFileReader, TongsConfigurationJsonExtension::class.java)
                    }

            val configuration = Configuration.Builder.configuration()
                    .withAndroidSdk(parsedArgs.sdk ?: Utils.cleanFileSafe(CommonDefaults.ANDROID_SDK))
                    .withApplicationApk(parsedArgs.apk)
                    .withInstrumentationApk(parsedArgs.testApk)
                    .withOutput(Utils.cleanFileSafe(tongsConfiguration.baseOutputDir ?: Defaults.TONGS_OUTPUT))
                    .withTitle(tongsConfiguration.title)
                    .withSubtitle(tongsConfiguration.subtitle)
                    .withTestPackage(tongsConfiguration.testPackage)
                    .withPlugins(tongsConfiguration.plugins)
                    .withTestOutputTimeout(tongsConfiguration.testOutputTimeout)
                    .withTestRunnerArguments(tongsConfiguration.android.instrumentationArguments)
                    .withExcludedSerials(tongsConfiguration.excludedSerials)
                    .withFallbackToScreenshots(tongsConfiguration.fallbackToScreenshots)
                    .withTotalAllowedRetryQuota(tongsConfiguration.totalAllowedRetryQuota)
                    .withRetryPerTestCaseQuota(tongsConfiguration.retryPerTestCaseQuota)
                    .withCoverageEnabled(tongsConfiguration.isCoverageEnabled)
                    .withPoolingStrategy(tongsConfiguration.poolingStrategy)
                    .withExcludedAnnotation(tongsConfiguration.excludedAnnotation)
                    .withTongsIntegrationTestRunType(tongsConfiguration.tongsIntegrationTestRunType)
                    .withPluginConfiguration(tongsConfiguration.configuration)
                    .build(true)

            val tongs = Tongs(configuration)
            if (!tongs.run() && !tongsConfiguration.ignoreFailures) {
                exitProcess(1)
            }
        } catch (e: FileNotFoundException) {
            logger.error("Could not find configuration file", e)
            exitProcess(1)
        }
    }

    class CommandLineArgs {
        @JvmField
        @field:Parameter(names = ["--sdk"], description = "Path to Android SDK", converter = FileConverter::class)
        var sdk: File? = null

        @field:Parameter(names = ["--apk"], description = "Application APK", converter = FileConverter::class, required = true)
        lateinit var apk: File

        @field:Parameter(names = ["--test-apk"], description = "Test application APK", converter = FileConverter::class, required = true)
        lateinit var testApk: File

        @field:Parameter(names = ["--config"], description = "Path of JSON config file", converter = FileConverter::class, required = true)
        lateinit var configurationFile: File

        @JvmField
        @field:Parameter(names = ["-h", "--help"], description = "Command help", help = true, hidden = true)
        var help = false
    }

    /* JCommander deems it necessary that this class be public. Lame. */
    class FileConverter : IStringConverter<File?> {
        override fun convert(s: String): File? {
            return Utils.cleanFile(s)
        }
    }
}