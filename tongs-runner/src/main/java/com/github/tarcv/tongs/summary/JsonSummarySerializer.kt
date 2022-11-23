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
package com.github.tarcv.tongs.summary

import com.github.tarcv.tongs.system.io.FileManager
import com.google.gson.Gson
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.IOException

class JsonSummarySerializer(private val fileManager: FileManager, private val gson: Gson) : SummaryPrinter {
    override fun print(summary: Summary) {
        try {
            logger.info("summary title ${summary.title}")
            logger.info("summary subtitle ${summary.subtitle}")
            logger.info("summary allTests ${summary.allTests}")
            logger.info("summary poolSummaries ${summary.poolSummaries}")
            logger.info("summary failedTests ${summary.failedTests}")
            logger.info("summary flakyTests ${summary.flakyTests}")
            logger.info("summary fatalCrashedTests ${summary.fatalCrashedTests}")
            logger.info("summary ignoredTests ${summary.ignoredTests}")
            fileManager.createSummaryFile()
                    .bufferedWriter(Charsets.UTF_8)
                    .use { writer ->
                        gson.toJson(summary, writer)
                        writer.flush()
                    }
        } catch (e: IOException) {
            logger.error("Could not serialize the summary.", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JsonSummarySerializer::class.java)
    }
}