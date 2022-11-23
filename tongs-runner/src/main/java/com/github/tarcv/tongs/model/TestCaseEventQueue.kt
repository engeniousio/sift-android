/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.model

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.TestCaseEvent
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TestCaseEventQueue(
        events: Collection<TestCaseEvent>,
        resultsCollection: MutableList<TestCaseRunResult>
) {
    private val list = ArrayList<TestCaseEvent>(events)
    private val syncResultsCollection = Collections.synchronizedList(resultsCollection)

    private val conditionLock = ReentrantLock()
    private val newItemCondition = conditionLock.newCondition()

    private val numEventsInWork = AtomicInteger()

    fun pollForDevice(device: Device, timeoutSeconds: Long = 0): TestCaseTask? {
        val currentTime = System.currentTimeMillis()
        val timeoutTime = currentTime + timeoutSeconds * 1000
        while (true) {
            conditionLock.withLock {
                val item = tryPollForDevice(device)
                if (item != null) {
                    return TestCaseTask(item)
                }

                newItemCondition.await(1, TimeUnit.SECONDS)
                if (timeoutSeconds > 0 && timeoutTime < System.currentTimeMillis()) {
                    return null
                }
            }
        }
    }

    fun hasNoPotentialEventsFor(device: Device): Boolean {
        conditionLock.withLock {
            return indexOfEventFor(device) == -1 && numEventsInWork.get() == 0
        }
    }

    fun offer(event: TestCaseEvent) {
        conditionLock.withLock {
            if (numEventsInWork.get() < 1) {
                throw IllegalStateException("TestCaseEventQueue.offer can only be called during TestCaseTask.doWork")
            }
            list.add(event)

            newItemCondition.signalAll()
        }
    }

    private fun tryPollForDevice(device: Device): TestCaseEvent? {
        return conditionLock.withLock {
            val itemIndex = indexOfEventFor(device)
            if (itemIndex != -1) {
                list.removeAt(itemIndex)
            } else {
                null
            }
        }
    }

    private fun indexOfEventFor(device: Device) = list.indexOfFirst { it.isEnabledOn(device) }

    inner class TestCaseTask(private val testCaseEvent: TestCaseEvent) {
        fun doWork(block: (testCaseEvent: TestCaseEvent) -> TestCaseRunResult) {
            try {
                numEventsInWork.incrementAndGet()
                val testCaseResult = block.invoke(testCaseEvent)
                syncResultsCollection.add(testCaseResult)
            } finally {
                val result = numEventsInWork.decrementAndGet()
                if (result < 0) {
                    throw IllegalStateException()
                }
            }
        }
    }
}