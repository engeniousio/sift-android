package io.engenious.sift

import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_INT
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_NODES
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_STRING
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface MergeableConfigFields {
    val rerunFailedTest: Int
    val applicationPackage: String
    val testApplicationPackage: String
    val outputDirectoryPath: String
    val setUpScriptPath: String
    val tearDownScriptPath: String
    val nodes: List<FileConfig.Node>
    val globalRetryLimit: Int?
    val testsExecutionTimeout: Int?
    val poolingStrategy: String?
    val reportTitle: String?
    val reportSubtitle: String?
    val testsBucket: Int

    companion object {
        const val DEFAULT_INT = 0
        const val DEFAULT_STRING = ""
        val DEFAULT_NODES = emptyList<FileConfig.Node>()
    }
}

@Serializable
data class FileConfig(
    val token: String,
    val testPlan: String? = null,
    val status: TestStatus? = null,

    override val applicationPackage: String = DEFAULT_STRING,
    override val testApplicationPackage: String = DEFAULT_STRING,
    override val outputDirectoryPath: String = "sift-result",
    override val rerunFailedTest: Int = DEFAULT_INT,
    override val testsBucket: Int = 1, // TODO: implement this option
    override val globalRetryLimit: Int = DEFAULT_INT,
    override val poolingStrategy: String = DEFAULT_STRING, // TODO: implement this option
    override val reportTitle: String = "Test report",
    override val reportSubtitle: String = " ",
    override val testsExecutionTimeout: Int = DEFAULT_INT, // TODO: implement this option
    override val setUpScriptPath: String = DEFAULT_STRING, // TODO: implement this option
    override val tearDownScriptPath: String = DEFAULT_STRING, // TODO: implement this option

    override val nodes: List<Node> = DEFAULT_NODES // TODO: implement this option
) : MergeableConfigFields {
    @Serializable
    data class Node(
        val name: String,
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val deploymentPath: String,

        val androidSdkPath: String,

        /**
         * Additional instrumentation arguments passed to a device
         */
        val environmentVariables: Map<String, String> = emptyMap(),

        val UDID: UdidLists?
    )

    @Serializable
    data class UdidLists(
        /**
         * devices udids, can be null
         */
        val devices: List<String>? = null,

        /**
         * emulators names, can be null
         */
        val simulators: List<String>? = null
    )

    @Suppress("unused")
    @Serializable
    enum class TestStatus {
        @SerialName("enabled") ENABLED,
        @SerialName("disabled") DISABLED,
        @SerialName("quarantine") QUARANTINE
    }
}
