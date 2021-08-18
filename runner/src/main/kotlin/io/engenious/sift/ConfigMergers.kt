package io.engenious.sift

import kotlin.reflect.KProperty
import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

fun <T : Any> dataClassToMap(value: T): Map<KProperty<Any?>, Any?> {
    return value::class.memberProperties.associateWith {
        it.getter
            .also { getter -> getter.isAccessible = true }
            .call(value)
    }
}

fun Config.mapPropertyValues(
    transform: (Map.Entry<KProperty<Any?>, Any?>) -> Any?
): Config {
    return dataClassToMap(this)
        .mapValues(transform)
        .mapToDataClass(this)
}

fun <T : Any> Map<KProperty<Any?>, Any?>.mapToDataClass(original: T): T {
    assert(original::class.isData)
    val copyFunction = original::class.memberFunctions.single { it.name == "copy" }
    return (this)
        .mapKeys { (property, _) ->
            copyFunction.findParameterByName(property.name)!!
        }
        .let {
            @Suppress("UNCHECKED_CAST")
            copyFunction.callBy(it + (copyFunction.instanceParameter!! to original)) as T
        }
}
