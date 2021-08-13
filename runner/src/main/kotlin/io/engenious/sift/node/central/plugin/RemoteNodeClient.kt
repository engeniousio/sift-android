package io.engenious.sift.node.central.plugin

import com.github.tarcv.tongs.api.testcases.TestCase
import io.engenious.sift.node.remote.Node
import io.engenious.sift.node.serialization.RemoteDevice
import io.engenious.sift.node.serialization.RemoteTestCaseRunResult
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.http4k.client.OkHttp
import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.format.ConfigurableKotlinxSerialization
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeoutException

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
                when (status.code) {
                    in Int.MIN_VALUE..399 -> Any()
                    in 500..599 -> {
                        throw RetryableException(
                            "Got an error from a node: $status\n${bodyString()}"
                        )
                    }
                    else -> {
                        throw RuntimeException(
                            "Got an error from a node: $status\n${bodyString()}"
                        )
                    }
                }.let { /* exhaustive */ }
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

    fun init(): Node.NodeInfo = doRequest("init", NoArguments())

    fun runTest(device: RemoteDevice, testCase: TestCase, timeoutMillis: Long): RemoteTestCaseRunResult {
        val taskResult: Node.TestRunRequestResult = doRequest("runTest", Node.RunTest(device, testCase))
        val delay = 10_000L
        val timeout = timeoutMillis + delay
        logger.info("Will wait for test result for $timeout ms")

        val timeoutTime = System.currentTimeMillis() + timeout
        do {
            val result: Node.TakeRunResultResult =
                doRequest("takeRunResult", Node.TakeRunResult(taskResult.taskId))
            if (result.result != null) {
                return result.result
            }

            Thread.sleep(delay)
        } while (System.currentTimeMillis() <= timeoutTime)
        throw TimeoutException("Timed out getting test execution result")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified Rq : Any, reified Rs : Any> doRequest(
        method: String,
        request: Rq,
        attempts: Int = 5
    ): Rs {
        val serializedParams = RequestSerializer.asJsonObject(request)
            .let {
                when (it) {
                    is JsonObject -> if (it.isEmpty()) {
                        emptyMap()
                    } else {
                        mapOf("params" to it)
                    }
                    is JsonArray -> if (it.isEmpty()) {
                        emptyMap()
                    } else {
                        mapOf("params" to it)
                    }
                    is JsonPrimitive -> mapOf("params" to it)
                }
            }
        val rpcRequest = JsonObject(
            mapOf(
                "jsonrpc" to JsonPrimitive("2.0"),
                "method" to JsonPrimitive(method),
                "id" to JsonPrimitive(id++)
            ) + serializedParams
        )

        val requestLens = RequestSerializer.autoBody<JsonObject>().toLens()
        val responseLens = RequestSerializer
            .autoBody<JsonObject>()
            .toLens()

        return repeatUntilSuccessful(attempts) {
            Request(Method.POST, baseUrl)
                .with(requestLens of rpcRequest)
                .run(client)
                .let { responseLens(it) }
                .run {
                    RequestSerializer.json.decodeFromJsonElement(
                        serializer(),
                        get("result") ?: errorFromRpcResponse(this)
                    )
                }
        }
    }

    private fun errorFromRpcResponse(response: JsonObject): Nothing {
        val error = response.get("error") ?: response
        throw RuntimeException(error.toString())
    }

    private inline fun <reified RS : Any> repeatUntilSuccessful(attempts: Int = 5, block: () -> RS): RS {
        var attemptsLeft = attempts
        var sleepDelay = 1_000L

        while (true) {
            val exception: Exception = try {
                return block()
            } catch (exception: IOException) {
                exception
            } catch (exception: RetryableException) {
                exception
            }

            if (attempts > 0) {
                Thread.sleep(sleepDelay)
                attemptsLeft -= 1
                sleepDelay *= 2
            } else {
                throw exception
            }
        }
    }

    class RetryableException(message: String) : RuntimeException(message)

    @Serializable class NoArguments
}
