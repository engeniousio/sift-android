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
package com.github.tarcv.tongs.summary

import com.github.tarcv.tongs.api.result.MonoTextReportData
import com.github.tarcv.tongs.api.result.SimpleMonoTextReportData
import com.github.tarcv.tongs.api.result.SimpleMonoTextReportData.Type.STDOUT
import com.github.tarcv.tongs.api.result.SimpleMonoTextReportData.Type.STRERR
import com.github.tarcv.tongs.api.result.StackTrace
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.ResultStatus
import org.apache.commons.lang3.StringEscapeUtils
import java.io.File
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField

class XmlResultWriter() {
    fun writeXml(xmlFile: File, result: TestCaseRunResult) {
        val predefinedProps = mapOf(
                "pool" to result.pool.name,
                "device" to "${result.device.modelName} - ${result.device.osApiLevel}",
                "deviceId" to result.device.serial,
                "totalFailureCount" to result.totalFailureCount.toString()
        )
        val stdOut = compileStdOut(result, STDOUT)
        val stdErr = compileStdOut(result, STRERR)

        writeXml(
                xmlFile,
                result.status,
                result.device.host,
                result.testCase.testClass,
                result.testCase.testMethod,
                predefinedProps + result.additionalProperties,
                result.stackTraces,
                stdOut,
                stdErr,
                result.startTimestampUtc,
                result.timeTakenSeconds.toDouble(),
                result.timeNetTakenSeconds?.toDouble() ?: 0.0
        )
    }

    private fun compileStdOut(result: TestCaseRunResult, type: SimpleMonoTextReportData.Type): String {
        return result.data
                .filterIsInstance<MonoTextReportData>()
                .filter { it.type == type }
                .joinToString(System.lineSeparator()) { it.monoText }
    }

    companion object {
        private val timeFormatter = DateTimeFormatterBuilder()
                .appendValue(ChronoField.HOUR_OF_DAY, 2)
                .appendLiteral(':')
                .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                .optionalStart()
                .appendLiteral(':')
                .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                .toFormatter()
                .withChronology(IsoChronology.INSTANCE)
                .withResolverStyle(ResolverStyle.STRICT)
        private val dateTimeFormatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral('T')
                .append(timeFormatter)
                .toFormatter()
                .withChronology(IsoChronology.INSTANCE)
                .withResolverStyle(ResolverStyle.STRICT)
                .withZone(ZoneId.of("UTC"))
        private val secondsFormatter = DecimalFormat().apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 3
        }

        // TODO: write fuzzing tests for this method
        internal fun writeXml(
                file: File,
                resultStatus: ResultStatus,
                host: String,
                testClass: String,
                testCaseMethod: String,
                properties: Map<String, String>,
                stackTraces: List<StackTrace>,
                stdOut: String,
                stdErr: String,
                startTimestampUtc: Instant,
                totalTime: Double,
                netTime: Double
        ) {
            val escapedProperties = properties
                    .map { StringEscapeUtils.escapeXml10(it.key) to StringEscapeUtils.escapeXml10(it.value) }
                    .toMap()
            internalWriteXml(
                    file,
                    resultStatus,
                    StringEscapeUtils.escapeXml10(host),
                    StringEscapeUtils.escapeXml10(testClass),
                    StringEscapeUtils.escapeXml10(testCaseMethod),
                    escapedProperties,
                    stackTraces,
                    StringEscapeUtils.escapeXml10(stdOut),
                    StringEscapeUtils.escapeXml10(stdErr),
                    StringEscapeUtils.escapeXml10(dateTimeFormatter.format(startTimestampUtc)),
                    StringEscapeUtils.escapeXml10(secondsFormatter.format(totalTime)),
                    StringEscapeUtils.escapeXml10(secondsFormatter.format(netTime))
            )
        }

        private fun internalWriteXml(
                file: File,
                resultStatus: ResultStatus,
                host: String,
                testClass: String,
                testCaseMethod: String,
                properties: Map<String, String>,
                stackTraces: List<StackTrace>,
                stdOut: String,
                stdErr: String,
                startTimestampUtc: String,
                totalTime: String,
                netTime: String
        ) {

            data class ResultTuple(val resultName: String?, val errors: Int, val failures: Int, val skipped: Int)
            val (resultType, errors, failures, skipped) = when (resultStatus) {
                ResultStatus.PASS -> ResultTuple(null, 0, 0, 0)
                ResultStatus.FAIL -> ResultTuple("failure", 0, 1, 0)
                ResultStatus.ERROR -> ResultTuple("error", 1, 0, 0)
                ResultStatus.IGNORED, ResultStatus.ASSUMPTION_FAILED -> ResultTuple("skipped", 0, 0, 1)
            }

            file
                    .bufferedWriter(Charsets.UTF_8)
                    .use {
                        it.write(("""<?xml version='1.0' encoding='UTF-8' ?>
                        |<testsuite name="$testClass" tests="1" failures="$failures" errors="$errors" """ +
                                """skipped="$skipped" time="$totalTime" timestamp="$startTimestampUtc" hostname="$host">
                        |${propertiesToStrings("  ", properties)}
                        |${testCaseToString("  ", resultType, stackTraces, testCaseMethod, testClass, netTime)}
                        |<system-out>$stdOut</system-out>
                        |<system-err>$stdErr</system-err>
                        |</testsuite>""").trimMargin())
                    }
        }

        private fun testCaseToString(
                indent: String,
                resultType: String?,
                stackTraces: List<StackTrace>,
                testCaseMethod: String,
                testClass: String,
                netTime: String
        ): String {
            val testCaseBlock = if (resultType != null) {
                val result = stackTraces.joinToString(System.lineSeparator()) {
                    val typeAttribute = """ type="${StringEscapeUtils.escapeXml10(it.errorType)}""""
                    val messageAttribute = if (it.errorMessage.isEmpty()) {
                        ""
                    } else {
                        """ message="${StringEscapeUtils.escapeXml10(it.errorMessage)}""""
                    }
                    val trace = StringEscapeUtils.escapeXml10(it.fullTrace)
                    """${indent}  <$resultType$messageAttribute$typeAttribute>$trace</$resultType>"""
                }
                """${indent}<testcase name="$testCaseMethod" classname="$testClass" time="$netTime">
                        |${result}
                        |</testcase>""".trimMargin()
            } else {
                """${indent}<testcase name="$testCaseMethod" classname="$testClass" time="$netTime" />"""
            }
            return testCaseBlock
        }

        private fun propertiesToStrings(indent: String, properties: Map<String, String>): String {
            val propertiesStr = if (properties.isEmpty()) {
                "${indent}<properties/>"
            } else {
                properties
                        .map { "${indent}  <property name=\"${it.key}\" value=\"${it.value}\" />" }
                        .joinToString(
                                System.lineSeparator(),
                                "${indent}<properties>" + System.lineSeparator(),
                                System.lineSeparator() + "${indent}</properties>"
                        )
            }
            return propertiesStr
        }
    }
}