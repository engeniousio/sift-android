/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.util

import com.github.tarcv.tongs.api.result.StackTrace

fun parseJavaTrace(trace: String): StackTrace {
    val firstLine = trace.lines().first()
    val traceHeaderRegex = Regex("""^(?:([A-Za-z_${'$'}][A-Za-z0-9_.]*):\s+)?(.*)$""")
    val match = traceHeaderRegex.matchEntire(firstLine)
    return if (match != null && match.groupValues[1].isNotBlank()) {
        val (errorType, errorMessage) = match.destructured
        StackTrace(errorType, errorMessage, trace)
    } else {
        StackTrace("", "", trace)
    }
}
