package io.engenious.sift.node

import io.engenious.sift.Sift
import org.http4k.format.KotlinxSerialization
import org.http4k.jsonrpc.ErrorHandler
import org.http4k.jsonrpc.ErrorMessage
import org.http4k.jsonrpc.JsonRpc
import org.http4k.server.KtorCIO
import org.http4k.server.asServer
import kotlin.system.exitProcess

object NodeCommand : Sift() {
    override fun run() {
        // val config = readConfigFromFile() // TODO
        val config = requestConfig()
        val node = Node(config)

        JsonRpc.auto(KotlinxSerialization, NodeErrorHandler) {
            method("provideDevices", handler(node::provideDevices))
        }
            .asServer(KtorCIO())
            .start()
            .block()

        exitProcess(0)
    }

    object NodeErrorHandler : ErrorHandler {
        override fun invoke(error: Throwable): ErrorMessage {
            return ErrorMessage(500, error.toString())
        }
    }
}
