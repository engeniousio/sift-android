/*
 * Copyright 2019 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.suite

import com.android.ddmlib.Log
import com.android.ddmlib.logcat.LogCatMessage
import org.junit.Assert
import org.junit.Test

class TestInfoCatCollectorTest {
    @Test
    fun testSingleThread() {
        val finalResult = JUnitTestCaseProvider.decodeMessages(
            listOf(
                    LogCatMessage(Log.LogLevel.INFO, "0000-1001:{"),
                    LogCatMessage(Log.LogLevel.INFO, "0000-1002:\"a"),
                    LogCatMessage(Log.LogLevel.INFO, "0000-1003:\": 0"),
                    LogCatMessage(Log.LogLevel.INFO, "0000-1004:}")
            ).shuffled()
        ).toList()

        Assert.assertEquals(1, finalResult.size.toLong())
        val resultObject = finalResult[0]
        Assert.assertEquals(1, resultObject.entrySet().size.toLong())
        Assert.assertEquals(0, resultObject.get("a").asInt.toLong())
    }

    @Test
    fun testTwoThreadsAndOutOfOrder() {
        val finalResult = JUnitTestCaseProvider.decodeMessages(
                listOf(LogCatMessage(Log.LogLevel.INFO, "2000-3001:{"),
                    LogCatMessage(Log.LogLevel.INFO, "2000-3005:\": 1"),
                    LogCatMessage(Log.LogLevel.INFO, "2000-3003:\": 0,"),
                    LogCatMessage(Log.LogLevel.INFO, "2000-3002:\"a"),
                    LogCatMessage(Log.LogLevel.INFO, "2000-3004:\"b"),
                    LogCatMessage(Log.LogLevel.INFO, "2000-3006:}")
                ).shuffled()
        ).toList()

        Assert.assertEquals(1, finalResult.size.toLong())
        val resultObject = finalResult[0]
        Assert.assertEquals(2, resultObject.entrySet().size.toLong())
        Assert.assertEquals(0, resultObject.get("a").asInt.toLong())
        Assert.assertEquals(1, resultObject.get("b").asInt.toLong())
    }
}
