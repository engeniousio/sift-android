package io.engenious.sift

import kotlin.reflect.full.hasAnnotation

private fun injectEnvVars(value: Any?, isLocalNode: Boolean): Any? = when (value) {
    is String -> injectEnvVars(value)
    is Config.NodeConfig -> injectEnvVarsIntoDataClass(value, isLocalNode)
    is Config.UdidLists -> injectEnvVarsIntoDataClass(value, isLocalNode)
    is Map<*, *> -> value.mapValues { (_, value) -> injectEnvVars(value, isLocalNode) }
    is List<*> -> value.map { injectEnvVars(it, isLocalNode) }
    else -> value
}

fun <T : Any> injectEnvVarsIntoDataClass(originalClass: T, isLocalNode: Boolean): T {
    return dataClassToMap(originalClass)
        .mapValues { (property, value) ->
            // TODO: unit test this:
            // Central node values are only expanded on a central node,
            // local node ones are only expaned on remote nodes (or on central when it is also a local node)
            if (isLocalNode == property.hasAnnotation<LocalNodeEnvironment>()) {
                injectEnvVars(value, isLocalNode)
            } else {
                value
            }
        }
        .mapToDataClass(originalClass)
}

fun injectEnvVars(s: String, resolver: (String) -> String? = { varName -> System.getenv(varName) }): String {
    return varRegex
        .replace(s) {
            val (entireMatch, escapedChar, varName) = it.groupValues
            when {
                escapedChar.isNotEmpty() -> {
                    escapedChar
                }
                varName.isNotEmpty() -> {
                    resolver(varName) ?: entireMatch
                }
                else -> {
                    entireMatch
                }
            }
        }
}

private val varRegex = Regex("""\\(.)|\$([A-Z][A-Z\d_]+)""")
