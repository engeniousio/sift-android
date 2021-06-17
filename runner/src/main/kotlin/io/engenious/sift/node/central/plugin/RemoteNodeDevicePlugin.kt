package io.engenious.sift.node.central.plugin

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.DeviceProvider
import com.github.tarcv.tongs.api.devices.DeviceProviderContext
import com.github.tarcv.tongs.api.devices.DeviceProviderFactory
import io.engenious.sift.MergedConfigWithInjectedVars
import io.engenious.sift.OrchestratorConfig
import kotlinx.serialization.json.Json
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.client.subsystem.sftp.SftpClient
import org.apache.sshd.client.subsystem.sftp.SftpClient.OpenMode.Create
import org.apache.sshd.client.subsystem.sftp.SftpClient.OpenMode.Truncate
import org.apache.sshd.client.subsystem.sftp.SftpClient.OpenMode.Write
import org.apache.sshd.client.subsystem.sftp.SftpClientFactory
import org.apache.sshd.common.channel.Channel
import org.apache.sshd.common.channel.ChannelListener
import org.apache.sshd.common.util.io.resource.PathResource
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.common.util.security.SecurityUtils
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RemoteNodeDevicePlugin(
    globalConfiguration: MergedConfigWithInjectedVars
) : DeviceProviderFactory<DeviceProvider> {
    private val globalConfiguration = globalConfiguration.mergedConfigWithInjectedVars
    private val relativeAutPath = "app." + globalConfiguration.mergedConfigWithInjectedVars.appPackage.substringAfterLast('.')
    private val relativeTestPath = "test." + globalConfiguration.mergedConfigWithInjectedVars.testPackage.substringAfterLast('.')

    companion object {
        const val siftLocalBasePort = 9760
        const val siftRemotePort = 9759
    }

    fun connect() {
        globalConfiguration.nodes
            .mapIndexed { index, it ->
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

                    val siftExecChannel = try {
                        session.executeSingleBackgroundCommand(
                            "cd ${it.deploymentPath} && " +
                                "chmod +x ./$relativeBinPath && " +
                                "./$relativeBinPath config _node -c ./$relativeConfigPath"
                        )
                    } catch (e: IOException) {
                        throw RuntimeException("Failed to start the node ${it.name}")
                    }

                    try {
                        val client = RemoteNodeClient(localPort)
                        val nodeInfo = client.init()

                        println(nodeInfo.devices)
                    } catch (t: Throwable) {
                        siftExecChannel.close()
                    }
                } catch (t: Throwable) {
                    session.close()
                    throw t
                }
            }
    }

    private fun SshSession.setupPortForwarding(
        it: OrchestratorConfig.Node.RemoteNode,
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
        it: OrchestratorConfig.Node.RemoteNode,
        nodeConfiguration: OrchestratorConfig
    ): String {
        val relativeConfigPath = "config.json"
        val encodedConfig = Json.encodeToString( // TODO: use the same serializer that used for reading local configs
            OrchestratorConfig.serializer(),
            nodeConfiguration
        )
        uploadContent(encodedConfig.encodeToByteArray(), "${it.deploymentPath}/$relativeConfigPath")
        return relativeConfigPath
    }

    private fun resolveConfigForNode(it: OrchestratorConfig.Node.RemoteNode) = globalConfiguration.copy(
        // TODO: env replacement
        nodes = listOf(it),
        appPackage = relativeAutPath,
        testPackage = relativeTestPath
    )

    private fun SshSession.uploadBinaries(
        nodeConfig: OrchestratorConfig.Node.RemoteNode,
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

    override fun deviceProviders(context: DeviceProviderContext): Array<out DeviceProvider> {
        return arrayOf(object : DeviceProvider {
            override fun provideDevices(): Set<Device> {
                TODO("Not yet implemented")
            }
        })
    }
}
class SshSession private constructor(private val client: SshClient, private val session: ClientSession) {
    private var open = true

    companion object {
        private const val defaultTimeoutSeconds = 30L

        fun create(host: String, port: Int, username: String, privateKeyPath: String): SshSession {
            val client = SshClient.setUpDefaultClient()
            client.start()

            val session = try {
                val session = client
                    .connect(username, host, port)
                    .verify(defaultTimeoutSeconds, TimeUnit.SECONDS).session
                try {
                    session.addPublicKeyIdentity(readKey(privateKeyPath))
                    session.auth().verify(defaultTimeoutSeconds, TimeUnit.SECONDS)
                    session
                } catch (t: Throwable) {
                    session.close()
                    throw t
                }
            } catch (t: Throwable) {
                client.stop()
                throw t
            }

            return SshSession(client, session)
        }

        private fun readKey(privateKeyPath: String): KeyPair {
            val keyBytes = File(privateKeyPath).readBytes()
            val identities = SecurityUtils.loadKeyPairIdentities(
                null,
                PathResource(Paths.get(privateKeyPath)),
                ByteArrayInputStream(keyBytes),
                null
            )

            return identities.singleOrNull()
                ?: throw RuntimeException("File $privateKeyPath should contain exactly one key")
        }
    }

    fun executeSingleCommand(command: String) {
        this.requireOpen()
        PipedInputStream().use { input ->
            PipedOutputStream(input).use { responseStream ->
                // Use a shell channel instead of an exec as they kill started processess on disconnect
                this.session.createShellChannel().use { channel ->
                    channel.out = responseStream
                    channel.err = responseStream
                    channel.use {
                        channel.open().verify(defaultTimeoutSeconds, TimeUnit.SECONDS)

                        channel.addChannelListener(object : ChannelListener {
                            override fun channelClosed(channel: Channel, reason: Throwable?) {
                                super.channelClosed(channel, reason)

                                // TODO: write to a separate log
                                val responseString = String(input.readBytes())
                                println(responseString)
                            }
                        })

                        channel.executeCommand(command)
                        channel.waitFor(
                            EnumSet.of(ClientChannelEvent.CLOSED),
                            TimeUnit.SECONDS.toMillis(defaultTimeoutSeconds)
                        )
                    }
                }
            }
        }
    }

    fun executeSingleBackgroundCommand(command: String): ChannelShell {
        requireOpen()
        val input = PipedInputStream()
        val responseStream = PipedOutputStream(input)
        try {
            // Use a shell channel instead of an exec as they kill started processess on disconnect
            val channel = session.createShellChannel()
            channel.out = responseStream
            channel.err = responseStream
            channel.open().verify(defaultTimeoutSeconds, TimeUnit.SECONDS)

            channel.executeCommand(command)

            thread(start = true, isDaemon = true) {
                input
                    .buffered()
                    .apply {
                        mark(1)
                        use {
                            do {
                                reset()
                                println(String(readBytes()))
                                mark(1)
                            } while (read() != -1)
                        }
                    }
            }
            return channel
        } catch (t: Throwable) {
            responseStream.close()
            throw t
        }
    }

    private fun ChannelShell.executeCommand(command: String) {
        invertedIn.apply {
            write(command.encodeToByteArray())
            write("\n".encodeToByteArray())
            flush()
        }
    }

    private fun requireOpen() {
        require(open) { "The SSH session should not be closed" }
    }

    fun close() {
        open = false
        try {
            session.close(true)
        } finally {
            client.close(true)
        }
    }

    fun forwardLocalToRemotePort(localPort: Int, remotePort: Int) {
        session.startLocalPortForwarding(
            SshdSocketAddress(SshdSocketAddress.LOCALHOST_IPV4, localPort),
            SshdSocketAddress(SshdSocketAddress.LOCALHOST_IPV4, remotePort)
        )
    }

    fun uploadFiles(vararg pairs: Pair<Path, String>) {
        SftpClientFactory.instance().createSftpClient(session).use { client ->
            pairs.forEach {
                client.createParentPath(it.second)
                client.createFile(it.second) { out ->
                    it.first.toFile().inputStream().copyTo(out)
                }
            }
        }
    }

    private fun SftpClient.createParentPath(
        filePath: String
    ) {
        val parentTargetPath = filePath.substringBeforeLast('/', "")
        if (parentTargetPath.isNotEmpty()) {
            val lstat = kotlin.runCatching { this.lstat(parentTargetPath) }
            if (lstat.isFailure) {
                createParentPath(parentTargetPath)
                mkdir(parentTargetPath)
            }
        }
    }

    fun uploadContent(content: ByteArray, targetPath: String) {
        val factory = SftpClientFactory.instance()
        factory.createSftpClient(session).use { client ->
            client.createParentPath(targetPath)
            client.createFile(targetPath) { out ->
                out.write(content)
            }
        }
    }

    private fun SftpClient.createFile(
        targetPath: String,
        block: (OutputStream) -> Unit
    ) {
        write(targetPath, listOf(Create, Truncate, Write)).use(block)
    }
}
