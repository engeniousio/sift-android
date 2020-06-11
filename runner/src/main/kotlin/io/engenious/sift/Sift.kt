package io.engenious.sift

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.ManualPooling
import com.github.tarcv.tongs.PoolingStrategy
import com.github.tarcv.tongs.Tongs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File
import java.io.IOException
import java.lang.RuntimeException

class Sift(private val configFile: File) {
    fun list() {
        val config = config()

        val tongsConfiguration = Configuration.aConfigurationBuilder()
                .setupCommonTongsConfiguration(config)
                .withPlugins(listOf(ListingPlugin::class.java.canonicalName))
                .build()
        // TODO: hide Tongs log
        Tongs(tongsConfiguration).run()

        ListingPlugin.collectedTests.asSequence()
                .map { "${it.`package`}.${it.`class`}#${it.method}" }
                .sorted()
                .forEach {
                    println(it)
                }
    }

    fun run() {
        val config = config()
        RunPlugin.config = config

        val tongsConfiguration = Configuration.aConfigurationBuilder()
                .setupCommonTongsConfiguration(config)
                .withPlugins(listOf(RunPlugin::class.java.canonicalName))
                .build()
        /*
        TODO:
         - nodes
         - testsBucket
         - testsExecutionTimeout
         - setUpScriptPath
         - tearDownScriptPath
         */

        // TODO: hide Tongs log
        Tongs(tongsConfiguration).run()
        // TODO: exit code
    }

    private fun Config.tongsPoolStrategy(): PoolingStrategy {
        return PoolingStrategy().apply {
            manual = ManualPooling().apply {
                groupings = mapOf(
                        "devices" to nodes[0] // TODO
                                .UDID
                                .devices
                )
            }
        }
    }

    private fun Configuration.Builder.setupCommonTongsConfiguration(config: Config): Configuration.Builder {
        return withOutput(File(config.outputDirectoryPath))
                .withAndroidSdk(File(config.androidSdkPath))
                .withApplicationApk(File(config.applicationPackage))
                .withInstrumentationApk(File(config.testApplicationPackage))
                .withRetryPerTestCaseQuota(config.rerunFailedTest)
                .withTotalAllowedRetryQuota(Int.MAX_VALUE)
                .withCoverageEnabled(false)
                .withPoolingStrategy(config.tongsPoolStrategy())
                .withDdmTermination(true)
    }

    private fun config(): Config {
        try {
            val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true))
            return json.parse(Config.serializer(), configFile.readText())
        } catch (e: IOException) {
            throw RuntimeException("Failed to read the configuration file '${configFile}'", e)
        }
    }
}