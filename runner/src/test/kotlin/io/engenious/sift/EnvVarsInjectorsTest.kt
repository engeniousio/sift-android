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
    fun escapedVarsAreNotReplaced() {
        val (name, _) = System.getenv().entries.first()
        assertEquals(
            "$$name",
            "\\$$name".injectEnvVars().string
        )
    }

    @Test
    fun doubleEscapedVarsAreReplaced() {
        val (name, value) = System.getenv().entries.first()
        assertEquals(
            """\$value""",
            """\\${"$"}$name""".injectEnvVars().string
        )
    }

    @Test
    fun doubleEscapeIsReplaced() {
        assertEquals(
            """aaBB\ccDD""",
            """aaBB\\ccDD""".injectEnvVars().string
        )
    }

    @Test
    fun varPrefixAtTheEndIsNotReplaced() {
        assertEquals(
            """aaBBccDD$""",
            """aaBBccDD$""".injectEnvVars().string
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
