package io.engenious.sift

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrchestratorConfig(
    val appPackage: String,
    val testPackage: String,
    val outputDirectoryPath: String = "sift-result",
    val testRetryLimit: Int,
    val testsBucket: Int = 1, // TODO: implement this option
    val globalRetryLimit: Int,
    val reportTitle: String = "Test report",
    val reportSubtitle: String = " ",
    val testsExecutionTimeout: Int,
    val environmentVariables: Map<String, String> = emptyMap(),
//    val setUpScriptPath: String, // TODO: implement this option
//    val tearDownScriptPath: String, // TODO: implement this option

    val nodes: List<RemoteNode>
) {
    @Serializable
    data class RemoteNode(
        val name: String,
        val host: String,
        val port: Int,
        val username: String,
        @Deprecated("Will be replaced with pathToCertificate in 1.0") val password: String? = null,
        val pathToCertificate: String? = null,
        val deploymentPath: String,

        val androidSdkPath: String,

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
        @SerialName("quarantined") QUARANTINED
    }
}
