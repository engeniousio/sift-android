package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.testcases.AnnotationInfo
import com.github.tarcv.tongs.api.testcases.TestCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
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

        fun RemoteTestCase.toTestCase(deviceMapper: (RemoteDevice) -> Device = RemoteDevice.Companion::fromLocalDevice) = TestCase(
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
                        @Suppress("USELESS_CAST")
                        when (value) {
                            is JsonPrimitive ->
                                value.booleanOrNull
                                    ?: value.intOrNull
                                    ?: value.longOrNull
                                    ?: value.floatOrNull
                                    ?: value.doubleOrNull
                                    ?: value.contentOrNull
                            is JsonArray -> value as List<*>
                            is JsonObject -> value as Map<String, *>
                        }
                    }
                )
            },
            includedDevices?.map(deviceMapper)?.toSet(),
            Any()
        )
    }
}
