package io.engenious.sift

import io.engenious.sift.run.ResultData
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.internal.closeQuietly
import okhttp3.logging.HttpLoggingInterceptor
import org.http4k.client.OkHttp
import org.http4k.core.ContentType
import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.format.ConfigurableKotlinxSerialization
import org.http4k.lens.LensFailure
import org.http4k.lens.MultipartFormFile
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

open class SiftClient(private val token: String, allowInsecureTls: Boolean) {
    protected open val baseUrl = "https://api.orchestrator.engenious.io"

    companion object {
        private val logger = LoggerFactory.getLogger(SiftClient::class.java)

        const val siftPlatform = "ANDROID"

        fun TestIdentifier.toSerialized() = "$`package`/$`class`/$method"
    }

    protected object RequestSerializer : ConfigurableKotlinxSerialization({
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    })

    protected object SuccessSerializer : ConfigurableKotlinxSerialization({
        ignoreUnknownKeys = true
        coerceInputValues = true
    })

    protected object ErrorSerializer : ConfigurableKotlinxSerialization({
        ignoreUnknownKeys = true
    })

    private val responseValidator = Filter { next ->
        {
            next(it).apply {
                when (status.code) {
                    400 -> {
                        val error = ErrorSerializer.autoBody<Error>().toLens()(this)
                        throw RuntimeException("Got an error from the Orchestrator: ${error.message}")
                    }
                    in 401..Int.MAX_VALUE -> {
                        throw RuntimeException(
                            "Got an error from the Orchestrator: ${bodyString()}"
                        )
                    }
                }
            }
        }
    }
    protected val client = OkHttpClient.Builder()
        .followRedirects(true)
        .let {
            it.addInterceptor(
                HttpLoggingInterceptor()
                    .apply { // TODO: only enable in verbose mode
                        setLevel(HttpLoggingInterceptor.Level.HEADERS)
                        redactHeader("token")
                    }
            )
        }
        .also {
            if (allowInsecureTls) {
                it.sslTrustAnything()
            }
        }
        .build()
        .let { OkHttp(it) }
        .let {
            responseValidator.then(it)
        }

    private fun OkHttpClient.Builder.sslTrustAnything(): OkHttpClient.Builder = try {
        val trustAllCerts: Array<TrustManager> = arrayOf(
            object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>?,
                    authType: String?
                ) {
                    // no op, because everything is trusted
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                    // no op, because everything is trusted
                }

                override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOfNulls(0)
            }
        )

        val sslContext: SSLContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory

        this
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
    } catch (e: Exception) {
        throw java.lang.RuntimeException(e)
    }

    open fun postTests(testCases: Set<TestIdentifier>) {
        // TODO: implement retry
        val bodyLens = RequestSerializer.autoBody<TestListRequest>().toLens()
        Request(Method.POST, "$baseUrl/v1/sift")
            .header("token", token)
            .query("platform", siftPlatform)

            .query("status", OrchestratorConfig.TestStatus.QUARANTINED.name.toUpperCase(Locale.ROOT))
            .with(bodyLens of TestListRequest(testCases))
            .run(client)
    }

    open fun getEnabledTests(testPlan: String, status: OrchestratorConfig.TestStatus): Map<TestIdentifier, Int> {
        // TODO: implement retry
        val request = Request(Method.GET, "$baseUrl/v1/sift")
            .header("token", token)
            .query("platform", siftPlatform)

            .query("testplan", testPlan)
            .query("status", status.name.toUpperCase(Locale.ROOT))
        return client(request)
            .decodeBody<RunSettingsResponse>()
            .tests
            .associate {
                TestIdentifier.fromSerialized(it.testName) to it.testId
            }
    }

    open fun createRun(testPlan: String): Int {
        // TODO: implement retry
        val request = Request(Method.POST, "$baseUrl/v1/sift/run")
            .header("token", token)
            .query("platform", siftPlatform)
            .query("testplan", testPlan)
        return client(request)
            .decodeBody<CreateRunResponse>()
            .runIndex
    }

    open fun postResults(testPlan: String, result: ResultData) {
        val runId = result.runId
        val resultMap = result.results.values

        // TODO: implement retry
        val bodyLens = RequestSerializer.autoBody<TestRunResult>().toLens()
        val request = Request(Method.POST, "$baseUrl/v1/sift/result")
            .header("token", token)
            .query("platform", siftPlatform)
            .query("testplan", testPlan)
            .with(bodyLens of TestRunResult(runId, resultMap))
        client(request)

        fun FilledTestResult.resultSize(): Long {
            return (this.result.screenshot?.length() ?: 0) + 1024
        }

        class ResultAccumulator() {
            var currentSize: Long = 0
                private set
            private var currentChunk: MutableList<FilledTestResult> = mutableListOf()
            private val chunks: MutableList<List<FilledTestResult>> = mutableListOf()

            fun addItem(item: FilledTestResult) {
                currentChunk.add(item)
                currentSize += item.resultSize()
            }

            fun addToNewChunk(item: FilledTestResult) {
                finalizeChunk()

                addItem(item)
            }

            private fun finalizeChunk() {
                chunks.add(currentChunk)

                currentChunk = mutableListOf()
                currentSize = 0
            }

            fun finalize(): List<List<FilledTestResult>> {
                finalizeChunk()
                return chunks
            }
        }
        resultMap
            .fold(ResultAccumulator()) { acc, it ->
                when {
                    acc.currentSize == 0L -> acc.addItem(it)
                    acc.currentSize + it.resultSize() < 50 * 1024 * 1024 -> acc.addItem(it)
                    else -> acc.addToNewChunk(it)
                }

                acc
            }
            .finalize()
            .forEach {
                var body = MultipartFormBody()
                val streams = it.mapNotNull { item ->
                    try {
                        item.result.screenshot?.inputStream()?.let { stream ->
                            body = body.plus(
                                "file" to MultipartFormFile(
                                    "${item.id}.png",
                                    ContentType.OCTET_STREAM,
                                    stream
                                )
                            )

                            stream
                        }
                    } catch (e: IOException) {
                        logger.warn("Failed to upload a failure screenshot ${item.result.screenshot}", e)
                        null
                    }
                }
                if (streams.isNotEmpty()) {
                    try {
                        Request(Method.POST, "$baseUrl/v1/sift/multi-upload")
                            .header("token", token)
                            .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                            .header("run-index", runId.toString())
                            .query("platform", siftPlatform)
                            .query("testplan", testPlan)
                            .body(body)
                            .run(client)
                    } finally {
                        streams.forEach(InputStream::closeQuietly)
                    }
                }
            }
    }

    open fun getConfiguration(testPlan: String): OrchestratorConfig {
        // TODO: implement retry
        val request = Request(Method.GET, "$baseUrl/v1/sift")
            .header("token", token)
            .query("platform", siftPlatform)

            .query("testplan", testPlan)
            .query("status", "ENABLED")
        try {
            return client(request).decodeBody()
        } catch (e: LensFailure) {
            e.cause
                ?.let { throw it }
                ?: throw e
        }
    }

    protected inline fun <reified T : Any> Response.decodeBody(): T = SuccessSerializer.autoBody<T>().toLens()(this)

    fun TestIdentifier.Companion.fromSerialized(it: String): TestIdentifier {
        val (`package`, `class`, method) = it.split("/", limit = 3)
        return TestIdentifier(`package`, `class`, method)
    }

    @Serializable
    private data class TestListRequest constructor(
        val tests: List<String>
    ) {
        constructor(test: Collection<TestIdentifier>) : this(
            tests = test.map { it.toSerialized() }
        )
    }

    @Serializable
    private data class TestRunResult constructor(
        val runIndex: Int,
        val testResults: List<TestRunResultItem>
    ) {
        constructor(runIndex: Int, runResults: Collection<FilledTestResult>) : this(
            runIndex,
            runResults.map { TestRunResultItem(it) }
        )

        @Serializable
        private data class TestRunResultItem(
            val testId: Int,
            val result: String,
            val errorMessage: String
        ) {
            constructor(result: FilledTestResult) : this(
                result.id,
                result.result.status.serialized,
                result.result.errorMessage.take(255)
            )
        }
    }

    @Serializable
    private data class RunSettingsResponse(
        val tests: List<TestEntry>
    ) {
        @Serializable
        data class TestEntry(
            val testId: Int,
            val testName: String
        )
    }

    @Serializable
    private data class CreateRunResponse(
        val runIndex: Int
    )

    @Serializable
    data class Error(
        val message: String
    )
}
