package io.engenious.sift

import io.engenious.sift.run.ResultData
import kotlinx.serialization.Serializable
import okhttp3.internal.closeQuietly
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.lens.LensFailure
import org.http4k.lens.MultipartFormFile
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.util.Locale

class SiftDevClient(private val token: String, allowInsecureTls: Boolean) : SiftClient(token, allowInsecureTls) {
    override val baseUrl = "https://dev.api.orchestrator.engenious.io"

    companion object {
        private val logger = LoggerFactory.getLogger(SiftDevClient::class.java)
    }

    override fun postTests(testCases: Set<TestIdentifier>) {
        // TODO: implement retry
        val bodyLens = RequestSerializer.autoBody<TestListRequest>().toLens()
        Request(Method.POST, "$baseUrl/v1/sift")
            .header("token", token)
            .query("platform", siftPlatform)

            .query("status", FileConfig.TestStatus.QUARANTINED.name.toUpperCase(Locale.ROOT))
            .with(bodyLens of TestListRequest(testCases))
            .run(client)
    }

    override fun getEnabledTests(testPlan: String, status: FileConfig.TestStatus): Map<TestIdentifier, Int> {
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

    override fun createRun(testPlan: String): Int {
        // TODO: implement retry
        val request = Request(Method.POST, "$baseUrl/v1/sift/run")
            .header("token", token)
            .query("platform", siftPlatform)
            .query("testplan", testPlan)
        return client(request)
            .decodeBody<CreateRunResponse>()
            .runIndex
    }

    override fun postResults(testPlan: String, result: ResultData) {
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

    override fun getConfiguration(testPlan: String): OrchestratorConfig {
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
}
