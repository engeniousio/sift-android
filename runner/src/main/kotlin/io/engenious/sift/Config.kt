package io.engenious.sift

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val token: String,
    val testPlan: String,
    val status: TestStatus,

    val androidSdkPath: String,
    val applicationPackage: String,
    val testApplicationPackage: String,
    val outputDirectoryPath: String,
    val rerunFailedTest: Int,
    val testsBucket: Int,
    val testsExecutionTimeout: Int,
    val setUpScriptPath: String = "",
    val tearDownScriptPath: String = "",

    val nodes: List<Node>
) {
    @Serializable
    data class Node(
        val name: String,
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val deploymentPath: String,
        val xcodePath: String,

        /**
         * Additional instrumentation arguments passed to a device
         */
        val environmentVariables: Map<String, String> = emptyMap(),

        val UDID: UdidLists
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
