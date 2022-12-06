package io.engenious.sift

import org.junit.Assert
import org.junit.Test

class RegexTest {

    @Test
    fun `regex for one test in one class`() {
        Assert.assertEquals(
            TestIdentifier("io.package.tests", "TestClass", "testMethod"),
            Regex("(.+)/(.+)#(.+)")
                .matchEntire("io.package.tests/TestClass#testMethod")
                ?.destructured
                ?.let { (packageName, className, methodName) ->
                    TestIdentifier(packageName, className, methodName)
                }
        )
    }

    @Test
    fun `regex for all test in one class`() {
        Assert.assertEquals(
            TestIdentifier("io.package.tests", "TestClass", "*"),
            Regex("(.+)/(.+)#([*])")
                .matchEntire("io.package.tests/TestClass#*")
                ?.destructured
                ?.let { (packageName, className, methodName) ->
                    TestIdentifier(packageName, className, methodName)
                }
        )
    }

    @Test
    fun `regex for all tests in one package`() {
        Assert.assertEquals(
            TestIdentifier("io.package.tests", "*", "*"),
            Regex("(.+)/([*])")
                .matchEntire("io.package.tests/*")
                ?.destructured
                ?.let { (packageName, className) ->
                    TestIdentifier(packageName, className, "*")
                }
        )
    }

    @Test
    fun `regex for all tests in app`() {
        Assert.assertEquals(
            TestIdentifier("*", "*", "*"),
            Regex("([*])/([*])")
                .matchEntire("*/*")
                ?.destructured
                ?.let { (packageName, className) ->
                    TestIdentifier(packageName, className, "*")
                }
        )
    }
}