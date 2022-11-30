package io.engenious.sift

import io.engenious.sift.run.ResultData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class LocalConfigurationClient(configPath: String) : Client {
    companion object {
        val jsonReader = Json {
            ignoreUnknownKeys = true
        }
        private val logger: Logger = LoggerFactory.getLogger(LocalConfigurationClient::class.java)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val configuration by lazy {
        jsonReader.decodeFromString<Config>(File(configPath).readText())
    }
    @OptIn(ExperimentalSerializationApi::class)
    private val testList by lazy {
        jsonReader.decodeFromString<TestList>(File(configPath).readText())
    }

    override fun postTests(testCases: Set<TestIdentifier>) {
        // no op in local mode
    }

    override fun getEnabledTests(testPlan: String, status: Config.TestStatus): Map<TestIdentifier, Int> {
        logger.info("Local getEnabledTests ${testList.tests}")
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

    override fun getConfiguration(testPlan: String): Config {
        return configuration
    }
}

@Serializable
data class TestList(
    val tests: List<String>
)
