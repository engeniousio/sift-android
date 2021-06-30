package io.engenious.sift

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.arguments.ProcessedArgument
import com.github.ajalt.clikt.parameters.arguments.RawArgument
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.tarcv.tongs.ComputedPooling
import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.ManualPooling
import com.github.tarcv.tongs.PoolingStrategy
import com.github.tarcv.tongs.Tongs
import com.github.tarcv.tongs.api.testcases.NoTestCasesFoundException
import com.github.tarcv.tongs.plugin.android.LocalDeviceProviderFactory
import com.github.tarcv.tongs.pooling.NoDevicesForPoolException
import com.github.tarcv.tongs.pooling.NoPoolLoaderConfiguredException
import io.engenious.sift.Conveyor.Companion.conveyor
import io.engenious.sift.list.NoOpPlugin
import io.engenious.sift.node.central.plugin.RemoteNodeDevicePlugin
import io.engenious.sift.node.central.plugin.RemoteNodeDeviceRunnerPlugin
import io.engenious.sift.node.remote.NodeCommand
import io.engenious.sift.run.RunData
import kotlinx.serialization.SerializationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.system.exitProcess

object SiftMain : CliktCommand(name = "sift", help = "Run tests distributed across nodes and devices") {
    val mode by argument().enumWithHelp<Mode>("Where to get the run configuration from")
    val command by argument().enumWithHelp<Command>("Command to execute")
    val localConfigurationOptions by ConfigurationGroup().cooccurring()
    val orchestratorOptions by OrchestratorGroup().cooccurring()

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
            Command._NODE -> NodeCommand
        }
        command.apply {
            if (orchestratorOptions != null && localConfigurationOptions != null) {
                throw UsageError("Only options of just one mode (local or orchestrator) should be specified")
            }
            options = when (mode) {
                Mode.ORCHESTRATOR -> requireNotNull(orchestratorOptions) { "Missing Orchestrator options" }
                Mode.CONFIG -> requireNotNull(localConfigurationOptions) { "Missing local configuration options" }
            }

            run()
        }
    }

    enum class Mode(private val help: String) {
        CONFIG("From a local configuration file"),
        ORCHESTRATOR("From Orchestrator");

        override fun toString(): String = help
    }
    enum class Command(private val help: String) {
        INIT("Initialize Orchestrator with the list of available tests (or add newly added ones)"),
        LIST("Print all available tests"),
        RUN("Run specified tests"),
        _NODE("");

        override fun toString(): String = help
    }
}

interface ClientProvider {
    val status: OrchestratorConfig.TestStatus
    val testPlan: String

    fun createClient(): Client
}

class ConfigurationGroup : OptionGroup("Local configuration specific options"), ClientProvider {
    val config by option("-c", "--config", help = "Path to a configuration file").required()

    override val testPlan: String = ""
    override val status: OrchestratorConfig.TestStatus = OrchestratorConfig.TestStatus.ENABLED

    override fun createClient(): Client = LocalConfigurationClient(this.config)
}
class OrchestratorGroup : OptionGroup("Orchestrator specific options"), ClientProvider {
    val token by option(help = "Orchestrator token for Android. It can be viewed on Global Settings page").required()
    override val testPlan by option(help = "Orchestrator test plan name").default("default_android_plan")
    override val status by option(help = "Filter tests by status in Orchestrator (default: 'enabled')")
        .enum<OrchestratorConfig.TestStatus> { it.name.toLowerCase(Locale.ROOT) }
        .default(OrchestratorConfig.TestStatus.ENABLED)

    val initSdk: String? by option(help = "Path to Android SDK for 'init' subcommand")
    val initSdkMissingError = "Please specify path to Android SDK with '--init-sdk' command-line option" +
        " or ANDROID_SDK_ROOT/ANDROID_HOME environment variable"

    val allowInsecureTls by option(hidden = true).flag(default = false)
        .help("USE FOR DEBUGGING ONLY, disable protection from Man-in-the-middle(MITM) attacks")

    private val developer: Boolean by option(hidden = true).flag(default = false)
        .help("use experimental, in development, instance of Orchestrator")

    val prodClient by lazy { !developer }

    override fun createClient(): Client {
        return if (prodClient) {
            OrchestratorClient(token, allowInsecureTls)
        } else {
            OrchestratorDevClient(token, allowInsecureTls)
        }
    }
}

private inline fun <reified T : Enum<T>> RawArgument.enumWithHelp(message: String): ProcessedArgument<T, T> {
    val converter: (T) -> String = { it.name.toLowerCase(Locale.ROOT) }
    val maxLength = T::class.java.enumConstants.maxOf { converter(it).length }
    val choicesHelp = T::class.java.enumConstants
        .filterNot { it.toString().isBlank() }
        .joinToString("\n") {
            val choice = converter(it)
            val padding = " ".repeat(4 + maxLength - choice.length)

            "- $choice$padding$it"
        }
    return this.enum(key = converter)
        .help("\n```\n$message\n$choicesHelp\n```\n")
}

abstract class Sift : Runnable {
    lateinit var options: ClientProvider

    companion object {
        private const val noRunId = -1

        private val logger: Logger = LoggerFactory.getLogger(Sift::class.java)
    }

    object List : Sift() {
        override fun run() {
            val config = requestConfig()
            val thisNodeConfig = config.mergedConfigWithInjectedVars.nodes.singleLocalNode()

            val tongsConfiguration = Configuration.Builder()
                .setupCommonTongsConfiguration(config)
                .apply {
                    if (thisNodeConfig != null) {
                        applyLocalNodeConfiguration(config, thisNodeConfig)
                    } else {
                        applyStubLocalNodeConfiguration()
                    }
                }
                .withPoolingStrategy(nodeDevicesStrategy(thisNodeConfiguration = thisNodeConfig))
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
                .map { it.toString() }
                .sorted()
                .forEach { println(it) }

            val exitcode = if (collectedTests.isNotEmpty()) 0 else 1
            exitProcess(exitcode)
        }
    }

    object Init : Sift() {
        override fun run() { // TODO: share code with Run command
            val orchestratorOptions = options.let {
                require(it is OrchestratorGroup) {
                    "'init' subcommand is only supported in orchestrated mode"
                }
                it
            }

            val finalizedConfig: MergedConfigWithInjectedVars = requestConfig()

            val siftClient by lazy {
                options.createClient()
            }

            conveyor
                .prepare(
                    {
                        setupCommonTongsConfiguration(finalizedConfig)

                        val sdkPath = orchestratorOptions.initSdk
                            ?: System.getenv("ANDROID_SDK_ROOT").takeUnless { it.isNullOrBlank() }
                            ?: System.getenv("ANDROID_HOME").takeUnless { it.isNullOrBlank() }
                            ?: throw RuntimeException(orchestratorOptions.initSdkMissingError)
                        withAndroidSdk(File(sdkPath))

                        finalizedConfig.mergedConfigWithInjectedVars.let { config ->
                            ifValueSupplied(config.reportTitle) { withTitle(it) }
                            ifValueSupplied(config.reportSubtitle) { withSubtitle(it) }
                            withPoolingStrategy(allLocalDevicesStrategy)
                        }
                    },
                    TestCaseCollectingPlugin,
                    { allTests, ctx ->
                        if (allTests.isEmpty()) {
                            ctx.throwDeferred(RuntimeException("No tests were found in the test APK"))
                            return@prepare RunData(noRunId, emptyMap())
                        }

                        siftClient.run {
                            postTests(allTests)
                        }
                    },
                    NoOpPlugin,
                    { _, _ -> }
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
            val finalizedConfig: MergedConfigWithInjectedVars = requestConfig()

            val siftClient by lazy {
                options.createClient()
            }

            val deviceRule = RemoteNodeDevicePlugin(
                finalizedConfig.mergedConfigWithInjectedVars.let {
                    it.copy(nodes = it.nodes.filterNot(::isLocalhostNode))
                }
            )
            val remoteDeviceSerials = deviceRule.connect()
                .map { it.serial }
            conveyor
                .prepare(
                    {
                        val thisNodeConfig = finalizedConfig.mergedConfigWithInjectedVars.nodes.singleLocalNode()

                        setupCommonTongsConfiguration(finalizedConfig)
                            .apply {
                                if (thisNodeConfig != null) {
                                    applyLocalNodeConfiguration(finalizedConfig, thisNodeConfig)
                                } else {
                                    applyStubLocalNodeConfiguration()
                                }
                            }
                        finalizedConfig.mergedConfigWithInjectedVars.let { config ->
                            withPoolingStrategy(nodeDevicesStrategy(thisNodeConfig, remoteDeviceSerials))
                            ifValueSupplied(config.reportTitle) { withTitle(it) }
                            ifValueSupplied(config.reportSubtitle) { withSubtitle(it) }
                        }
                    },
                    TestCaseCollectingPlugin,
                    { allTests, ctx ->
                        if (allTests.isEmpty()) {
                            ctx.throwDeferred(RuntimeException("No tests were found in the test APK"))
                            return@prepare RunData(noRunId, emptyMap())
                        }

                        siftClient.run {
                            postTests(allTests)
                            val enabledTests = getEnabledTests(options.testPlan, options.status)
                            val runId = createRun(options.testPlan)
                            RunData(runId, enabledTests)
                        }
                    },
                    FilteringTestCasePlugin,
                    ResultCollectingPlugin(),
                    { result, ctx ->
                        if (result.runId == noRunId) {
                            return@prepare
                        }

                        if (result.results.isEmpty()) {
                            ctx.throwDeferred(RuntimeException("The run produced no results"))
                            return@prepare
                        }
                        siftClient.postResults(options.testPlan, result)
                    }
                )
                .addRule(deviceRule)
                .addRule(RemoteNodeDeviceRunnerPlugin())
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

    protected fun requestConfig(): MergedConfigWithInjectedVars {
        return options.createClient()
            .getConfiguration(options.testPlan)
            .injectEnvVars()
    }
}

fun nodeDevicesStrategy(
    thisNodeConfiguration: OrchestratorConfig.RemoteNode?,
    additionalSerials: List<String> = emptyList()
): PoolingStrategy {
    return PoolingStrategy().apply {
        manual = ManualPooling().apply {
            groupings = mapOf(
                siftPoolName to (
                    thisNodeConfiguration
                        ?.UDID
                        ?.devices
                        ?: emptyList()
                    ) + additionalSerials
            )
        }
    }
}

private val allLocalDevicesStrategy: PoolingStrategy by lazy {
    PoolingStrategy().apply {
        computed = ComputedPooling().apply {
            characteristic = ComputedPooling.Characteristic.api
            groups = mapOf(
                siftPoolName to 0
            )
        }
    }
}

internal fun Configuration.Builder.applyLocalNodeConfiguration(
    config: MergedConfigWithInjectedVars,
    thisNodeConfiguration: OrchestratorConfig.RemoteNode
): Configuration.Builder {
    withAndroidSdk(File(thisNodeConfiguration.androidSdkPath))

    // Node variables have higher priority
    val finalVariables = thisNodeConfiguration.environmentVariables + config.mergedConfigWithInjectedVars.environmentVariables
    withTestRunnerArguments(thisNodeConfiguration.environmentVariables)
    return this
}

private fun Configuration.Builder.applyStubLocalNodeConfiguration(): Configuration.Builder {
    withAndroidSdk(
        Files.createTempDirectory(tempEmptyDirectoryName).toFile()
    )
    withPlugins(listOf("-${LocalDeviceProviderFactory::class.java.name}"))

    return this
}

internal fun Configuration.Builder.setupCommonTongsConfiguration(merged: MergedConfigWithInjectedVars): Configuration.Builder {
    merged.mergedConfigWithInjectedVars.let { it ->
        ifValueSupplied(it.appPackage) { withApplicationApk(File(it)) }
        ifValueSupplied(it.testPackage) { withInstrumentationApk(File(it)) }
        ifValueSupplied(it.testRetryLimit) { withRetryPerTestCaseQuota(it) }
        ifValueSupplied(it.globalRetryLimit) { withTotalAllowedRetryQuota(it) }
        ifValueSupplied(it.testsExecutionTimeout) { withTestOutputTimeout(it * 1_000) }
        ifValueSupplied(it.outputDirectoryPath) { withOutput(File(it)) }
        withCoverageEnabled(false)
        withDdmTermination(true)
    }
    return this
}

const val tempEmptyDirectoryName = "sift"
const val siftPoolName = "devices"

internal fun Collection<OrchestratorConfig.RemoteNode>.singleLocalNode(): OrchestratorConfig.RemoteNode? {
    require(this.isNotEmpty()) {
        "At least one node should be defined"
    }

    val localNode = this.filter(::isLocalhostNode)
    if (localNode.size > 1) {
        throw SerializationException("Only one node can be a localhost node")
    }
    return localNode.singleOrNull()
}

fun isLocalhostNode(node: OrchestratorConfig.RemoteNode): Boolean {
    return node.host == "127.0.0.1" && node.port == 22
}
