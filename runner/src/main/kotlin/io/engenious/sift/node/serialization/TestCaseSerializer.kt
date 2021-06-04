package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.testcases.AnnotationInfo
import com.github.tarcv.tongs.api.testcases.TestCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

object TestCaseSerializer : KSerializer<TestCase> {
    override val descriptor: SerialDescriptor = SurrogateTestCase.serializer().descriptor

    override fun deserialize(decoder: Decoder): TestCase {
        val surrogate = decoder.decodeSerializableValue(SurrogateTestCase.serializer())
        return TestCase(
            Class.forName(surrogate.typeTag),
            surrogate.testPackage,
            surrogate.testClass,
            surrogate.testMethod,
            surrogate.readablePath,
            surrogate.properties,
            surrogate.annotations.map {
                AnnotationInfo(
                    it.first,
                    it.second.mapValues { (_, value) ->
                        Json.decodeFromJsonElement(serializer(), value)
                    }
                )
            },
            Any()
        )
    }

    @ExperimentalSerializationApi
    override fun serialize(encoder: Encoder, value: TestCase) {
        val surrogate = SurrogateTestCase(
            value.typeTag.canonicalName,
            value.testPackage,
            value.testClass,
            value.testMethod,
            value.readablePath,
            value.properties,
            value.annotations.map { annotation ->
                val fixedProps = annotation.properties.mapValues { (_, value) ->
                    when (value) {
                        is Number -> JsonPrimitive(value)
                        is String -> JsonPrimitive(value)
                        null -> JsonNull
                        else -> try {
                            Json.encodeToJsonElement(serializer(value::class.java), value)
                        } catch (e: SerializationException) {
                            JsonPrimitive("<value not supported for remote nodes>")
                        }
                    }
                }
                annotation.fullyQualifiedName to fixedProps
            }
        )
        encoder.encodeSerializableValue(SurrogateTestCase.serializer(), surrogate)
    }

    @Serializable
    private data class SurrogateTestCase(
        val typeTag: String,
        val testPackage: String,
        val testClass: String,
        val testMethod: String,
        val readablePath: List<String>,
        val properties: Map<String, String>,

        val annotations: List<Pair<String, Map<String, JsonElement>>>
    )
}
