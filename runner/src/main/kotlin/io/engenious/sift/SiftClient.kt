package io.engenious.sift

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

class SiftClient(private val token: String) {
    private val baseUrl = "http://api.orchestrator.engenious.io"

    @OptIn(UnstableDefault::class)
    private val client = HttpClient(Apache) {
        engine {
            followRedirects = true
        }

        install(JsonFeature) {
            serializer = KotlinxSerializer(
                Json(
                        JsonConfiguration(
                                ignoreUnknownKeys = true
                        )
                )
            )
        }
    }

    fun postTests(testCases: Set<TestIdentifier>) {
        // TODO: implement retry
        runBlocking {
            val result: HttpResponse = client.post("$baseUrl/settings") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header("token", token)
                body = TestListRequest(testCases)
            }
            println(result)
        }
    }

    fun getEnabledTests(testPlan: String, status: Config.TestStatus): Set<TestIdentifier> {
        // TODO: implement retry
        return runBlocking {
            val result: RunSettingsReponse = client.get("$baseUrl/settings") {
                header("token", token)
                parameter("platform", siftPlatform)

                parameter("testplan", testPlan)
                if (status != Config.TestStatus.ALL) {
                    parameter("status", status.name.toLowerCase())
                }
            }
            println(result)

            result.tests
                    .map {
                        val parts = it.split("/")
                        val clazz = parts.dropLast(1).joinToString(".")
                        val method = parts.last()
                        TestIdentifier(clazz, method)
                    }
                    .toSet()
        }
    }
}

private const val siftPlatform = "ANDROID"

@Serializable
private data class TestListRequest private constructor(
        val platform: String = siftPlatform,
        val tests: List<String>
) {
    constructor(test: Collection<TestIdentifier>): this(
            tests = test.map { "${it.`class`}/${it.method}" }
    )
}

@Serializable
private data class RunSettingsReponse(
        val tests: List<String>
) {

}