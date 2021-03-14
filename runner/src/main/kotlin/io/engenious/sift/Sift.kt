package io.engenious.sift

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.ManualPooling
import com.github.tarcv.tongs.PoolingStrategy
import com.github.tarcv.tongs.Tongs
import io.engenious.sift.Conveyor.Companion.conveyor
import io.engenious.sift.run.RunData
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.system.exitProcess

object SiftMain : BaseSiftCommand(help = "Command to execute") {
    override fun run() {
        // no op
    }
}

abstract class BaseSiftCommand(help: String, name: String? = null) : CliktCommand(
    printHelpOnEmptyArgs = true,
    help = help,
    name = name
)

abstract class Sift(
    help: String,
    name: String? = null,
) : BaseSiftCommand(help = help, name = name) {
    protected val configFile by option("--config", "-c").file(mustBeReadable = true)
        .help("Path to the configuration file")
        .required()

    private val allowInsecureTls by option(hidden = true).flag(default = false)
        .help("USE FOR DEBUGGING ONLY, disable protection from Man-in-the-middle(MITM) attacks")

    private val developer: Boolean by option(hidden = true).flag(default = false)
        .help("use experimental, in development, instance of Orchestrator")

    private val prodClient by lazy { !developer }

    object List : Sift(help = "List all tests in the test package") {
        override fun run() {
            val config = requestConfig(configFile).injectEnvVars()
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

            collectedTests.forEach { println(it) }

            val exitcode = if (collectedTests.isNotEmpty()) 0 else 1
            exitProcess(exitcode)
        }
    }

    object Run : Sift(help = "Run tests according to the current configuration") {
        override fun run() {
            val finalizedConfig: MergedConfigWithInjectedVars = requestConfig(configFile).injectEnvVars()
            val testPlan = finalizedConfig.mergedConfigWithInjectedVars.testPlan
            val status = finalizedConfig.mergedConfigWithInjectedVars.status
                ?: throw SerializationException("Field 'status' in the configuration file is required to run tests")

            val siftClient by lazy {
                createSiftClient(finalizedConfig.mergedConfigWithInjectedVars.token)
            }

            conveyor
                .prepare(
                    {
                        setupCommonTongsConfiguration(finalizedConfig)
                        finalizedConfig.mergedConfigWithInjectedVars.let { config ->
                            ifValueSupplied(config.reportTitle) { withTitle(it) }
                            ifValueSupplied(config.reportSubtitle) { withSubtitle(it) }
                        }
                    },
                    TestCaseCollectingPlugin,
                    { allTests ->
                        siftClient.run {
                            postTests(allTests)
                            val enabledTests = getEnabledTests(testPlan, status)
                            val runId = createRun(testPlan)
                            RunData(runId, enabledTests)
                        }
                    },
                    FilteringTestCasePlugin,
                    ResultCollectingPlugin(),
                    { result ->
                        siftClient.postResults(testPlan, result)
                    }
                )
                .run(withWarnings = true)
                .let { result ->
                    val exitCode = if (result) 0 else 1
                    exitProcess(exitCode)
                }
        }
    }

    protected fun requestConfig(configFile: File): MergedConfig {
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
    ): SiftClient.OrchestratorConfig {
        return createSiftClient(config.fileConfigWithInjectedVars.token)
            .getConfiguration(config.fileConfigWithInjectedVars.testPlan)
    }

    protected fun createSiftClient(token: String): SiftClient {
        return if (prodClient) {
            SiftClient(token, allowInsecureTls)
        } else {
            SiftDevClient(token, allowInsecureTls)
        }
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

private const val tempEmptyDirectoryName = "sift"

private fun Iterable<FileConfig.Node>.singleLocalNode(): FileConfig.Node {
    return this.singleOrNull()
        ?: throw SerializationException(
            "Exactly one node (localhost) should be specified under the 'nodes' key" +
                " (remote nodes will be supported in future versions)"
        )
}
