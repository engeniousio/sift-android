/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.injector

import org.junit.Assert.*
import org.junit.Test
import org.slf4j.LoggerFactory

class RuleBlockTest {
    val executedParts = ArrayList<String>()

    inner class Rule(val index: Int) {
        fun before() {
            executedParts.add("Before $index")
        }
        fun after(e: Throwable?) {
            val withException = (e != null)
            executedParts.add("After $index - exception:$withException")
        }
    }

    fun action(): String {
        executedParts.add("Action")
        return "Success"
    }

    @Test
    fun exceptionInBefore() {
        try {
            withRules(
                    logger,
                    "",
                    "",
                    listOf(Rule(1), Rule(2), Rule(3)),
                    {
                        it.before()
                        if (it.index == 2) {
                            throw RuntimeException()
                        }
                    },
                    { it, ret ->
                        it.after(ret.exceptionOrNull())
                        ret
                    },
                    ::action
            )
            fail("Should get an exception")
        } catch (t: Throwable) {
            assert(t is RuntimeException)
        }

        assertEquals(
                listOf("Before 1", "Before 2", "After 2 - exception:true", "After 1 - exception:true"),
                executedParts
        )
    }

    @Test
    fun exceptionInAfter() {
        try {
            withRules(
                    logger,
                    "",
                    "",
                    listOf(Rule(1), Rule(2), Rule(3)),
                    {
                        it.before()
                    },
                    { it, ret ->
                        it.after(ret.exceptionOrNull())
                        if (it.index == 2) {
                            throw RuntimeException()
                        }
                        ret
                    },
                    ::action
            )
            fail("Should get an exception")
        } catch (t: Throwable) {
            assert(t is RuntimeException)
        }

        assertEquals(
                listOf(
                        "Before 1", "Before 2", "Before 3",
                        "Action",
                        "After 3 - exception:false", "After 2 - exception:false", "After 1 - exception:true"),
                executedParts
        )
    }

    @Test
    fun exceptionInAction() {
        try {
            withRules(
                    logger,
                    "",
                    "",
                    listOf(Rule(1), Rule(2), Rule(3)),
                    {
                        it.before()
                    },
                    { it, ret ->
                        it.after(ret.exceptionOrNull())
                        ret
                    },
                    fun(): Unit {
                        action()
                        throw RuntimeException()
                    }
            )
            fail("Should get an exception")
        } catch (t: Throwable) {
            assert(t is RuntimeException)
        }

        assertEquals(
                listOf(
                        "Before 1", "Before 2", "Before 3",
                        "Action",
                        "After 3 - exception:true", "After 2 - exception:true", "After 1 - exception:true"),
                executedParts
        )
    }

    @Test
    fun multipleExceptionsAreSavedAsSuppressed() {
        val receivedExceptions = ArrayList<Throwable>()

        try {
            withRules(
                    logger,
                    "",
                    "",
                    listOf(Rule(1), Rule(2), Rule(3)),
                    {
                        it.before()
                    },
                    { it, ret ->
                        val e = ret.exceptionOrNull()

                        if (e != null) {
                            receivedExceptions.add(e)
                        }

                        it.after(e)

                        if (it.index >= 2) {
                            throw RuntimeException()
                        }
                        ret
                    },
                    fun(): Unit {
                        action()
                        throw RuntimeException()
                    }
            )
            fail("Should get an exception")
        } catch (t: Throwable) {
            assert(t is RuntimeException)
        }

        assertEquals(
                listOf(
                        "Before 1", "Before 2", "Before 3",
                        "Action",
                        "After 3 - exception:true", "After 2 - exception:true", "After 1 - exception:true"),
                executedParts
        )

        assert(receivedExceptions.size == 3)
        assert(receivedExceptions.all { it === receivedExceptions[0] })
        assert(receivedExceptions[0].suppressed.size == 2)
    }


    @Test
    fun happyExecution() {
        val out = withRules(
                logger,
                "",
                "",
                listOf(Rule(1), Rule(2), Rule(3)),
                {
                    it.before()
                },
                { it, ret ->
                    it.after(ret.exceptionOrNull())
                    Result.success(ret.getOrThrow() + it.index)
                },
                ::action
        )

        assertEquals(
                listOf(
                        "Before 1", "Before 2", "Before 3",
                        "Action",
                        "After 3 - exception:false", "After 2 - exception:false", "After 1 - exception:false"),
                executedParts
        )
        assertEquals("Success321", out)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RuleBlockTest::class.java)
    }
}