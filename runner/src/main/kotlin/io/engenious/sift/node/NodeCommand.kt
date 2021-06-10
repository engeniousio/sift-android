package io.engenious.sift.node

import io.engenious.sift.Sift
import org.http4k.format.KotlinxSerialization
import org.http4k.jsonrpc.ErrorHandler
import org.http4k.jsonrpc.ErrorMessage
import org.http4k.jsonrpc.JsonRpc
import org.http4k.server.KtorCIO
import org.http4k.server.asServer
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

object NodeCommand : Sift() {
    override fun run() {
        val exitCode = try {
            handleCommonErrors {
                // val config = readConfigFromFile() // TODO
                val config = requestConfig()
                val shutdownSignaller = CountDownLatch(1)
                val node = Node(config, shutdownSignaller)

                JsonRpc.auto(KotlinxSerialization, NodeErrorHandler) {
                    method("init", handler(node::init))
                    method("runTest", handler(node::runTest))
                    method("shutdown", handler(node::shutdown))
                }
                    .asServer(KtorCIO())
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
