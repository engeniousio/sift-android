package io.engenious.sift

import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_INT
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_NODES
import io.engenious.sift.MergeableConfigFields.Companion.DEFAULT_STRING
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.full.memberProperties

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
    val testPlan: String = "default_android_plan",
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
    override val testsExecutionTimeout: Int = DEFAULT_INT,
    override val setUpScriptPath: String = DEFAULT_STRING, // TODO: implement this option
    override val tearDownScriptPath: String = DEFAULT_STRING, // TODO: implement this option

    override val nodes: List<Node> = DEFAULT_NODES // TODO: implement this option
) : MergeableConfigFields {
    @Serializable(with = Node.Companion::class)
    sealed class Node {
        abstract val androidSdkPath: String

        /**
         * Additional instrumentation arguments passed to a device
         */
        abstract val environmentVariables: Map<String, String>

        abstract val UDID: UdidLists?

        @Serializable
        data class ThisNode(
            override val androidSdkPath: String,
            override val environmentVariables: Map<String, String> = emptyMap(),
            override val UDID: UdidLists?
        ) : Node()

        @Serializable
        data class RemoteNode(
            val name: String,
            val host: String,
            val port: Int,
            val username: String,
            @Deprecated("Will be replaced with pathToCertificate in 1.0") val password: String? = null,
            val pathToCertificate: String? = null,
            val deploymentPath: String,

            override val androidSdkPath: String,

            override val environmentVariables: Map<String, String> = emptyMap(),

            override val UDID: UdidLists?
        ) : Node()

        companion object : KSerializer<Node> {
            override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Node", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): Node {
                val input = decoder as? JsonDecoder
                    ?: throw SerializationException("Expected JsonDecoder for ${decoder::class}")
                val jsonObject = input.decodeJsonElement() as? JsonObject
                    ?: throw SerializationException("Expected object for ${input.decodeJsonElement()::class}")

                val isRemoteNode = jsonObject.keys.any { key ->
                    ThisNode::class.memberProperties.none { prop -> prop.name == key }
                }

                return if (isRemoteNode) {
                    decoder.json.decodeFromJsonElement(RemoteNode.serializer(), jsonObject)
                } else {
                    decoder.json.decodeFromJsonElement(ThisNode.serializer(), jsonObject)
                }
            }

            override fun serialize(encoder: Encoder, value: Node) {
                TODO("Not yet implemented")
            }
        }
    }

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
