package io.engenious.sift

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvVarsInjectorsTest {

    @Test
    fun emptyString() = assertEquals(
        "",
        injectEnvVars("")
    )

    @Test
    fun stringWithoutVariables() = assertEquals(
        "foobar",
        injectEnvVars("foobar")
    )

    @Test
    fun dollarString() = assertEquals(
        "$",
        injectEnvVars("$")
    )

    @Test
    fun notExistingVarsAreNotReplaced() = assertEquals(
        "\$Yuio",
        injectEnvVars("\$Yuio")
    )

    @Test
    fun existingVarsAreReplaced() {
        val (name, value) = System.getenv().entries.first()
        assertEquals(
            value,
            injectEnvVars("\$$name")
        )
    }

    @Test
    fun escapedVarsAreNotReplaced() {
        val (name, _) = System.getenv().entries.first()
        assertEquals(
            "$$name",
            injectEnvVars("\\$$name")
        )
    }

    @Test
    fun doubleEscapedVarsAreReplaced() {
        val (name, value) = System.getenv().entries.first()
        assertEquals(
            """\$value""",
            injectEnvVars("""\\${"$"}$name""")
        )
    }

    @Test
    fun doubleEscapeIsReplaced() {
        assertEquals(
            """aaBB\ccDD""",
            injectEnvVars("""aaBB\\ccDD""")
        )
    }

    @Test
    fun varPrefixAtTheEndIsNotReplaced() {
        assertEquals(
            """aaBBccDD$""",
            injectEnvVars("""aaBBccDD$""")
        )
    }

    @Test
    fun varNameBoundedBySpecialChars() {
        val (name, value) = System.getenv().entries.first()
        assertEquals(
            "$$value^",
            injectEnvVars("$\$$name^")
        )
    }
}
