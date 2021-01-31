package io.engenious.sift

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvVarsInjectorsTest {

    @Test
    fun emptyString() = assertEquals(
        "",
        "".injectEnvVars().string
    )

    @Test
    fun stringWithoutVariables() = assertEquals(
        "foobar",
        "foobar".injectEnvVars().string
    )

    @Test
    fun dollarString() = assertEquals(
        "$",
        "$".injectEnvVars().string
    )

    @Test
    fun notExistingVarsAreNotReplaced() = assertEquals(
        "\$Yuio",
        "\$Yuio".injectEnvVars().string
    )

    @Test
    fun existingVarsAreReplaced() {
        val (name, value) = System.getenv().entries.first()
        assertEquals(
            value,
            "\$$name".injectEnvVars().string
        )
    }

    @Test
    fun varNameBoundedBySpecialChars() {
        val (name, value) = System.getenv().entries.first()
        assertEquals(
            "$$value^",
            "$\$$name^".injectEnvVars().string
        )
    }
}
