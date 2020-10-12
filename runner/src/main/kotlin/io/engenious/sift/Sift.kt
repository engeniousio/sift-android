package io.engenious.sift

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.ManualPooling
import com.github.tarcv.tongs.PoolingStrategy
import com.github.tarcv.tongs.Tongs
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_NODES
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import kotlin.reflect.KProperty
import kotlin.reflect.full.findParameterByName

class Sift(private val configFile: File) {
    fun list(): Int {

        val config = requestConfig()
        val tongsConfiguration = Configuration.Builder()
                .setupCommonTongsConfiguration(config)
                .withPlugins(listOf(ListingPlugin::class.java.canonicalName))
                .build(true)

        Tongs(tongsConfiguration).run()

        val collectedTests = ListingPlugin.collectedTests
        collectedTests.asSequence()
                .map { "${it.`package`}.${it.`class`}#${it.method}" }
                .sorted()
                .forEach {
                    println(it)
                }

        return if (collectedTests.isNotEmpty()) 0 else 1
    }

    private fun requestConfig(): FileConfig {
        val fileConfig = try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            json.decodeFromString(FileConfig.serializer(), configFile.readText())
        } catch (e: IOException) {
            throw RuntimeException("Failed to read the configuration file '${configFile}'", e)
        }

        return if (fileConfig.token.isNotEmpty()) {
            val orchestratorConfig = SiftClient(fileConfig.token).getConfiguration(fileConfig.testPlan)
            mergeConfigs(fileConfig, orchestratorConfig)
        } else {
            fileConfig
        }
    }

    fun run(): Int {
        val config = requestConfig()
        RunPlugin.config = config

        val tongsConfiguration = Configuration.Builder()
                .setupRunTongsConfiguration(config)
                .withPlugins(listOf(RunPlugin::class.java.canonicalName))
                .build(true)


        val result = try {
            Tongs(tongsConfiguration).run()
        } finally {
            RunPlugin.postResults()
        }

        return if (result) 0 else 1
    }

    private fun FileConfig.tongsPoolStrategy(): PoolingStrategy {
        return PoolingStrategy().apply {
            manual = ManualPooling().apply {
                groupings = mapOf(
                        "devices" to (
                                nodes[0]
                                    .UDID
                                    ?.devices
                                    ?: emptyList()
                                )
                )
            }
        }
    }

    private fun Configuration.Builder.setupCommonTongsConfiguration(config: FileConfig): Configuration.Builder {
        ifValueSupplied(config.nodes.single().androidSdkPath) { withAndroidSdk(File(it)) }
        ifValueSupplied(config.applicationPackage) { withApplicationApk(File(it)) }
        ifValueSupplied(config.testApplicationPackage) { withInstrumentationApk(File(it)) }
        ifValueSupplied(config.rerunFailedTest) { withRetryPerTestCaseQuota(it) }
        ifValueSupplied(config.globalRetryLimit) { withTotalAllowedRetryQuota(it) }

        ifValueSupplied(config.outputDirectoryPath) { withOutput(File(it)) }
        withCoverageEnabled(false)
        withPoolingStrategy(config.tongsPoolStrategy())
        withDdmTermination(true)

        return this
    }

    private fun Configuration.Builder.setupRunTongsConfiguration(config: FileConfig): Configuration.Builder {
        setupCommonTongsConfiguration(config)
        ifValueSupplied(config.reportTitle) { withTitle(it) }
        ifValueSupplied(config.reportSubtitle) { withSubtitle(it) }

        return this
    }

    private inline fun <T: Any> ifValueSupplied(value: T, block: (T) -> Unit) {
        if (isNonDefaultValue(value) != false) {
            block(value)
        }
    }

    companion object {
        internal fun mergeConfigs(fileConfig: FileConfig, orchestratorConfig: MergeableConfigFields): FileConfig {
            val overridingEntries = MergeableConfigFields::class.members
                    .filterIsInstance<KProperty<*>>()
                    .mapNotNull {
                        val defaultValue = it.getter.call(fileConfig)
                        val overridingValue = it.getter.call(orchestratorConfig)

                        assert(defaultValue != null)
                        if (overridingValue == null) {
                            return@mapNotNull null
                        }
                        if (defaultValue!!::class != overridingValue::class &&
                                (defaultValue !is List<*> || overridingValue !is List<*>)) {
                            throw RuntimeException("Orchestrator provided invalid value for '${it.name}' key")
                        }

                        val shouldOverride = isNonDefaultValue(overridingValue)
                                ?: throw RuntimeException("Orchestrator provided invalid value for '${it.name}' key")

                        if (shouldOverride) {
                            it.name to overridingValue
                        } else {
                            null
                        }
                    }

            return fileConfig::copy
                    .let { copyFunction ->
                        val parameterValues = overridingEntries.associate {
                            copyFunction.findParameterByName(it.first)!! to it.second
                        }
                        copyFunction.callBy(parameterValues)
                    }
        }

        private fun isNonDefaultValue(value: Any): Boolean? {
            return when (value) {
                is Number -> value != 0
                is String -> value.isNotEmpty()
                is List<*> -> value.isNotEmpty()
                DEFAULT_NODES -> false
                else -> null
            }
        }
    }
}