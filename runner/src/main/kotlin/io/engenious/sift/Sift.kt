package io.engenious.sift

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.Tongs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File
import java.io.IOException
import java.lang.RuntimeException

class Sift(private val configFile: File) {
    fun run() {
        val config = config()

        val tongsConfiguration = Configuration.aConfigurationBuilder()
                .withOutput(File(config.outputDirectoryPath))
                .withAndroidSdk(File(config.androidSdkPath))
                .withApplicationApk(File(config.applicationPackage))
                .withInstrumentationApk(File(config.testApplicationPackage))
                .withRetryPerTestCaseQuota(config.rerunFailedTest)
                .withTotalAllowedRetryQuota(Int.MAX_VALUE)
                .withCoverageEnabled(false)
                .withDdmTermination(true)
                .build()
        /*
        TODO:
         - nodes
         - testsBucket
         - testsExecutionTimeout
         - setUpScriptPath
         - tearDownScriptPath
         */

        Tongs(tongsConfiguration).run()
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