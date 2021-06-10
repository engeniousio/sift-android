package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.testcases.AnnotationInfo
import com.github.tarcv.tongs.api.testcases.TestCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

@Serializable
data class RemoteTestCase(
    val typeTag: String,
    val testPackage: String,
    val testClass: String,
    val testMethod: String,
    val readablePath: List<String>,
    val properties: Map<String, String>,

    val annotations: List<Pair<String, Map<String, JsonElement>>>,

    val includedDevices: List<RemoteDevice>?
) {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun fromTestCase(value: TestCase) = RemoteTestCase(
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
            },
            value.includedDevices?.map { RemoteDevice.fromLocalDevice(it) }
        )

        fun RemoteTestCase.toTestCase() = TestCase(
            Class.forName(typeTag),
            testPackage,
            testClass,
            testMethod,
            readablePath,
            properties,
            annotations.map {
                AnnotationInfo(
                    it.first,
                    it.second.mapValues { (_, value) ->
                        Json.decodeFromJsonElement(kotlinx.serialization.serializer(), value)
                    }
                )
            },
            includedDevices?.map(RemoteDevice.Companion::fromLocalDevice)?.toSet(),
            Any()
        )
    }
}
