package io.engenious.sift

import io.engenious.sift.FileConfig.TestStatus
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_INT
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_STRING
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
import org.http4k.lens.LensFailure
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SiftClient(private val token: String, allowInsecureTls: Boolean) {
    private val baseUrl = "https://staging.api.orchestrator.engenious.io"

    private object RequestSerializer : ConfigurableKotlinxSerialization({
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    })

    private object SuccessSerializer : ConfigurableKotlinxSerialization({
        ignoreUnknownKeys = true
        coerceInputValues = true
    })

    private object ErrorSerializer : ConfigurableKotlinxSerialization({
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
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .let {
            it.addInterceptor(
                HttpLoggingInterceptor()
                    .apply { // TODO: only enable in debug mode
                        setLevel(HttpLoggingInterceptor.Level.BODY)
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

    fun postTests(testCases: Set<TestIdentifier>) {
        // TODO: implement retry
        val bodyLens = RequestSerializer.autoBody<TestListRequest>().toLens()
        Request(Method.POST, "$baseUrl/public")
            .header("token", token)
            .query("status", TestStatus.QUARANTINED.name.toUpperCase())
            .with(bodyLens of TestListRequest(testCases))
            .run(client)
    }

    fun getEnabledTests(testPlan: String, status: TestStatus): Set<TestIdentifier> {
        // TODO: implement retry
        return Request(Method.GET, "$baseUrl/public")
            .header("token", token)
            .query("platform", siftPlatform)
            .query("testplan", testPlan)
            .query("status", status.name.toUpperCase())
            .run(client)
            .decodeBody<RunSettingsResponse>()
            .tests
            .map {
                TestIdentifier.fromSerialized(it)
            }
            .toSet()
    }

    fun postResults(resultMap: Map<TestIdentifier, Boolean>) {
        // TODO: implement retry
        val bodyLens = RequestSerializer.autoBody<TestRunResult>().toLens()
        Request(Method.POST, "$baseUrl/public/result")
            .header("token", token)
            .with(bodyLens of TestRunResult(resultMap))
            .run(client)
    }

    fun getConfiguration(testPlan: String): OrchestratorConfig {
        // TODO: implement retry
        val request = Request(Method.GET, "$baseUrl/public")
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

    private inline fun <reified T : Any> Response.decodeBody(): T = SuccessSerializer.autoBody<T>().toLens()(this)
}

private fun TestIdentifier.Companion.fromSerialized(it: String): TestIdentifier {
    val (`package`, `class`, method) = it.split("/", limit = 3)
    return TestIdentifier(`package`, `class`, method)
}
private fun TestIdentifier.toSerialized() = "$`package`/$`class`/$method"

private const val siftPlatform = "ANDROID"

@Serializable
private data class TestListRequest constructor(
    val platform: String = siftPlatform,
    val tests: List<String>
) {
    constructor(test: Collection<TestIdentifier>) : this(
        tests = test.map { it.toSerialized() }
    )
}

@Serializable
private data class TestRunResult constructor(
    val testResults: Map<String, Boolean>,
    val platform: String = siftPlatform
) {
    constructor(runResults: Map<TestIdentifier, Boolean>) : this(
        runResults.mapKeys { it.key.toSerialized() },
        siftPlatform
    )
}

@Serializable
private data class RunSettingsResponse(
    val tests: List<String>
)

@Serializable
data class OrchestratorConfig(
    private val appPackage: String = DEFAULT_STRING,
    private val testPackage: String = DEFAULT_STRING,
    private val poollingStrategy: String = DEFAULT_STRING,
    override val testsBucket: Int = DEFAULT_INT,
    override val outputDirectoryPath: String = DEFAULT_STRING,
    override val globalRetryLimit: Int = DEFAULT_INT,
    private val testRetryLimit: Int = DEFAULT_INT,
    override val testsExecutionTimeout: Int = DEFAULT_INT,
    override val setUpScriptPath: String = DEFAULT_STRING,
    override val tearDownScriptPath: String = DEFAULT_STRING,
    override val reportTitle: String = DEFAULT_STRING,
    override val reportSubtitle: String = DEFAULT_STRING,

    override val nodes: List<FileConfig.Node> = emptyList()
) : MergeableConfigFields {
    override val applicationPackage: String
        get() = appPackage

    override val testApplicationPackage: String
        get() = testPackage

    override val rerunFailedTest: Int
        get() = testRetryLimit

    override val poolingStrategy: String
        get() = poollingStrategy
}

@Serializable
data class Error(
    val message: String
)
