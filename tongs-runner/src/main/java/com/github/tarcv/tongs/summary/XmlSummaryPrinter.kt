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

import com.github.tarcv.tongs.system.io.FileManager
import com.github.tarcv.tongs.api.result.StandardFileTypes
import java.io.File

class XmlSummaryPrinter(
        private val outputDir: File,
        private val fileManager: FileManager,
        private val xmlResultWriter: XmlResultWriter
) : SummaryPrinter {
    override fun print(summary: Summary) {
        for (poolSummary in summary.poolSummaries) {
            for (result in poolSummary.testResults) {
                val file = fileManager.createFile(StandardFileTypes.TEST, result.pool, result.device, result.testCase)
                xmlResultWriter.writeXml(file, result)
            }
        }
    }
}