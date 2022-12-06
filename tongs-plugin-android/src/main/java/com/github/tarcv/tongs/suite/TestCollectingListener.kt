/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.suite

import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.TestIdentifier
import java.util.*
import javax.annotation.concurrent.GuardedBy

/**
 * Records identifiers of started tests
 */
class TestCollectingListener : ITestRunListener {
    private val lock = Any()

    @GuardedBy("lock")
    private val tests = HashSet<TestIdentifier>()

    @GuardedBy("lock")
    private var lastFailure: String? = null

    val result: Result
        get() = synchronized(lock) {
            lastFailure.let {
                if (it == null) {
                    Result.Successful(Collections.unmodifiableSet(HashSet(tests)))
                } else {
                    Result.Failed(it)
                }
            }
        }

    override fun testStarted(test: TestIdentifier) {
        synchronized(lock) {
            tests.add(test)
        }
    }

    override fun testIgnored(test: TestIdentifier) {}
    override fun testRunStarted(runName: String, testCount: Int) {}
    override fun testFailed(test: TestIdentifier, trace: String) {}
    override fun testAssumptionFailure(test: TestIdentifier, trace: String) {}
    override fun testEnded(test: TestIdentifier, testMetrics: Map<String, String>) {}
    override fun testRunFailed(errorMessage: String) {
        synchronized(lock) {
            lastFailure = errorMessage
        }
    }
    override fun testRunStopped(elapsedTime: Long) {}
    override fun testRunEnded(elapsedTime: Long, runMetrics: Map<String, String>?) {}

    sealed class Result {
        class Successful(val tests: Set<TestIdentifier>): Result()
        class Failed(val lastFailure: String): Result()
    }
}