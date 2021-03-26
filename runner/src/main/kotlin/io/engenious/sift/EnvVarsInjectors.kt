package io.engenious.sift

fun OrchestratorConfig.injectEnvVars(): MergedConfigWithInjectedVars {
    return MergedConfigWithInjectedVars(
        this
            .mapPropertyValues { (_, value) -> injectEnvVarsIfPossible(value) }
            .let { FileConfigWithInjectedVars(it) }.orchestratorConfigWithInjectedVars
    )
}

private fun injectEnvVarsIfPossible(value: Any?): Any? = when (value) {
    is String -> value.injectEnvVars().string
    is OrchestratorConfig.Node.ThisNode -> injectEnvVarsIntoDataClass(value)
    is OrchestratorConfig.Node.RemoteNode -> injectEnvVarsIntoDataClass(value)
    is OrchestratorConfig.UdidLists -> injectEnvVarsIntoDataClass(value)
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
    val mergedConfigWithInjectedVars: OrchestratorConfig
)

inline class FileConfigWithInjectedVars(
    val orchestratorConfigWithInjectedVars: OrchestratorConfig
)

private val varRegex = Regex("""\\(.)|\$([A-Z][A-Z\d_]+)""")
