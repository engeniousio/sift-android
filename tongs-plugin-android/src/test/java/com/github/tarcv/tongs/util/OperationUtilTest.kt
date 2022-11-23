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
package com.github.tarcv.tongs.util

import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class OperationUtilTest {
    @Test
    fun repeatUntilSuccessfulExitsOnFirstSuccess() {
        val counter = AtomicInteger()
        repeatUntilSuccessful<RuntimeException>() {
            counter.incrementAndGet()
        }
        Assert.assertEquals(1, counter.get())
    }

    @Test
    fun repeatUntilSuccessfulExitsOnFirstSuccess_DifferentIteration() {
        val counter = AtomicInteger()
        repeatUntilSuccessful<RuntimeException>() {
            if (counter.incrementAndGet() < 3) {
                throw RuntimeException()
            }
        }
        Assert.assertEquals(3, counter.get())
    }

    @Test
    fun repeatUntilSuccessfulThrowsOnFirstWrongException() {
        val counter = AtomicInteger()
        val result = kotlin.runCatching {
            repeatUntilSuccessful<RuntimeException>() {
                counter.incrementAndGet()
                throw IOException()
            }
        }
        Assert.assertEquals(IOException::class.java, result.exceptionOrNull()?.javaClass)
        Assert.assertEquals(1, counter.get())
    }

    @Test
    fun repeatUntilSuccessfulThrowsOnFirstWrongException_DifferentIteration() {
        val counter = AtomicInteger()
        val result = kotlin.runCatching {
            repeatUntilSuccessful<RuntimeException>() {
                if (counter.incrementAndGet() < 3) {
                    throw RuntimeException()
                } else {
                    throw IOException()
                }
            }
        }
        Assert.assertEquals(IOException::class.java, result.exceptionOrNull()?.javaClass)
        Assert.assertEquals(3, counter.get())
    }

    @Test
    fun repeatUntilSuccessfulThrowsOnAllFailures() {
        val counter = AtomicInteger()
        val result = kotlin.runCatching {
            repeatUntilSuccessful<RuntimeException>() {
                counter.incrementAndGet()
                throw RuntimeException()
            }
        }
        Assert.assertEquals(RuntimeException::class.java, result.exceptionOrNull()?.javaClass)
        Assert.assertEquals(5, counter.get())
    }

    @Test
    fun repeatUntilSuccessfulRepeatsUntilSuccess() {
        val counter = AtomicInteger()
        repeatUntilSuccessful<RuntimeException>() {
            if (counter.incrementAndGet() < 3) {
                throw RuntimeException()
            }
        }
        Assert.assertEquals(3, counter.get())
    }
}