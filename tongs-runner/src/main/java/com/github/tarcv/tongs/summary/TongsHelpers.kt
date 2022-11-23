/*
 * Copyright 2020 TarCV
 * Copyright 2015 Shazam Entertainment Limited
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

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.TagType
import com.github.tarcv.tongs.api.result.StandardFileTypes
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.system.io.FileUtils
import org.apache.commons.lang3.text.WordUtils.capitalizeFully
import org.slf4j.LoggerFactory
import java.nio.file.Paths

public enum class TongsHelpers: Helper<Any> {
    size {
        override fun apply(context: Any, options: Options): Any? {
            return if (context is Collection<*>) {
                context.size
            } else if (context is Map<*, *>) {
                context.size
            } else {
                throw RuntimeException("'size' helper is only applicable to collections")
            }
        }
    },
    unixPath {
        override fun apply(context: Any, options: Options): Any? {
            return Paths.get(context.toString()).joinToString("/")
        }
    },
    simpleClassName {
        override fun apply(context: Any, options: Options): Any? {
            return context.toString()
                    .split('.')
                    .last()
        }
    },
    readableMethodName {
        private val testPattern = Regex("(?:^test(?=\\W))|(?:(?=\\W)test$)")

        override fun apply(context: Any, options: Options): Any? {
            return context.toString()
                    .replace("(\\p{Ll})(\\p{Lu})".toRegex(), "$1 $2")
                    .replace("(\\d)(\\p{IsAlphabetic})".toRegex(), "$1 $2")
                    .replace("(\\p{IsAlphabetic})(\\d)".toRegex(), "$1 $2")
                    .let {
                        if (it.equals("test", ignoreCase = true)) {
                            it
                        } else {
                            it.replaceFirst(testPattern, "")
                        }
                    }
                    .replace("_", " ")
                    .trim()
                    .let { capitalizeFully(it) }
        }
    },
    filenameForTest { // TODO: Add a test case for this helper
        override fun apply(context: Any, options: Options?): Any {
            return when (context) {
                is TestCaseRunResult -> createFileName(context.testCase)
                is TestCase -> createFileName(context)
                else -> throw IllegalStateException("filenameForTest is not supported for a ${context::class.java.simpleName}")
            }
        }

        private fun createFileName(testCase: TestCase): String {
            return FileUtils.createFilenameForTest(testCase, StandardFileTypes.DOT_WITHOUT_EXTENSION)
        }

    }
    ;

    companion object {
        @JvmStatic
        fun register(handlebars: Handlebars) {
            for (helper in values()) {
                handlebars.registerHelper(helper.name, helper)
            }
            handlebars.registerHelperMissing(DefaultHelper(handlebars))
        }
    }
}

class DefaultHelper(private val handlebars: Handlebars) : Helper<Any> {
    companion object {
        const val placeholderForUnresolvedSymbols = "[N/A]"
        private val logger = LoggerFactory.getLogger(TongsHelpers::class.java)
    }

    override fun apply(context: Any?, options: Options): Any? {
        return when {
            options.tagType == TagType.VAR -> {
                logAndOutputPlaceholder(options)
            }
            options.tagType == TagType.SECTION -> {
                val defaultHelper = handlebars.helper<Any>(options.helperName)
                if (defaultHelper == null || defaultHelper is DefaultHelper) {
                    logAndOutputPlaceholder(options)
                } else {
                    // hieuristic to avoid false negatives like '{#optionalKey}'
                    defaultHelper.apply(context, options)
                }
            }
            else -> {
                logAndOutputPlaceholder(options)
            }
        }
    }

    private fun logAndOutputPlaceholder(options: Options): String {
        logger.warn("Got missing value in a report template (${options.fn})")
        return placeholderForUnresolvedSymbols
    }
}