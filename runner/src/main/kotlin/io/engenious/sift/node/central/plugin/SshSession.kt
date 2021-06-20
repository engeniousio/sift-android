package io.engenious.sift.node.central.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.client.subsystem.sftp.SftpClient
import org.apache.sshd.client.subsystem.sftp.SftpClientFactory
import org.apache.sshd.common.channel.Channel
import org.apache.sshd.common.util.io.resource.PathResource
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.common.util.security.SecurityUtils
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SshSession private constructor(private val client: SshClient, private val session: ClientSession) {
    private var open = true
    private val channels = mutableListOf<Channel>()

    companion object {
        private const val defaultTimeoutSeconds = 30L
        private const val defaultTimeout = 30L * 1000
        private const val shortOperationTimeout = 15_000L
        private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        fun create(host: String, port: Int, username: String, privateKeyPath: String): SshSession {
            return runBlocking(dispatcher) {
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

                SshSession(client, session)
            }
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

    fun executeSingleBackgroundCommand(command: String) = runBlocking<Unit>(dispatcher) {
        requireOpen()
        val input = PipedInputStream()
        val responseStream = PipedOutputStream(input)
        var ch: ChannelShell? = null
        try {
            // Use a shell channel instead of an exec as they kill started processess on disconnect
            val channel = withSshDispatcher(defaultTimeout) {
                ch = session.createShellChannel()
                ch!!
            }
            withSshDispatcher(defaultTimeout) {
                channel.out = responseStream
                channel.err = responseStream
                channel.open().verify(defaultTimeoutSeconds, TimeUnit.SECONDS)

                channel.invertedIn.apply {
                    write(command.encodeToByteArray())
                    write("\n".encodeToByteArray())
                    flush()
                }
            }

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
        } catch (t: Throwable) {
            withSshDispatcher(shortOperationTimeout) {
                ch?.close()
                responseStream.close()
                throw t
            }
        }
        ch?.let { channels.add(it) }
    }

    private fun requireOpen() {
        require(open) { "The SSH session should not be closed" }
    }

    fun close() = runBlocking<Unit>(dispatcher) {
        open = false
        withSshDispatcher(defaultTimeout) {
            channels.forEach {
                try {
                    it.close()
                } catch (e: Exception) {}
            }
            try {
                try {
                    session.close(true)
                } catch (e: Exception) {}
            } catch (e: Exception) {}
        }
    }

    fun forwardLocalToRemotePort(localPort: Int, remotePort: Int) = runBlocking<Unit> {
        withSshDispatcher(shortOperationTimeout) {
            session.startLocalPortForwarding(
                SshdSocketAddress(SshdSocketAddress.LOCALHOST_IPV4, localPort),
                SshdSocketAddress(SshdSocketAddress.LOCALHOST_IPV4, remotePort)
            )
        }
    }

    fun uploadFiles(vararg pairs: Pair<Path, String>) = runBlocking<Unit> {
        createSftpClient().use { client ->
            pairs.forEach {
                client.createParentPath(it.second)
                client.createFile(it.second) { out ->
                    it.first.toFile().inputStream().copyTo(out)
                }
            }
        }
    }

    fun uploadContent(content: ByteArray, targetPath: String) = runBlocking<Unit> {
        createSftpClient().use { client ->
            client.createParentPath(targetPath)
            client.createFile(targetPath) { out ->
                out.write(content)
            }
        }
    }

    private suspend fun createSftpClient(): SftpWrapper {
        var client: SftpClient? = null
        return try {
            withSshDispatcher(defaultTimeout) {
                client = SftpClientFactory.instance().createSftpClient(session)
                SftpWrapper(client!!)
            }
        } catch (t: Throwable) {
            @Suppress("BlockingMethodInNonBlockingContext")
            withSshDispatcher(shortOperationTimeout) {
                client?.close()
            }
            throw t
        }
    }

    private suspend fun <T> withSshDispatcher(
        timeoutMs: Long,
        block: suspend CoroutineScope.() -> T
    ): T = withTimeout(timeoutMs) {
        withContext(dispatcher, block)
    }

    private suspend fun <T> withSshDispatcher(
        block: suspend CoroutineScope.() -> T
    ): T = withContext(dispatcher, block)

    private inner class SftpWrapper(
        private val sftpClient: SftpClient
    ) : Closeable {
        suspend fun createParentPath(
            filePath: String
        ) {
            val parentTargetPath = filePath.substringBeforeLast('/', "")
            if (parentTargetPath.isNotEmpty()) {
                val lstat = kotlin.runCatching { lstat(parentTargetPath) }
                if (lstat.isFailure) {
                    createParentPath(parentTargetPath)
                    withTimeout(shortOperationTimeout) {
                        withContext(Companion.dispatcher) {
                            mkdir(parentTargetPath)
                        }
                    }
                }
            }
        }

        suspend fun createFile(
            targetPath: String,
            block: (OutputStream) -> Unit
        ) {
            write(
                targetPath,
                listOf(SftpClient.OpenMode.Create, SftpClient.OpenMode.Truncate, SftpClient.OpenMode.Write),
                block
            )
        }

        override fun close() = runBlocking {
            withSshDispatcher(shortOperationTimeout) {
                sftpClient.close()
            }
        }

        private suspend fun write(path: String, modes: Collection<SftpClient.OpenMode>, writer: (OutputStream) -> Unit) {
            var stream: OutputStream? = null
            try {
                withSshDispatcher(shortOperationTimeout) {
                    stream = sftpClient.write(path, modes)
                }
                stream?.let {
                    withSshDispatcher(defaultTimeout) {
                        writer(it)
                    }
                }
            } finally {
                stream?.let {
                    withSshDispatcher(shortOperationTimeout) {
                        it.close()
                    }
                }
            }
        }

        private suspend fun mkdir(path: String) = withTimeout(shortOperationTimeout) {
            @Suppress("BlockingMethodInNonBlockingContext")
            withContext(dispatcher) {
                sftpClient.mkdir(path)
            }
        }

        private suspend fun lstat(path: String) = withTimeout(shortOperationTimeout) {
            @Suppress("BlockingMethodInNonBlockingContext")
            withContext(dispatcher) {
                sftpClient.lstat(path)
            }
        }
    }
}
