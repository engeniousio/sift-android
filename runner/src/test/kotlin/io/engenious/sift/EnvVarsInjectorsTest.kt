package io.engenious.sift

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvVarsInjectorsTest {

    @Test
    fun emptyString() = assertEquals(
        "",
        injectEnvVars("").string
    )

    @Test
    fun stringWithoutVariables() = assertEquals(
        "foobar",
        injectEnvVars("foobar").string
    )

    @Test
    fun dollarString() = assertEquals(
        "$",
        injectEnvVars("$").string
    )

    @Test
    fun notExistingVarsAreNotReplaced() = assertEquals(
        "\$Yuio",
        injectEnvVars("\$Yuio").string
    )

    @Test
    fun existingVarsAreReplaced() {
        val (name, value) = System.getenv().entries.first()
        assertEquals(
            value,
            injectEnvVars("\$$name").string
        )
    }

    @Test
    fun escapedVarsAreNotReplaced() {
        val (name, _) = System.getenv().entries.first()
        assertEquals(
            "$$name",
            injectEnvVars("\\$$name").string
        )
    }

    @Test
    fun doubleEscapedVarsAreReplaced() {
        val (name, value) = System.getenv().entries.first()
        assertEquals(
            """\$value""",
            injectEnvVars("""\\${"$"}$name""").string
        )
    }

    @Test
    fun doubleEscapeIsReplaced() {
        assertEquals(
            """aaBB\ccDD""",
            injectEnvVars("""aaBB\\ccDD""").string
        )
    }

    @Test
    fun varPrefixAtTheEndIsNotReplaced() {
        assertEquals(
            """aaBBccDD$""",
            injectEnvVars("""aaBBccDD$""").string
        )
    }

    @Test
    fun varNameBoundedBySpecialChars() {
        val (name, value) = System.getenv().entries.first()
        assertEquals(
            "$$value^",
            injectEnvVars("$\$$name^").string
        )
    }
}
