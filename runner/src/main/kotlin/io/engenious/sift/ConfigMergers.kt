package io.engenious.sift

import io.engenious.sift.Sift.Companion.isNonDefaultValue
import io.engenious.sift.Sift.Companion.mapPropertyValues

fun mergeConfigs(fileConfig: FileConfig, orchestratorConfig: MergeableConfigFields?): MergedConfig {
    if (orchestratorConfig == null) {
        return MergedConfig(fileConfig)
    }

    val orchestratorValues = Sift.dataClassToMap(orchestratorConfig)
    return fileConfig.mapPropertyValues { (name, defaultValue) ->
        val overridingValue = orchestratorValues[name]

        assert(defaultValue != null)
        if (overridingValue == null) {
            return@mapPropertyValues defaultValue
        }
        if (defaultValue!!::class != overridingValue::class &&
            (defaultValue !is List<*> || overridingValue !is List<*>)
        ) {
            throw RuntimeException("Orchestrator provided invalid value for '${name}' key")
        }

        val shouldOverride = isNonDefaultValue(overridingValue)
            ?: throw RuntimeException("Orchestrator provided invalid value for '${name}' key")

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
