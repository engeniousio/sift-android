package io.engenious.sift.node.central.plugin

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.DeviceProvider
import com.github.tarcv.tongs.api.devices.DeviceProviderContext
import com.github.tarcv.tongs.api.devices.DeviceProviderFactory
import com.github.tarcv.tongs.api.run.RunRule
import com.github.tarcv.tongs.api.run.RunRuleContext
import com.github.tarcv.tongs.api.run.RunRuleFactory
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.TestCaseProvider
import com.github.tarcv.tongs.api.testcases.TestCaseProviderContext
import com.github.tarcv.tongs.api.testcases.TestCaseProviderFactory
import io.engenious.sift.LocalConfigurationClient
import io.engenious.sift.OrchestratorConfig
import io.engenious.sift.node.serialization.RemoteDevice
import io.engenious.sift.node.serialization.RemoteTestCase.Companion.toTestCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections

class RemoteNodeDevicePlugin(
    private val globalConfiguration: OrchestratorConfig
) : DeviceProviderFactory<DeviceProvider>, RunRuleFactory<RunRule>, TestCaseProviderFactory<TestCaseProvider> {
    private val relativeAutPath = "app." + globalConfiguration.appPackage.substringAfterLast('.')
    private val relativeTestPath = "test." + globalConfiguration.testPackage.substringAfterLast('.')

    private val sessions = Collections.synchronizedList(mutableListOf<SshSession>())
    private val devices = CompletableDeferred<List<RemoteNodeDevice>>()
    private val tests = CompletableDeferred<List<TestCase>>()

    companion object {
        const val siftLocalBasePort = 9760
        const val siftRemotePort = 9759
        val logger: Logger = LoggerFactory.getLogger(RemoteNodeDevicePlugin::class.java)
    }

    override fun runRules(context: RunRuleContext): Array<out RunRule> {
        return arrayOf(object : RunRule {
            override fun before() {
                // no op, connect is called from the outside
            }

            override fun after() {
                disconnectAll()
            }
        })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun deviceProviders(context: DeviceProviderContext): Array<out DeviceProvider> {
        return arrayOf(object : DeviceProvider {
            override fun provideDevices(): Set<Device> = devices.getCompleted().toSet()
        })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun suiteLoaders(context: TestCaseProviderContext): Array<out TestCaseProvider> {
        return arrayOf(object : TestCaseProvider {
            override fun loadTestSuite(): Collection<TestCase> = tests.getCompleted()
        })
    }

    fun connect(): MutableList<RemoteNodeDevice> {
        val allDevices = mutableListOf<RemoteNodeDevice>()

        globalConfiguration.nodes
            .forEachIndexed { index, it ->
                requireNotNull(it.pathToCertificate) { "Node ${it.name} has no private key set" }
                require(File(it.pathToCertificate).isFile) { "Private key for node ${it.name} is not a file" }

                val session = SshSession.create(
                    it.host, it.port,
                    it.username, it.pathToCertificate
                )

                try {
                    val selfJar = getSelfJarPath()
                    val relativeBinPath = session.uploadBinaries(
                        it,
                        selfJar, Paths.get(globalConfiguration.appPackage), Paths.get(globalConfiguration.testPackage)
                    )
                    val relativeConfigPath = session.uploadConfig(it, resolveConfigForNode(it))
                    val localPort = session.setupPortForwarding(it, index)

                    try {
                        session.executeSingleBackgroundCommand(
                            "cd ${it.deploymentPath} && " +
                                "chmod +x ./$relativeBinPath && " +
                                "./$relativeBinPath config _node -c ./$relativeConfigPath"
                        )
                    } catch (e: IOException) {
                        throw RuntimeException("Failed to start the node ${it.name}")
                    }

                    val client = RemoteNodeClient(localPort)
                    val nodeInfo = client.init()

                    val node = RemoteSshNode(
                        it,
                        client
                    )
                    val dict = mutableMapOf<RemoteDevice, RemoteNodeDevice>()

                    nodeInfo.testCases
                        .map { test ->
                            test.toTestCase(
                                deviceMapper = { device ->
                                    dict.getOrPut(device) {
                                        RemoteNodeDevice(node, device)
                                    }
                                }
                            )
                        }
                        .let {
                            tests.complete(it)
                        }

                    nodeInfo.devices
                        .map { device ->
                            dict.getOrPut(device) {
                                RemoteNodeDevice(node, device)
                            }
                        }
                        .run {
                            synchronized(devices) {
                                toCollection(allDevices)
                            }
                        }
                    sessions.add(session)
                } catch (t: Throwable) {
                    session.close()
                    throw t
                }
            }

        devices.complete(allDevices)
        return allDevices
    }

    private fun disconnectAll() {
        synchronized(sessions) {
            sessions.forEach {
                try {
                    it.close()
                } catch (t: Throwable) {
                    logger.warn("Error while closing SSH session", t)
                }
            }
        }
    }

    private fun SshSession.setupPortForwarding(
        it: OrchestratorConfig.RemoteNode,
        nodeIndex: Int
    ): Int {
        val localPort = siftLocalBasePort + nodeIndex
        try {
            forwardLocalToRemotePort(localPort, siftRemotePort)
            return localPort
        } catch (e: IOException) {
            throw RuntimeException("Failed to set up port forwarding for a node ${it.name}")
        }
    }

    private fun SshSession.uploadConfig(
        it: OrchestratorConfig.RemoteNode,
        nodeConfiguration: OrchestratorConfig
    ): String {
        val relativeConfigPath = "config.json"
        val encodedConfig = LocalConfigurationClient.jsonReader.encodeToString(
            OrchestratorConfig.serializer(),
            nodeConfiguration
        )
        uploadContent(encodedConfig.encodeToByteArray(), "${it.deploymentPath}/$relativeConfigPath")
        return relativeConfigPath
    }

    private fun resolveConfigForNode(it: OrchestratorConfig.RemoteNode) = globalConfiguration.copy(
        // TODO: env replacement
        nodes = listOf(it),
        appPackage = relativeAutPath,
        testPackage = relativeTestPath
    )

    private fun SshSession.uploadBinaries(
        nodeConfig: OrchestratorConfig.RemoteNode,
        selfJar: Path,
        appPackage: Path,
        testPackage: Path
    ): String {
        val binDir = selfJar.parent.resolveSibling("bin")
        val selfBin = binDir.resolve("sift")

        val relativeBinPath = "bin/${selfBin.fileName}"
        val relativeJarPath = "lib/${selfJar.fileName}"
        uploadFiles(
            selfBin to "${nodeConfig.deploymentPath}/$relativeBinPath",
            selfJar to "${nodeConfig.deploymentPath}/$relativeJarPath",
            appPackage to "${nodeConfig.deploymentPath}/$relativeAutPath",
            testPackage to "${nodeConfig.deploymentPath}/$relativeTestPath",
        )
        return relativeBinPath
    }

    private fun getSelfJarPath(): Path {
        return javaClass.getResource(javaClass.simpleName + ".class")
            .let {
                it ?: throw RuntimeException("Failed to detect path to sift")
            }
            .toString()
            .removePrefix("jar:")
            .removeSuffix(javaClass.canonicalName.replace("/", ".") + ".class")
            .substringBeforeLast("!")
            .let { Paths.get(URL(it).toURI()) }
    }
}
