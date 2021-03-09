package io.engenious.sift

import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_INT
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_STRING
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigMergersTest {

    @Test
    fun mergeWithEmptyConfig() {
        assertEquals(
            defaultFileConfig,
            mergeConfigs(defaultFileConfig, emptyOrchestratorConfig).mergedConfig
        )
    }

    @Test
    fun mergeWithOverridingInteger() {
        val overridingConfig = emptyOrchestratorConfig.copy(testRetryLimit = 75)
        val expectedConfig = defaultFileConfig.copy(rerunFailedTest = 75)
        assertEquals(
            expectedConfig,
            mergeConfigs(defaultFileConfig, overridingConfig).mergedConfig
        )
    }
    @Test
    fun mergeWithOverridingString() {
        val overridingConfig = emptyOrchestratorConfig.copy(appPackage = "new package")
        val expectedConfig = defaultFileConfig.copy(applicationPackage = "new package")
        assertEquals(
            expectedConfig,
            mergeConfigs(defaultFileConfig, overridingConfig).mergedConfig
        )
    }
    @Test
    fun mergeWithOverridingList() {
        val newNode = FileConfig.Node.RemoteNode(
            "otherNode", "host", 77, "user", "pass", "path", "deploy", "sdk",
            emptyMap(), FileConfig.UdidLists(emptyList(), emptyList())
        )
        val overridingConfig = emptyOrchestratorConfig.copy(nodes = listOf(newNode))
        val expectedConfig = defaultFileConfig.copy(nodes = listOf(newNode))
        assertEquals(
            expectedConfig,
            mergeConfigs(defaultFileConfig, overridingConfig).mergedConfig
        )
    }

    @Test
    fun envVarsAreInjected() {
        val (name, value) = System.getenv().entries.first()
        val originalConfig = defaultFileConfig.copy(applicationPackage = "hjk_\$$name ")
        val expectedConfig = defaultFileConfig.copy(applicationPackage = "hjk_$value ")
        assertEquals(
            expectedConfig,
            mergeConfigs(originalConfig, null).injectEnvVars().mergedConfigWithInjectedVars
        )
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
            nodes = listOf(
                FileConfig.Node.RemoteNode(
                    "name", "host", 77, "user", "pass", "path", "deploy", "sdk",
                    emptyMap(), FileConfig.UdidLists(emptyList(), emptyList())
                )
            ),
            setUpScriptPath = "setUp",
            tearDownScriptPath = "tearDown",
            testsBucket = 7,
            testsExecutionTimeout = 100
        )
        private val emptyOrchestratorConfig = SiftClient.OrchestratorConfig(
            testRetryLimit = DEFAULT_INT,
            tearDownScriptPath = DEFAULT_STRING,
            setUpScriptPath = DEFAULT_STRING,
            nodes = emptyList(),
            outputDirectoryPath = DEFAULT_STRING,
            testPackage = DEFAULT_STRING,
            appPackage = DEFAULT_STRING,
            reportSubtitle = DEFAULT_STRING,
            reportTitle = DEFAULT_STRING,
            globalRetryLimit = DEFAULT_INT,
            testsExecutionTimeout = DEFAULT_INT
        )
    }
}
