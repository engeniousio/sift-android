package io.engenious.sift

import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_INT
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_STRING
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SiftClient(private val token: String) {
    private val baseUrl = "https://staging.api.orchestrator.engenious.io"

    private val client = HttpClient(Apache) {
        engine {
            followRedirects = true
        }

        install(JsonFeature) {
            serializer = KotlinxSerializer(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
            )
        }
        install(HttpCallValidator)
    }

    fun postTests(testCases: Set<TestIdentifier>) {
        // TODO: implement retry
        runBlocking {
            val result: HttpResponse = client.post("$baseUrl/public") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header("token", token)
                body = TestListRequest(testCases)
            }
        }
    }

    fun getEnabledTests(testPlan: String, status: Config.TestStatus): Set<TestIdentifier> {
        // TODO: implement retry
        return runBlocking {
            val result: RunSettingsResponse = client.get("$baseUrl/public") {
                header("token", token)
                parameter("platform", siftPlatform)

                parameter("testplan", testPlan)
                parameter("status", status.name.toUpperCase())
            }

            result.tests
                    .map {
                        TestIdentifier.fromSerialized(it)
                    }
                    .toSet()
        }
    }

    fun postResults(resultMap: Map<TestIdentifier, Boolean>) {
        // TODO: implement retry
        runBlocking {
            val result: HttpResponse = client.post("$baseUrl/public/result") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header("token", token)
                body = TestRunResult(resultMap)
            }
            println(result)
        }
    }
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
    constructor(test: Collection<TestIdentifier>): this(
            tests = test.map { it.toSerialized() }
    )
}

@Serializable
private data class TestRunResult constructor(
        val testResults: Map<String, Boolean>,
        val platform: String = siftPlatform
) {
    constructor(runResults: Map<TestIdentifier, Boolean>): this(
            runResults.mapKeys { it.key.toSerialized() },
            siftPlatform
    )
}

@Serializable
private data class RunSettingsResponse(
        val tests: List<String>
)
