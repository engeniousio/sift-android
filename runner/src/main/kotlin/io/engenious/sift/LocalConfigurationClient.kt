package io.engenious.sift

import io.engenious.sift.run.ResultData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class LocalConfigurationClient(configPath: String) : Client {
    companion object {
        val jsonReader = Json {
            ignoreUnknownKeys = true
        }
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

    override fun getEnabledTests(testPlan: String, status: Config.TestStatus, allTests: MutableSet<TestIdentifier>): Map<TestIdentifier, Int> {
        return collectEnabledTests(allTests).associateWith { -1 }
    }

    private fun collectEnabledTests(allTests: MutableSet<TestIdentifier>): MutableSet<TestIdentifier> {
        val enabledTests = mutableSetOf<TestIdentifier>()
        testList.tests.forEach { testStr ->
            when (TestIdentifier.getTestType(testStr)) {
                TestIdentifierType.TEST -> {
                    enabledTests.add(TestIdentifier.fromString(testStr))
                }
                TestIdentifierType.CLASS -> {
                    val testClass = TestIdentifier.fromString(testStr).`class`
                    allTests
                        .filter { it.`class` == testClass }
                        .let { testsFromClass ->
                            enabledTests.addAll(testsFromClass)
                        }
                }
                TestIdentifierType.PACKAGE -> {
                    val testPackage = TestIdentifier.fromString(testStr).`package`
                    allTests
                        .filter { it.`package` == testPackage }
                        .let { testsFromPackage ->
                            enabledTests.addAll(testsFromPackage)
                        }
                }
                TestIdentifierType.ALL -> {
                    enabledTests.addAll(allTests)
                }
            }
        }
        return enabledTests
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
