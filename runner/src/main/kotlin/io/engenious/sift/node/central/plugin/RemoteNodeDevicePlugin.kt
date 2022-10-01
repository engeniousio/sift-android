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
import io.engenious.sift.Config
import io.engenious.sift.LocalConfigurationClient
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
    private val globalConfiguration: Config.WithInjectedCentralNodeVars
) : DeviceProviderFactory<DeviceProvider>, RunRuleFactory<RunRule>, TestCaseProviderFactory<TestCaseProvider> {
    private val sessions = Collections.synchronizedList(mutableListOf<SshSession>())
    private val devicesAndTests = CompletableDeferred<DeviceAndTests>()

    class DeviceAndTests(
        val devices: List<RemoteNodeDevice>,
        val tests: List<TestCase>
    )

    inner class RemoteNodeDeviceProvider : DeviceProvider {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun provideDevices(): Set<Device> = devicesAndTests.getCompleted().devices.toSet()
    }

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

    override fun deviceProviders(context: DeviceProviderContext): Array<out DeviceProvider> {
        return arrayOf(RemoteNodeDeviceProvider())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun suiteLoaders(context: TestCaseProviderContext): Array<out TestCaseProvider> {
        return arrayOf(object : TestCaseProvider {
            override fun loadTestSuite(): Collection<TestCase> = devicesAndTests.getCompleted().tests
        })
    }

    fun connect(): List<RemoteNodeDevice> {
        val allDevices = Collections.synchronizedList(mutableListOf<RemoteNodeDevice>())
        val allTests = Collections.synchronizedList(mutableListOf<TestCase>())

        globalConfiguration.nodes
            .forEachIndexed { index, it ->
                val certificatePath = it.pathToCertificate
                requireNotNull(certificatePath) { "Node ${it.name} has no private key set" }
                require(File(certificatePath).isFile) { "Private key for node ${it.name} is not a file" }

                val session = SshSession.create(
                    it.name,
                    it.host, it.port,
                    it.username, certificatePath
                )

                try {
                    val selfJar = getSelfJarPath()
                    val deploymentPath = it.resolveDeploymentPath { key ->
                        session.executeSingleCommandForStdout("echo $key").trim()
                    }

                    val selfBin = selfJar.parent
                        .resolveSibling("bin")
                        .resolve("sift")

                    val absoluteBinPath: String
                    val nodeConfiguration: Config.WithInjectedCentralNodeVars
                    val uploadTasks = buildUploadTasks {
                        absoluteBinPath = scheduleUpload(selfBin, "$deploymentPath/bin/${selfBin.fileName}")

                        scheduleUpload(selfJar, "$deploymentPath/lib/${selfJar.fileName}")

                        nodeConfiguration = globalConfiguration
                            .withNodes(listOf(it))
                            .withAppPackage(
                                scheduleUpload(
                                    globalConfiguration.appPackage,
                                    "$deploymentPath/app.${fileExtension(globalConfiguration.appPackage)}"
                                )
                            )
                            .withTestPackage(
                                scheduleUpload(
                                    globalConfiguration.testPackage,
                                    "$deploymentPath/test.${fileExtension(globalConfiguration.testPackage)}"
                                )
                            )
                            .withSetUpScriptPath(
                                ifNonEmptyScheduleUpload(
                                    globalConfiguration.setUpScriptPath, "$deploymentPath/setUpScriptPath.sh"
                                )
                            )
                            .withTearDownScriptPath(
                                ifNonEmptyScheduleUpload(
                                    globalConfiguration.tearDownScriptPath, "$deploymentPath/tearDownScriptPath.sh"
                                )
                            )
                    }

                    session.uploadFiles(uploadTasks)
                    val relativeConfigPath = session.uploadConfig(deploymentPath, nodeConfiguration)
                    val localPort = session.setupPortForwarding(it, index)

                    try {
                        session.executeSingleBackgroundCommand(
                            "cd $deploymentPath && " +
                                    "chmod +x ./$absoluteBinPath && " +
                                    "./$absoluteBinPath config _node -c ./$relativeConfigPath"
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
                            allTests.addAll(it)
                        }

                    nodeInfo.devices
                        .map { device ->
                            dict.getOrPut(device) {
                                RemoteNodeDevice(node, device)
                            }
                        }
                        .let {
                            allDevices.addAll(it)
                        }
                    sessions.add(session)
                } catch (t: Throwable) {
                    session.close()
                    throw t
                }
            }

        devicesAndTests.complete(
            DeviceAndTests(
                allDevices,
                allTests
            )
        )
        return allDevices
    }

    private fun fileExtension(path: String) = File(path).extension

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
        it: Config.NodeConfig.WithInjectedCentralNodeVars,
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
        deploymentPath: String,
        nodeConfiguration: Config.WithInjectedCentralNodeVars
    ): String {
        val relativeConfigPath = "config.json"
        val encodedConfig = LocalConfigurationClient.jsonReader.encodeToString(
            Config.WithInjectedCentralNodeVars,
            nodeConfiguration
        )
        uploadContent(encodedConfig.encodeToByteArray(), "$deploymentPath/$relativeConfigPath")
        return relativeConfigPath
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
