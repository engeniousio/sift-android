package io.engenious.sift.node.remote

import io.ktor.application.ApplicationCallPipeline.ApplicationPhase.Call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.util.KtorExperimentalAPI
import org.http4k.core.HttpHandler
import org.http4k.server.Http4kServer
import org.http4k.server.ServerConfig
import org.http4k.server.asHttp4k
import org.http4k.server.fromHttp4K
import java.util.concurrent.TimeUnit

/**
 * Custom KtorCIO implementation that binds to 127.0.0.1 interface
 *
 * Based on http4k-server-ktorcio by http4k contributors which is under Apache License Version 2.0, January 2004
 */
class LoopbackV4KtorCIO(val port: Int = 8000) : ServerConfig {
    @OptIn(KtorExperimentalAPI::class)
    override fun toServer(httpHandler: HttpHandler): Http4kServer = object : Http4kServer {
        private val engine: CIOApplicationEngine = embeddedServer(CIO, port, "127.0.0.1") {
            intercept(Call) {
                with(context) { response.fromHttp4K(httpHandler(request.asHttp4k())) }
                return@intercept finish()
            }
        }

        override fun start() = apply {
            engine.start()
        }

        override fun stop() = apply {
            engine.stop(0, 2, TimeUnit.SECONDS)
        }

        override fun port() = engine.environment.connectors[0].port
    }
}
