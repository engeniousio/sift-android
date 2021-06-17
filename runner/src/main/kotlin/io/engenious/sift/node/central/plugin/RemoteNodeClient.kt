package io.engenious.sift.node.central.plugin

import io.engenious.sift.node.remote.Node
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.http4k.client.OkHttp
import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.format.ConfigurableKotlinxSerialization
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep

open class RemoteNodeClient(port: Int) {
    private val baseUrl = "http://127.0.0.1:$port/rpc"
    private var id = 1
    protected object RequestSerializer : ConfigurableKotlinxSerialization({
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    })

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteNodeClient::class.java)
    }

    private val responseValidator = Filter { next ->
        {
            next(it).apply {
                throw RuntimeException(
                    "Got an error from a node: $status\n${bodyString()}"
                )
            }
        }
    }
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .let {
            it.addInterceptor(
                HttpLoggingInterceptor()
                    .apply { // TODO: only enable in verbose mode
                        setLevel(HttpLoggingInterceptor.Level.BODY)
                    }
            )
        }
        .build()
        .let { OkHttp(it) }
        .let {
            responseValidator.then(it)
        }

    fun init(): Node.NodeInfo = doRequest(InitRequest())

    private inline fun <reified RS : Any> doRequest(request: NoArgsRpcRequest, attempts: Int = 5): RS {
        val bodyLens = RequestSerializer.autoBody<NoArgsRpcRequest>().toLens()
        return repeatUntilSuccessful(attempts) {
            Request(Method.POST, baseUrl)
                .with(bodyLens of request.withId(id++))
                .run(client)
                .decodeBody()
        }
    }

    private fun <RS : Any> repeatUntilSuccessful(attempts: Int = 5, block: () -> RS): RS {
        tailrec fun doRepeat(attempts: Int, sleepDelay: Long): RS {
            val runCatching = runCatching(block)
            return if (runCatching.isSuccess || attempts == 0) {
                runCatching.getOrThrow()
            } else {
                val exception = runCatching.exceptionOrNull()
                if (exception !is Exception) {
                    throw exception!!
                } else {
                    sleep(sleepDelay)
                    doRepeat(attempts - 1, sleepDelay * 2)
                }
            }
        }
        return doRepeat(attempts, 1_000)
    }

    @Serializable
    class InitRequest : NoArgsRpcRequest(
        "init"
    )

    @Serializable
    open class NoArgsRpcRequest private constructor(
        private val method: String,
        @Suppress("unused")
        private val id: Int = 0
    ) {
        @Suppress("unused", "SpellCheckingInspection")
        val jsonrpc = "2.0"

        constructor(method: String) : this(method, 0)

        fun withId(id: Int) = NoArgsRpcRequest(method, id)
    }

    private inline fun <reified T : Any> Response.decodeBody(): T = RequestSerializer.autoBody<T>().toLens()(this)
}
