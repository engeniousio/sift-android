package io.engenious.sift

import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

fun mergeConfigs(fileConfig: FileConfig, orchestratorConfig: MergeableConfigFields?): MergedConfig {
    if (orchestratorConfig == null) {
        return MergedConfig(fileConfig)
    }

    val orchestratorValues = dataClassToMap(orchestratorConfig)
    return fileConfig.mapPropertyValues { (name, defaultValue) ->
        val overridingValue = orchestratorValues[name]

        assert(defaultValue != null)
        if (overridingValue == null) {
            return@mapPropertyValues defaultValue
        }
        if (defaultValue!!::class != overridingValue::class &&
            (defaultValue !is List<*> || overridingValue !is List<*>)
        ) {
            throw RuntimeException("Orchestrator provided invalid value for '$name' key")
        }

        val shouldOverride = isNonDefaultValue(overridingValue)
            ?: throw RuntimeException("Orchestrator provided invalid value for '$name' key")

        if (shouldOverride) {
            overridingValue
        } else {
            return@mapPropertyValues defaultValue
        }
    }.let { MergedConfig(it) }
}

inline class MergedConfig(
    val mergedConfig: FileConfig
)

fun <T : Any> dataClassToMap(value: T): Map<String, Any?> {
    return value::class.memberProperties
        .associate {
            it.name to it.getter
                .also { getter -> getter.isAccessible = true }
                .call(value)
        }
}

fun FileConfig.mapPropertyValues(
    transform: (Map.Entry<String, Any?>) -> Any?
): FileConfig {
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
        is kotlin.collections.List<*> -> value.isNotEmpty()
        MergeableConfigFields.DEFAULT_NODES -> false
        else -> null
    }
}

inline fun <T : Any> ifValueSupplied(value: T, block: (T) -> Unit) {
    if (isNonDefaultValue(value) != false) {
        block(value)
    }
}
