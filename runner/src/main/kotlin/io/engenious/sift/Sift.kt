package io.engenious.sift

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.ManualPooling
import com.github.tarcv.tongs.PoolingStrategy
import com.github.tarcv.tongs.Tongs
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_NODES
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_STRING
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.reflect.KProperty
import kotlin.reflect.full.findParameterByName
import kotlin.system.exitProcess

fun main(args: Array<String>) = SiftMain
    .subcommands(Sift.List, Sift.Init, Sift.Run)
    .main(args)

@Suppress("unused")
object SiftMain: BaseSiftCommand(help = "Command to execute") {
    override fun run() {
        // no op
    }
}

abstract class BaseSiftCommand(help: String, name: String? = null) : CliktCommand(
    printHelpOnEmptyArgs = true,
    help = help,
    name = name
)

abstract class Sift(help: String, name: String? = null) : BaseSiftCommand(help = help, name = name) {
    private val config by option().file(mustBeReadable = true)
        .help("Path to the configuration file")
        .required()

    @Suppress("unused")
    object List : Sift(help = "List all tests in the test package") {
        override fun run() {
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

            exitProcess(if (collectedTests.isNotEmpty()) 0 else 1)
        }
    }

    @Suppress("unused")
    object Init : Sift(help = "Initialize Orchestrator") {
        override fun run() {
            val config = requestConfig()
            val tongsConfiguration = Configuration.Builder()
                .setupCommonTongsConfiguration(config)
                .withOutput(Files.createTempDirectory(tempEmptyDirectoryName).toFile())
                .withPlugins(listOf(ListingPlugin::class.java.canonicalName))
                .build(true)

            Tongs(tongsConfiguration).run()

            val collectedTests = ListingPlugin.collectedTests
            if (collectedTests.isEmpty()) {
                exitProcess(1)
            } else {
                SiftClient(config.token).run {
                    postTests(collectedTests)
                }
                exitProcess(0)
            }
        }
    }

    @Suppress("unused")
    object Run : Sift(help = "Run tests according to the current configuration") {
        override fun run() {
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

            exitProcess(if (result) 0 else 1)
        }
    }

    protected fun requestConfig(): FileConfig {
        val fileConfig = try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            json.decodeFromString(FileConfig.serializer(), config.readText())
        } catch (e: IOException) {
            throw RuntimeException("Failed to read the configuration file '$config'", e)
        }

        return if (fileConfig.token.isNotEmpty()) {
            val testPlan = fileConfig.testPlan.let {
                if (it.isNullOrEmpty()) {
                    "default_android_plan"
                } else {
                    it
                }
            }
            val orchestratorConfig = SiftClient(fileConfig.token).getConfiguration(testPlan)
            mergeConfigs(fileConfig, orchestratorConfig)
        } else {
            fileConfig
        }
    }

    companion object {
        const val tempEmptyDirectoryName = "sift"

        protected fun Configuration.Builder.setupRunTongsConfiguration(config: FileConfig): Configuration.Builder {
            setupCommonTongsConfiguration(config)
            ifValueSupplied(config.reportTitle) { withTitle(it) }
            ifValueSupplied(config.reportSubtitle) { withSubtitle(it) }

            return this
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

        fun Configuration.Builder.setupCommonTongsConfiguration(config: FileConfig): Configuration.Builder {
            ifValueSupplied(config.nodes) { nodes ->
                val localNode = nodes.singleLocalNode()
                ifValueSupplied(localNode.androidSdkPath ?: DEFAULT_STRING) {
                    withAndroidSdk(File(it))
                }

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

        private inline fun <T : Any> ifValueSupplied(value: T, block: (T) -> Unit) {
            if (isNonDefaultValue(value) != false) {
                block(value)
            }
        }

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
                        (defaultValue !is kotlin.collections.List<*> || overridingValue !is kotlin.collections.List<*>)
                    ) {
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
                is kotlin.collections.List<*> -> value.isNotEmpty()
                DEFAULT_NODES -> false
                else -> null
            }
        }

        private fun Iterable<FileConfig.Node>.singleLocalNode(): FileConfig.Node {
            return this.singleOrNull()
                ?: throw SerializationException(
                    "Exactly one node (localhost) should be specified under the 'nodes' key" +
                        " (remote nodes will be supported in future versions)"
                )
        }
    }
}
