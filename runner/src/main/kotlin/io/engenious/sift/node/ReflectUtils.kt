package io.engenious.sift.node

import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

inline fun <reified T : Any> propertyByName(name: String): KProperty1<T, *> {
    return T::class.declaredMemberProperties
        .single { it.name == name }
        .also { it.isAccessible = true }
}

inline fun <reified T : Any> T.extractProperty(name: String): Any? {
    return propertyByName<T>(name)
        .get(this)
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any, V> T.changePropertyField(name: String, value: V) {
    (propertyByName<T>(name).javaField)
        ?.set(this, value)
        ?: throw IllegalArgumentException("Property has no backing field")
}
