/*
 * Copyright 2020 TarCV
 * Copyright 2015 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.api.run

enum class ResultStatus(private val overrideOrder: Int) {
    // TODO: Make names match JUnit terminology
    PASS(0),
    FAIL(9),
    ERROR(10),
    IGNORED(-2),
    ASSUMPTION_FAILED(-1);

    fun overrideCompareTo(other: ResultStatus): Int = overrideOrder.compareTo(other.overrideOrder)

    companion object {
        @JvmStatic
        fun isFailure(status: ResultStatus): Boolean {
            return status !in listOf(PASS, IGNORED, ASSUMPTION_FAILED)
        }

        @JvmStatic
        fun isIgnored(status: ResultStatus): Boolean {
            return status in listOf(IGNORED, ASSUMPTION_FAILED)
        }
    }
}