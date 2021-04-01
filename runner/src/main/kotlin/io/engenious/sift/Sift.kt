package io.engenious.sift

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.arguments.ProcessedArgument
import com.github.ajalt.clikt.parameters.arguments.RawArgument
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.ManualPooling
import com.github.tarcv.tongs.PoolingStrategy
import com.github.tarcv.tongs.Tongs
import com.github.tarcv.tongs.api.testcases.NoTestCasesFoundException
import com.github.tarcv.tongs.pooling.NoDevicesForPoolException
import com.github.tarcv.tongs.pooling.NoPoolLoaderConfiguredException
import io.engenious.sift.Conveyor.Companion.conveyor
import io.engenious.sift.list.NoOpPlugin
import io.engenious.sift.run.RunData
import kotlinx.serialization.SerializationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.system.exitProcess

object SiftMain : CliktCommand(help = "Run tests distributed across nodes and devices") {
    val mode by argument().enumWithHelp<Mode>("Where to get the run configuration from")
    val command by argument().enumWithHelp<Command>("Command to execute")
    val orchestratorOptions by OrchestratorGroup() // TODO

    init {
        context {
            helpFormatter = object : CliktHelpFormatter() {
                override fun formatHelp(
                    prolog: String,
                    epilog: String,
                    parameters: List<HelpFormatter.ParameterHelp>,
                    programName: String
                ) = buildString {
                    addUsage(parameters, programName)
                    addProlog(prolog)
                    addCommands(parameters)
                    addArguments(parameters)
                    addOptions(parameters)
                    addEpilog(epilog)
                }
            }
        }
    }

    override fun run() {
        val command = when (command) {
            Command.INIT -> Sift.Init
            Command.LIST -> Sift.List
            Command.RUN -> Sift.Run
        }
        command.apply {
            options = orchestratorOptions
            run()
        }
    }

    enum class Mode(private val help: String) {
        ORCHESTRATOR("From Orchestrator");

        override fun toString(): String = help
    }
    enum class Command(private val help: String) {
        INIT("Initialize Orchestrator with the list of available tests (or add newly added ones)"),
        LIST("Print all available tests"),
        RUN("Run specified tests");

        override fun toString(): String = help
    }
}

class OrchestratorGroup : OptionGroup("Orchestrator specific options") {
    val token by option().required()
    val testPlan by option().default("default_android_plan")
    val status by option()
        .enum<OrchestratorConfig.TestStatus> { it.name.toLowerCase(Locale.ROOT) }
        .default(OrchestratorConfig.TestStatus.ENABLED)

    val allowInsecureTls by option(hidden = true).flag(default = false)
        .help("USE FOR DEBUGGING ONLY, disable protection from Man-in-the-middle(MITM) attacks")

    private val developer: Boolean by option(hidden = true).flag(default = false)
        .help("use experimental, in development, instance of Orchestrator")

    val prodClient by lazy { !developer }
}

private inline fun <reified T : Enum<T>> RawArgument.enumWithHelp(message: String): ProcessedArgument<T, T> {
    val converter: (T) -> String = { it.name.toLowerCase(Locale.ROOT) }
    val maxLength = T::class.java.enumConstants.maxOf { converter(it).length }
    val choicesHelp = T::class.java.enumConstants.joinToString("\n") {
        val choice = converter(it)
        val padding = " ".repeat(4 + maxLength - choice.length)

        "- $choice$padding$it"
    }
    return this.enum(key = converter)
        .help("\n```\n$message\n$choicesHelp\n```\n")
}

abstract class Sift : Runnable {
    lateinit var options: OrchestratorGroup

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(Sift::class.java)
    }

    object List : Sift() {
        override fun run() {
            val config = requestConfig(options.token, options.testPlan).injectEnvVars()
            val tongsConfiguration = Configuration.Builder()
                .setupCommonTongsConfiguration(config)
                .withOutput(Files.createTempDirectory(tempEmptyDirectoryName).toFile())
                .withPlugins(listOf(ListingPlugin::class.java.canonicalName))
                .build(true)

            handleCommonErrors {
                try {
                    Tongs(tongsConfiguration).run(allowThrows = true)
                    logger.error("Failed to list available tests")
                    1
                } catch (e: NoTestCasesFoundException) {
                    // This is expected result
                    0
                }
            }.let { runExitCode ->
                if (runExitCode != 0) {
                    exitProcess(runExitCode)
                }
            }

            val collectedTests = ListingPlugin.collectedTests
            collectedTests.asSequence()
                .map { "${it.`package`}.${it.`class`}#${it.method}" }
                .sorted()
                .forEach { println(it) }

            val exitcode = if (collectedTests.isNotEmpty()) 0 else 1
            exitProcess(exitcode)
        }
    }

    object Init : Sift() {
        override fun run() { // TODO: share code with Run command
            val finalizedConfig: MergedConfigWithInjectedVars = requestConfig(options.token, options.testPlan).injectEnvVars()

            val siftClient by lazy {
                createSiftClient(options.token)
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
                        }
                    },
                    NoOpPlugin,
                    { }
                )
                .apply {
                    val exitCode = handleCommonErrors {
                        try {
                            run(withWarnings = true)
                            logger.error("Failed to list available tests")
                            1
                        } catch (e: NoTestCasesFoundException) {
                            // This is expected result
                            0
                        }
                    }
                    exitProcess(exitCode)
                }
        }
    }

    object Run : Sift() {
        override fun run() {
            val finalizedConfig: MergedConfigWithInjectedVars = requestConfig(options.token, options.testPlan).injectEnvVars()

            val siftClient by lazy {
                createSiftClient(options.token)
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
                            val enabledTests = getEnabledTests(options.testPlan, options.status)
                            val runId = createRun(options.testPlan)
                            RunData(runId, enabledTests)
                        }
                    },
                    FilteringTestCasePlugin,
                    ResultCollectingPlugin(),
                    { result ->
                        siftClient.postResults(options.testPlan, result)
                    }
                )
                .apply {
                    val exitCode = handleCommonErrors {
                        val result = run(withWarnings = true)
                        when {
                            result -> 0
                            else -> 1
                        }
                    }
                    exitProcess(exitCode)
                }
        }
    }

    protected fun handleCommonErrors(runBlock: () -> Int) = try {
        runBlock()
    } catch (e: NoPoolLoaderConfiguredException) {
        logger.error("Configuring devices and pools failed", e)
        1
    } catch (e: NoDevicesForPoolException) {
        logger.error("Configuring devices and pools failed", e)
        1
    } catch (e: NoTestCasesFoundException) {
        logger.error("Error when trying to find test classes", e)
        1
    } catch (e: Exception) {
        logger.error("Error while executing a test run", e)
        1
    }

    protected fun requestConfig(token: String, testPlan: String): OrchestratorConfig {
        return createSiftClient(token)
            .getConfiguration(testPlan)
    }

    protected fun createSiftClient(token: String): SiftClient {
        return if (options.prodClient) {
            SiftClient(token, options.allowInsecureTls)
        } else {
            SiftDevClient(token, options.allowInsecureTls)
        }
    }
}

private fun OrchestratorConfig.tongsPoolStrategy(): PoolingStrategy {
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
        ifValueSupplied(it.appPackage) { withApplicationApk(File(it)) }
        ifValueSupplied(it.testPackage) { withInstrumentationApk(File(it)) }
        ifValueSupplied(it.testRetryLimit) { withRetryPerTestCaseQuota(it) }
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

private fun Iterable<OrchestratorConfig.Node>.singleLocalNode(): OrchestratorConfig.Node {
    return this.singleOrNull()
        ?: throw SerializationException(
            "Exactly one node (localhost) should be specified under the 'nodes' key" +
                " (remote nodes will be supported in future versions)"
        )
}
