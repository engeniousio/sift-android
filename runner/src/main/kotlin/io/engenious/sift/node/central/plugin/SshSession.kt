package io.engenious.sift.node.central.plugin

import io.engenious.sift.exceptions.ConfigurationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelExec
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.client.subsystem.sftp.SftpClient
import org.apache.sshd.client.subsystem.sftp.SftpClientFactory
import org.apache.sshd.common.channel.Channel
import org.apache.sshd.common.util.io.resource.PathResource
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.common.util.security.SecurityUtils
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SshSession private constructor( // TODO: refactor this whole class, especially timeout and singleThreadExecutor
    private val name: String,
    private val session: ClientSession,
    private val dispatcher: ExecutorService
) {
    private var open = true
    private val channels = mutableListOf<Channel>()

    companion object {
        private const val defaultTimeoutSeconds = 30L
        private const val defaultTimeout = 30L * 1000
        private const val shortOperationTimeout = 15_000L

        fun create(name: String, host: String, port: Int, username: String, privateKeyPath: String): SshSession {
            val dispatcher = Executors.newSingleThreadExecutor()

            val client = SshClient.setUpDefaultClient() // TODO: use same client instance for all sessions
            client.start()

            return try {
                dispatcher
                    .submit(
                        Callable {
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
                                try {
                                    client.stop()
                                } finally {
                                    dispatcher.close()
                                }
                                throw t
                            }

                            SshSession(name, session, dispatcher)
                        }
                    )
                    .get(defaultTimeoutSeconds, TimeUnit.SECONDS)
            } catch (e: ExecutionException) {
                val wrappedException = e.cause ?: e
                throw ConfigurationException(
                    "Cannot connect to node '$name' ($host:$port). Please verify host, port, login and key file are correct",
                    wrappedException
                )
            }
        }

        private fun ExecutorService.close() {
            Closeable { this@close.shutdownNow() }.use {
                this@close.shutdown()
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

            return identities
                ?.singleOrNull()
                ?: throw RuntimeException("File $privateKeyPath should contain exactly one private key")
        }
    }

    fun executeSingleBackgroundCommand(command: String) {
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
                    write("$command\n".encodeToByteArray())
                    flush()
                }
                repeat(3) {
                    Thread.sleep(1_000)
                    channel.invertedIn.apply {
                        write("\n\r\n\r\n".encodeToByteArray())
                        flush()
                    }
                }
            }

            val logger = LoggerFactory.getLogger(SshSession::class.java)
            thread(start = true, isDaemon = true) {
                input
                    .bufferedReader()
                    .use { reader ->
                        reader.lineSequence().forEach {
                            logger.info("[$name] $it")
                        }
                    }
            }
        } catch (t: Throwable) {
            withSshDispatcher(shortOperationTimeout) {
                try {
                    ch?.close()
                    responseStream.close()
                } catch (closeEx: Throwable) {
                    t.addSuppressed(closeEx)
                } finally {
                    throw t
                }
            }
        }
        ch?.let { channels.add(it) }
    }

    fun executeSingleCommandForStdout(command: String): String {
        requireOpen()
        val input = PipedInputStream()
        val responseStream = PipedOutputStream(input)
        var ch: ChannelExec? = null
        return try {
            // Use a shell channel instead of an exec as they kill started processes on disconnect
            val channel = withSshDispatcher(defaultTimeout) {
                ch = session.createExecChannel(command)
                ch!!
            }
            withSshDispatcher(defaultTimeout) {
                channel.out = responseStream
                channel.err = responseStream
                channel.open().verify(defaultTimeoutSeconds, TimeUnit.SECONDS)
            }

            runBlocking {
                withTimeout(defaultTimeout) {
                    withContext(Dispatchers.IO) {
                        input.bufferedReader().use {
                            it.readText()
                        }
                    }
                }
            }
        } finally {
            withSshDispatcher(shortOperationTimeout) {
                ch?.close()
                responseStream.close()
            }
        }
    }

    private fun requireOpen() {
        require(open) { "The SSH session should not be closed" }
    }

    fun close() {
        open = false

        Closeable { dispatcher.close() }.use {
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
    }

    fun forwardLocalToRemotePort(localPort: Int, remotePort: Int) {
        withSshDispatcher(shortOperationTimeout) {
            session.startLocalPortForwarding(
                SshdSocketAddress(SshdSocketAddress.LOCALHOST_IPV4, localPort),
                SshdSocketAddress(SshdSocketAddress.LOCALHOST_IPV4, remotePort)
            )
        }
    }

    fun uploadFiles(vararg pairs: Pair<Path, String>) {
        createSftpClient().use { client ->
            pairs.forEach {
                client.createParentPath(it.second)
                client.createFile(it.second) { out ->
                    it.first.toFile().inputStream().copyTo(out)
                }
            }
        }
    }

    fun uploadContent(content: ByteArray, targetPath: String) {
        createSftpClient().use { client ->
            client.createParentPath(targetPath)
            client.createFile(targetPath) { out ->
                out.write(content)
            }
        }
    }

    private fun createSftpClient(): SftpWrapper {
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

    private inline fun <T> withSshDispatcher(
        timeoutMs: Long,
        crossinline block: () -> T
    ): T {
        return dispatcher
            .submit(
                Callable {
                    block()
                }
            )
            .get(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private inner class SftpWrapper(
        private val sftpClient: SftpClient
    ) : Closeable {
        fun createParentPath(
            filePath: String
        ) {
            val parentTargetPath = filePath.substringBeforeLast('/', "")
            if (parentTargetPath.isNotEmpty()) {
                val lstat = kotlin.runCatching { lstat(parentTargetPath) }
                if (lstat.isFailure) {
                    createParentPath(parentTargetPath)
                    withSshDispatcher(shortOperationTimeout) {
                        mkdir(parentTargetPath)
                    }
                }
            }
        }

        fun createFile(
            targetPath: String,
            block: (OutputStream) -> Unit
        ) {
            write(
                targetPath,
                listOf(SftpClient.OpenMode.Create, SftpClient.OpenMode.Truncate, SftpClient.OpenMode.Write),
                block
            )
        }

        override fun close() {
            withSshDispatcher(shortOperationTimeout) {
                sftpClient.close()
            }
        }

        private fun write(path: String, modes: Collection<SftpClient.OpenMode>, writer: (OutputStream) -> Unit) {
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

        private fun mkdir(path: String) {
            sftpClient.mkdir(path)
        }

        private fun lstat(path: String) {
            sftpClient.lstat(path)
        }
    }
}
