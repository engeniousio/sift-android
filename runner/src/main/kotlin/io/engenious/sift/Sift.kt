package io.engenious.sift

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.ManualPooling
import com.github.tarcv.tongs.PoolingStrategy
import com.github.tarcv.tongs.Tongs
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_NODES
import io.engenious.sift.Sift.Companion.mapPropertyValues
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.memberProperties

class Sift(private val configFile: File) {
    fun list(): Int {

        val config = requestConfig()
        val tongsConfiguration = Configuration.Builder()
            .setupCommonTongsConfiguration(config)
            .withOutput(Files.createTempDirectory(tempEmptyDirectoryName).toFile())
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

    fun initOrchestrator(): Int {
        val config = requestConfig()
        val tongsConfiguration = Configuration.Builder()
            .setupCommonTongsConfiguration(config)
            .withOutput(Files.createTempDirectory(tempEmptyDirectoryName).toFile())
            .withPlugins(listOf(ListingPlugin::class.java.canonicalName))
            .build(true)

        Tongs(tongsConfiguration).run()

        val collectedTests = ListingPlugin.collectedTests
        return if (collectedTests.isEmpty()) {
            1
        } else {
            SiftClient(config.token).run {
                postTests(collectedTests)
            }
            0
        }
    }

    private fun requestConfig(): FileConfig {
        val fileConfig = try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            json.decodeFromString(FileConfig.serializer(), configFile.readText())
        } catch (e: IOException) {
            throw RuntimeException("Failed to read the configuration file '$configFile'", e)
        }

        val testPlan = fileConfig.testPlan
        return if (fileConfig.token.isNotEmpty() && !testPlan.isNullOrEmpty()) {
            val orchestratorConfig = SiftClient(fileConfig.token).getConfiguration(testPlan)
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
                        nodes.singleLocalNode()
                            .UDID
                            ?.devices
                            ?: emptyList()
                        )
                )
            }
        }
    }

    private fun Configuration.Builder.setupCommonTongsConfiguration(config: FileConfig): Configuration.Builder {
        ifValueSupplied(config.nodes) {
            val localNode = it.singleLocalNode()
            withAndroidSdk(File(localNode.androidSdkPath))
            withTestRunnerArguments(localNode.environmentVariables)
        }
        ifValueSupplied(config.applicationPackage) { withApplicationApk(File(it)) }
        ifValueSupplied(config.testApplicationPackage) { withInstrumentationApk(File(it)) }
        ifValueSupplied(config.rerunFailedTest) { withRetryPerTestCaseQuota(it) }
        ifValueSupplied(config.globalRetryLimit) { withTotalAllowedRetryQuota(it) }
        ifValueSupplied(config.testsExecutionTimeout) { withTestOutputTimeout(it * 1_000) }
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

    private inline fun <T : Any> ifValueSupplied(value: T, block: (T) -> Unit) {
        if (isNonDefaultValue(value) != false) {
            block(value)
        }
    }

    companion object {
        const val tempEmptyDirectoryName = "sift"

        internal fun mergeConfigs(fileConfig: FileConfig, orchestratorConfig: MergeableConfigFields): FileConfig {
            val orchestratorValues = configToMap(orchestratorConfig)
            return fileConfig.mapPropertyValues { (name, defaultValue) ->
                val overridingValue = orchestratorValues[name]

                assert(defaultValue != null)
                if (overridingValue == null) {
                    return@mapPropertyValues defaultValue
                }
                if (defaultValue!!::class != overridingValue::class &&
                    (defaultValue !is List<*> || overridingValue !is List<*>)
                ) {
                    throw RuntimeException("Orchestrator provided invalid value for '${name}' key")
                }

                val shouldOverride = this@Companion.isNonDefaultValue(overridingValue)
                    ?: throw RuntimeException("Orchestrator provided invalid value for '${name}' key")

                if (shouldOverride) {
                    overridingValue
                } else {
                    return@mapPropertyValues defaultValue
                }
            }
        }

        fun FileConfig.mapPropertyValues(
            transform: (Map.Entry<String, Any?>) -> Any?
        ): FileConfig {
            val copyFunction = this::copy
            return configToMap(this)
                .mapValues(transform)
                .mapKeys { (name, _) ->
                    copyFunction.findParameterByName(name)!!
                }
                .let {
                    copyFunction.callBy(it)
                }
        }

        private fun configToMap(fileConfig: MergeableConfigFields) = MergeableConfigFields::class.memberProperties
            .associate {
                it.name to it.getter.call(fileConfig)
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

private fun Iterable<FileConfig.Node>.singleLocalNode(): FileConfig.Node {
    return this.singleOrNull()
        ?: throw SerializationException(
            "Exactly one node (localhost) should be specified under the 'nodes' key" +
                " (remote nodes will be supported in future versions)"
        )
}
