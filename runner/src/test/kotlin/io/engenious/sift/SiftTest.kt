package io.engenious.sift

import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_INT
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_STRING
import org.junit.Assert.assertEquals
import org.junit.Test

class SiftTest {

    @Test
    fun mergeWithEmptyConfig() {
        assertEquals(defaultFileConfig, Sift.mergeConfigs(defaultFileConfig, emptyOrchestratorConfig))
    }

    @Test
    fun mergeWithOverridingInteger() {
        val overridingConfig = emptyOrchestratorConfig.copy(rerunFailedTest = 75)
        val expectedConfig = defaultFileConfig.copy(rerunFailedTest = 75)
        assertEquals(expectedConfig, Sift.mergeConfigs(defaultFileConfig, overridingConfig))
    }
    @Test
    fun mergeWithOverridingString() {
        val overridingConfig = emptyOrchestratorConfig.copy(applicationPackage = "new package")
        val expectedConfig = defaultFileConfig.copy(applicationPackage = "new package")
        assertEquals(expectedConfig, Sift.mergeConfigs(defaultFileConfig, overridingConfig))
    }
    @Test
    fun mergeWithOverridingList() {
        val newNode = FileConfig.Node(
                "otherNode", "host", 77, "user", "pass", "deploy", "sdk",
                emptyMap(), FileConfig.UdidLists(emptyList(), emptyList())
        )
        val overridingConfig = emptyOrchestratorConfig.copy(nodes = listOf(newNode))
        val expectedConfig = defaultFileConfig.copy(nodes = listOf(newNode))
        assertEquals(expectedConfig, Sift.mergeConfigs(defaultFileConfig, overridingConfig))
    }

    companion object {
        private val defaultFileConfig = FileConfig(
                token = "",
                testPlan = "TESTPLAN",
                status = FileConfig.TestStatus.ENABLED,
                applicationPackage = "APP PACKAGE",
                testApplicationPackage = "TEST APP PKG",
                outputDirectoryPath = "OUTPUT DIR",
                rerunFailedTest = 5,
                nodes = listOf(FileConfig.Node(
                        "name", "host", 77, "user", "pass", "deploy", "sdk",
                        emptyMap(), FileConfig.UdidLists(emptyList(), emptyList()))
                ),
                setUpScriptPath = "setUp",
                tearDownScriptPath = "tearDown",
                testsBucket = 7,
                testsExecutionTimeout = 100
        )
        private val emptyOrchestratorConfig = OrchestratorConfig(
                rerunFailedTest = DEFAULT_INT,
                tearDownScriptPath = DEFAULT_STRING,
                setUpScriptPath = DEFAULT_STRING,
                nodes = emptyList(),
                outputDirectoryPath = DEFAULT_STRING,
                testApplicationPackage = DEFAULT_STRING,
                applicationPackage = DEFAULT_STRING,
                reportSubtitle = DEFAULT_STRING,
                reportTitle = DEFAULT_STRING,
                globalRetryLimit = DEFAULT_INT,
                testExecutionTimeout = DEFAULT_INT
        )
    }
}