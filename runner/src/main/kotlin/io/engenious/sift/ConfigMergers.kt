package io.engenious.sift

import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

fun <T : Any> dataClassToMap(value: T): Map<String, Any?> {
    return value::class.memberProperties
        .associate {
            it.name to it.getter
                .also { getter -> getter.isAccessible = true }
                .call(value)
        }
}

fun OrchestratorConfig.mapPropertyValues(
    transform: (Map.Entry<String, Any?>) -> Any?
): OrchestratorConfig {
    return dataClassToMap(this)
        .mapValues(transform)
        .mapToDataClass(this)
}

fun <T : Any> Map<String, Any?>.mapToDataClass(original: T): T {
    assert(original::class.isData)
    val copyFunction = original::class.memberFunctions.single { it.name == "copy" }
    return (this)
        .mapKeys { (name, _) ->
            copyFunction.findParameterByName(name)!!
        }
        .let {
            @Suppress("UNCHECKED_CAST")
            copyFunction.callBy(it + (copyFunction.instanceParameter!! to original)) as T
        }
}

fun isNonDefaultValue(value: Any): Boolean? {
    return when (value) {
        is Number -> value != 0
        is String -> value.isNotEmpty()
        is List<*> -> value.isNotEmpty()
        else -> null
    }
}

inline fun <T : Any> ifValueSupplied(value: T, block: (T) -> Unit) {
    if (isNonDefaultValue(value) != false) {
        block(value)
    }
}
