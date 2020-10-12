package io.engenious.sift

import kotlinx.cli.ArgType

class EnumArgChoice<T : Enum<T>>(private val choices: Array<T>) : ArgType<T>(true) {
    init {
        if (getChoicesStr().distinct().size != choices.size) {
            throw IllegalArgumentException("Enum value names are not distinct when converted to lower case")
        }
    }

    override val description: kotlin.String
        get() {
            return "{ Value should be one of ${getChoicesStr()} }"
        }

    override fun convert(value: kotlin.String, name: kotlin.String): T {
        val ret = choices.singleOrNull { toActualChoice(it) == value }
        if (ret == null) {
            throw Exception("Option $name is expected to be one of ${getChoicesStr()}. $value is provided.")
        } else {
            return ret
        }
    }

    private fun toActualChoice(name: T) = name.name.toLowerCase()

    private fun getChoicesStr() = choices.map { toActualChoice(it) }

    companion object {
        inline fun <reified T : Enum<T>> enumArgChoice(): EnumArgChoice<T> = EnumArgChoice(enumValues())
    }
}
