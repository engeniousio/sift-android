package io.engenious.sift

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.ManualPooling
import com.github.tarcv.tongs.PoolingStrategy
import com.github.tarcv.tongs.Tongs
import io.engenious.sift.Conveyor.Companion.conveyor
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_NODES
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class Sift(private val configFile: File) {
    fun list(): Int {

        val config = requestConfig().injectEnvVars()
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

    private fun requestConfig(): MergedConfig {
        val config = try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            json
                .decodeFromString(FileConfig.serializer(), configFile.readText())
                .injectEnvVarsToNonMergableFields()
        } catch (e: IOException) {
            throw RuntimeException("Failed to read the configuration file '$configFile'", e)
        }

        return config.fileConfigWithInjectedVars.let {
            val testPlan = it.testPlan
            if (it.token.isNotEmpty() && testPlan.isNotEmpty()) {
                val orchestratorConfig = requestOrchestratorConfig(config)
                mergeConfigs(it, orchestratorConfig)
            } else {
                mergeConfigs(it, null)
            }
        }
    }

    private fun requestOrchestratorConfig(
        config: FileConfigWithInjectedVars
    ) = SiftClient(
        config.fileConfigWithInjectedVars.token
    ).getConfiguration(config.fileConfigWithInjectedVars.testPlan)

    fun run(): Int {
        val finalizedConfig: MergedConfigWithInjectedVars = requestConfig().injectEnvVars()
        val testPlan = finalizedConfig.mergedConfigWithInjectedVars.testPlan
        val status = finalizedConfig.mergedConfigWithInjectedVars.status
            ?: throw SerializationException("Field 'status' in the configuration file is required to run tests")

        val siftClient by lazy { SiftClient(finalizedConfig.mergedConfigWithInjectedVars.token) }

        return conveyor
            .prepare(
                {
                    setupRunTongsConfiguration(finalizedConfig)
                },
                TestCaseCollectingPlugin,
                { allTests ->
                    siftClient.run {
                        postTests(allTests)
                        getEnabledTests(testPlan, status)
                    }
                },
                FilteringTestCasePlugin,
                ResultCollectingPlugin(),
                { results ->
                    siftClient.postResults(results)
                }
            )
            .run(withWarnings = true)
            .let { result ->
                if (result) 0 else 1
            }
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

    private fun Configuration.Builder.setupCommonTongsConfiguration(merged: MergedConfigWithInjectedVars): Configuration.Builder {
        merged.mergedConfigWithInjectedVars.let { it ->
            ifValueSupplied(it.nodes) {
                val localNode = it.singleLocalNode()
                withAndroidSdk(File(localNode.androidSdkPath))
                withTestRunnerArguments(localNode.environmentVariables)
            }
            ifValueSupplied(it.applicationPackage) { withApplicationApk(File(it)) }
            ifValueSupplied(it.testApplicationPackage) { withInstrumentationApk(File(it)) }
            ifValueSupplied(it.rerunFailedTest) { withRetryPerTestCaseQuota(it) }
            ifValueSupplied(it.globalRetryLimit) { withTotalAllowedRetryQuota(it) }
            ifValueSupplied(it.testsExecutionTimeout) { withTestOutputTimeout(it * 1_000) }
            ifValueSupplied(it.outputDirectoryPath) { withOutput(File(it)) }
            withCoverageEnabled(false)
            withPoolingStrategy(it.tongsPoolStrategy())
            withDdmTermination(true)
        }
        return this
    }

    private fun Configuration.Builder.setupRunTongsConfiguration(merged: MergedConfigWithInjectedVars): Configuration.Builder {
        merged.mergedConfigWithInjectedVars.let { it ->
            setupCommonTongsConfiguration(merged)
            ifValueSupplied(it.reportTitle) { withTitle(it) }
            ifValueSupplied(it.reportSubtitle) { withSubtitle(it) }
        }
        return this
    }

    private inline fun <T : Any> ifValueSupplied(value: T, block: (T) -> Unit) {
        if (isNonDefaultValue(value) != false) {
            block(value)
        }
    }

    companion object {
        const val tempEmptyDirectoryName = "sift"

        fun FileConfig.mapPropertyValues(
            transform: (Map.Entry<String, Any?>) -> Any?
        ): FileConfig {
            return dataClassToMap(this)
                .mapValues(transform)
                .mapToDataClass(this)
        }

        fun <T : Any> Map<String, Any?>.mapToDataClass(original: T): T {
            assert(original::class.isData)
            val copyFunction = original::class.memberFunctions.single { it.name == "copy" }
            return (this)
                .mapKeys { (name, _) ->
                    copyFunction.findParameterByName(name)!!
                }
                .let {
                    @Suppress("UNCHECKED_CAST")
                    copyFunction.callBy(it + (copyFunction.instanceParameter!! to original)) as T
                }
        }

        fun <T : Any> dataClassToMap(value: T): Map<String, Any?> {
            return value::class.memberProperties
                .associate {
                    it.name to it.getter
                        .also { getter -> getter.isAccessible = true }
                        .call(value)
                }
        }

        fun isNonDefaultValue(value: Any): Boolean? {
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
