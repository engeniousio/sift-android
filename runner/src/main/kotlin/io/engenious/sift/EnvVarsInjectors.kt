package io.engenious.sift

import io.engenious.sift.Sift.Companion.dataClassToMap
import io.engenious.sift.Sift.Companion.mapPropertyValues
import io.engenious.sift.Sift.Companion.mapToDataClass

fun MergedConfig.injectEnvVars(): MergedConfigWithInjectedVars {
    return MergedConfigWithInjectedVars(
        this.mergedConfig
            .mapPropertyValues { (_, value) -> injectEnvVarsIfPossible(value) }
            .let { FileConfigWithInjectedVars(it) }.fileConfigWithInjectedVars
    )
}

fun FileConfig.injectEnvVarsToNonMergableFields(): FileConfigWithInjectedVars {
    return dataClassToMap(this)
        .filterKeys { key -> MergeableConfigFields::class.members.none { it.name == key } }
        .mapValues { (_, value) -> injectEnvVarsIfPossible(value) }
        .mapToDataClass(this)
        .let { FileConfigWithInjectedVars(it) }
}

private fun injectEnvVarsIfPossible(value: Any?): Any? = when (value) {
    is String -> value.injectEnvVars().string
    is FileConfig.Node.ThisNode -> injectEnvVarsIntoDataClass(value)
    is FileConfig.Node.RemoteNode -> injectEnvVarsIntoDataClass(value)
    is FileConfig.UdidLists -> injectEnvVarsIntoDataClass(value)
    is Map<*, *> -> value.mapValues { (_, value) -> injectEnvVarsIfPossible(value) }
    is List<*> -> value.map { injectEnvVarsIfPossible(it) }
    else -> value
}

private fun <T : Any> injectEnvVarsIntoDataClass(value: T): T {
    return dataClassToMap(value)
        .mapValues { (_, value) -> injectEnvVarsIfPossible(value) }
        .mapToDataClass(value)
}

fun String.injectEnvVars(): StringWithInjectedVars {
    return varRegex
        .replace(this) {
            val (entireMatch, escapedChar, varName) = it.groupValues
            when {
                escapedChar.isNotEmpty() -> {
                    escapedChar
                }
                varName.isNotEmpty() -> {
                    System.getenv(varName) ?: entireMatch
                }
                else -> {
                    entireMatch
                }
            }
        }
        .let { StringWithInjectedVars(it) }
}

inline class StringWithInjectedVars(
    val string: String
)

inline class MergedConfigWithInjectedVars(
    val mergedConfigWithInjectedVars: FileConfig
)

inline class FileConfigWithInjectedVars(
    val fileConfigWithInjectedVars: FileConfig
)

private val varRegex = Regex("""\\(.)|\$([A-Z][A-Z\d_]+)""")
