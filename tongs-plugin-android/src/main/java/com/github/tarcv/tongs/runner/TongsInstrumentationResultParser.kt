/*
 * Copyright 2020 TarCV
 * Based on portions of code from DdmLib which are Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.runner

import com.android.ddmlib.Log
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.InstrumentationResultParser
import com.github.tarcv.tongs.runner.listeners.BroadcastingListener
import java.lang.RuntimeException
import java.lang.reflect.InvocationTargetException

class TongsInstrumentationResultParser(
        runName: String,
        listeners: Collection<ITestRunListener>
) : InstrumentationResultParser(runName, PatchingListener(listeners)) {
    // TODO: Consider forking DdmLib to patch this class more cleanly

    private val streamBuilder = StringBuilder()
    private var insideStream: Boolean = false

    init {
        this.privateFieldValue<InstrumentationResultParser, List<ITestRunListener>>("mTestListeners")
                .get(0)
                .let {
                    (it as PatchingListener).streamBuilder = streamBuilder
                }
    }

    override fun processNewLines(lines: Array<out String>) {
        // From com.android.ddmlib.testrunner.InstrumentationResultParser.processNewLines
        for (line in lines) {
            parse(line)

            // in verbose mode, dump all adb output to log
            Log.v(LOG_TAG, line)
        }
    }

    private fun parse(line: String) {
        val linePrefix = knownPrefixes.firstOrNull { line.startsWith(it) }
        if (linePrefix != null) {
            // section finished, so reset anything related to reading stream
            insideStream = false

            tryParseAsStreamStart(linePrefix, line)
        } else if (insideStream) {
            streamBuilder.appendln(line)
        }

        try {
            parseMethod.invoke(this, line)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun tryParseAsStreamStart(linePrefix: String, line: String) {
        if (linePrefix == statusPrefix || linePrefix == resultPrefix) {
            val otherPart = line.removePrefix(linePrefix)
            val parts = otherPart.split(Regex("="), 2)
            if (parts.size == 2 && parts[0].trim().startsWith(streamKey)) {
                insideStream = true
                streamBuilder.append(parts[1])
            }
        }
    }

    class PatchingListener(targetListeners: Collection<ITestRunListener>) : BroadcastingListener(targetListeners) {
        internal lateinit var streamBuilder: StringBuilder

        override fun testRunStarted(runName: String?, testCount: Int) {
            super.testRunStarted(runName, testCount)
        }

        override fun testRunEnded(elapsedTime: Long, output: String, runMetrics: Map<String, String>?) {
            assert(output.isEmpty()) // Call through ITestRunListener#testRunEnded brings just empty string here

            super.testRunEnded(elapsedTime, streamBuilder.toString(), runMetrics)
        }
    }

    companion object {
        private val LOG_TAG: String = InstrumentationResultParser::class.java.privateStaticFieldValue("LOG_TAG")

        private val prefixesClass = InstrumentationResultParser::class.java.privateStaticClass("Prefixes")

        private val knownPrefixes = prefixesClass
                .declaredFields
                .filter { !it.isSynthetic }
                .map {
                    try {
                        it.isAccessible = true
                        it.get(null) as String
                    } catch (t: Throwable) {
                        throw RuntimeException("Failed to read ${it.name}", t)
                    }
                }
                .filter {
                    // filter out pseudo prefixes that still part of the stream,
                    // and thus should not stop reading it
                    it == it.toUpperCase()
                }

        private val statusPrefix: String = prefixesClass.privateStaticFieldValue("STATUS")
        private val resultPrefix: String = prefixesClass.privateStaticFieldValue("RESULT")

        private val streamKey: String = InstrumentationResultParser::class.java.privateStaticFieldValue("STREAM")

        private val parseMethod = InstrumentationResultParser::class.java
                .declaredMethods
                .single { it.name == "parse" }
                .apply {
                    isAccessible = true
                }

        private fun prepareTraceForComparison(trace: String): String {
            return trace
                    .trim()
                    .lineSequence()
                    .map { it.trim() }
                    .joinToString(System.lineSeparator())
        }

        private inline fun <reified T> Class<*>.privateStaticFieldValue(name: String): T {
            return this.declaredFields
                    .single { it.name == name }
                    .apply {
                        isAccessible = true
                    }
                    .get(null) as T
        }

        private inline fun <reified C, reified T> C.privateFieldValue(name: String): T {
            val foundFields = C::class.java
                    .declaredFields
            return foundFields
                    .single { it.name == name }
                    .apply {
                        isAccessible = true
                    }
                    .get(this) as T
        }

        private fun Class<*>.privateStaticClass(name: String): Class<*> {
            return this.declaredClasses
                    .single { it.simpleName == name }
        }
    }
}