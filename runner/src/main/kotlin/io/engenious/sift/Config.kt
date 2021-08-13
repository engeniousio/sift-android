@file:UseSerializers(Config.WithInjectedCentralNodeVars.Serializer::class)
package io.engenious.sift

import io.engenious.sift.node.serialization.SurrogateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Marks properties where env vars are replaced with values from execution node, not from the central one
 */
@Target(AnnotationTarget.PROPERTY)
annotation class LocalNodeEnvironment

@Serializable
data class Config(
    private val appPackage: String,
    private val testPackage: String,
    private val outputDirectoryPath: String = "sift-result",
    private val testRetryLimit: Int,
    private val testsBucket: Int = 1, // TODO: implement this option
    private val globalRetryLimit: Int,
    private val reportTitle: String = "Test report",
    private val reportSubtitle: String = " ",
    private val testsExecutionTimeout: Int,
    private val environmentVariables: Map<String, String> = emptyMap(),
//    private val setUpScriptPath: String, // TODO: implement this option
//    private val tearDownScriptPath: String, // TODO: implement this option

    private val nodes: List<NodeConfig>
) {
    fun injectCentralNodeVars() = WithInjectedCentralNodeVars(
        injectEnvVarsIntoDataClass(this, isLocalNode = false)
    )

    open class WithInjectedCentralNodeVars internal constructor(
        protected val resolvedConfig: Config
    ) {
        @Suppress("unused")
        private val preventDefaultSerialization = Any()

        val outputDirectoryPath: String
            get() = resolvedConfig.outputDirectoryPath
        val testsExecutionTimeout: Int
            get() = resolvedConfig.testsExecutionTimeout
        val testRetryLimit: Int
            get() = resolvedConfig.testRetryLimit
        val globalRetryLimit: Int
            get() = resolvedConfig.globalRetryLimit
        val appPackage: String
            get() = resolvedConfig.appPackage
        val testPackage: String
            get() = resolvedConfig.testPackage
        val reportTitle: String
            get() = resolvedConfig.reportTitle
        val reportSubtitle: String
            get() = resolvedConfig.reportSubtitle

        open val nodes: List<NodeConfig.WithInjectedCentralNodeVars> by lazy {
            resolvedConfig.nodes.map {
                NodeConfig.WithInjectedCentralNodeVars(it)
            }
        }

        fun injectLocalNodeVars() = WithInjectedCentralAndNodeVars(
            injectEnvVarsIntoDataClass(resolvedConfig, isLocalNode = true)
        )

        fun withNodes(nodes: List<NodeConfig.WithInjectedCentralNodeVars>): WithInjectedCentralNodeVars {
            return with(NodeConfig.WithInjectedCentralNodeVars) {
                WithInjectedCentralNodeVars(
                    resolvedConfig.copy(nodes = nodes.extractUnwrappedNodes())
                )
            }
        }

        fun withAppPackage(path: String) = WithInjectedCentralNodeVars(
            resolvedConfig.copy(appPackage = path)
        )

        fun withTestPackage(path: String) = WithInjectedCentralNodeVars(
            resolvedConfig.copy(testPackage = path)
        )

        companion object Serializer : SurrogateSerializer<WithInjectedCentralNodeVars, Config>(Config.serializer()) {
            override fun toSurrogate(value: WithInjectedCentralNodeVars): Config = value.resolvedConfig

            override fun fromSurrogate(surrogate: Config) = throw UnsupportedOperationException()
        }
    }

    class WithInjectedCentralAndNodeVars(
        configWithInjectedCentralAndNodeVars: Config
    ) : WithInjectedCentralNodeVars(configWithInjectedCentralAndNodeVars) {
        val environmentVariables: Map<String, String>
            get() = resolvedConfig.environmentVariables

        override val nodes: List<NodeConfig.WithInjectedCentralAndNodeVars> by lazy {
            resolvedConfig.nodes.map {
                NodeConfig.WithInjectedCentralAndNodeVars(it)
            }
        }
    }

    @Serializable
    data class NodeConfig(
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
    ) {

        open class WithInjectedCentralNodeVars(
            protected val resolvedConfig: NodeConfig
        ) {
            fun resolveDeploymentPath(envVarResolver: (String) -> String?): String {
                return injectEnvVars(resolvedConfig.deploymentPath, envVarResolver)
            }

            internal companion object {
                fun List<WithInjectedCentralNodeVars>.extractUnwrappedNodes(): List<NodeConfig> {
                    return this.map {
                        it.resolvedConfig
                    }
                }
            }

            val name: String
                get() = resolvedConfig.name

            val username: String
                get() = resolvedConfig.username
            val pathToCertificate: String?
                get() = resolvedConfig.pathToCertificate

            val host: String
                get() = resolvedConfig.host
            val port: Int
                get() = resolvedConfig.port

            val UDID: UdidLists?
                get() = resolvedConfig.UDID

            val uniqueIdentifier: Any
                get() = resolvedConfig
        }

        class WithInjectedCentralAndNodeVars(
            resolvedConfig: NodeConfig
        ) : WithInjectedCentralNodeVars(resolvedConfig) {
            val deploymentPath: String
                get() = resolvedConfig.deploymentPath
            val androidSdkPath: String
                get() = resolvedConfig.androidSdkPath
            val environmentVariables: Map<String, String>
                get() = resolvedConfig.environmentVariables
        }
    }

    @Serializable
    data class UdidLists(
        /**
         * devices udids, can be null
         */
        @LocalNodeEnvironment
        val devices: List<String>? = null,

        /**
         * emulators names, can be null
         */
        @LocalNodeEnvironment
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
