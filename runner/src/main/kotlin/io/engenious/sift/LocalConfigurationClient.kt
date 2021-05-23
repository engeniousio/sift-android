package io.engenious.sift

import io.engenious.sift.run.ResultData
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class LocalConfigurationClient(configPath: String) : Client {
    private val jsonReader = Json {
        ignoreUnknownKeys = true
    }

    private val configuration by lazy {
        jsonReader.decodeFromString<OrchestratorConfig>(File(configPath).readText())
    }
    private val testList by lazy {
        jsonReader.decodeFromString<TestList>(File(configPath).readText())
    }

    override fun postTests(testCases: Set<TestIdentifier>) {
        // no op in local mode
    }

    override fun getEnabledTests(testPlan: String, status: OrchestratorConfig.TestStatus): Map<TestIdentifier, Int> {
        return testList.tests
            .associate {
                TestIdentifier.fromString(it) to -1
            }
    }

    override fun createRun(testPlan: String): Int {
        // no op in local mode
        return -1
    }

    override fun postResults(testPlan: String, result: ResultData) {
        // no op in local mode
    }

    override fun getConfiguration(testPlan: String): OrchestratorConfig {
        return configuration
    }
}

@Serializable
data class TestList(
    val tests: List<String>
)
