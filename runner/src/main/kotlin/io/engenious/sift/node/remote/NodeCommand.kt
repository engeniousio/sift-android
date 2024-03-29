package io.engenious.sift.node.remote

import io.engenious.sift.Sift
import io.engenious.sift.node.central.plugin.RemoteNodeDevicePlugin.Companion.siftRemotePort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.http4k.format.KotlinxSerialization
import org.http4k.jsonrpc.ErrorHandler
import org.http4k.jsonrpc.ErrorMessage
import org.http4k.jsonrpc.JsonRpc
import org.http4k.server.asServer
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

object NodeCommand : Sift() {
    @ExperimentalCoroutinesApi
    override fun run() {
        val exitCode = try {
            handleCommonErrors {
                val config = requestConfig().injectLocalNodeVars()
                val shutdownSignaller = CountDownLatch(1)
                val node = Node(config, shutdownSignaller)

                JsonRpc.auto(KotlinxSerialization, NodeErrorHandler) {
                    method("init", handler(node::init))
                    method("runTest", handler(node::runTest))
                    method("takeRunResult", handler(node::takeRunResult))
                    method("shutdown", handler(node::shutdown))
                }
                    .asServer(LoopbackV4KtorCIO(siftRemotePort))
                    .start()
                    .use {
                        shutdownSignaller.await()
                    }

                0
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            2
        }
        exitProcess(exitCode)
    }

    object NodeErrorHandler : ErrorHandler {
        override fun invoke(error: Throwable): ErrorMessage {
            return ErrorMessage(500, error.toString())
        }
    }
}
