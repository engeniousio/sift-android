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
package com.github.tarcv.tongs.io

import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.IOException

class HtmlGenerator(private val templateFactory: Handlebars) {
    fun generateHtml(htmlTemplateResource: String, output: File, filename: String, vararg htmlModels: Any) {
        val template = templateFactory.compile(htmlTemplateResource)
        val context = buildCombinedContext(htmlModels)
        File(output, filename)
                .bufferedWriter(Charsets.UTF_8)
                .use { writer ->
                    template.apply(context, writer)
                }
    }

    fun generateHtmlFromInline(inlineTemplate: String, vararg htmlModels: Any): String {
        return try {
            val template = templateFactory.compileInline(inlineTemplate)
            val context = buildCombinedContext(htmlModels)
            template.apply(context)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private fun buildCombinedContext(htmlModels: Array<out Any>): Context? {
            var lastContext: Context? = null
            for (model in htmlModels) {
                lastContext = if (lastContext == null) {
                    Context.newContext(model)
                } else {
                    Context.newContext(lastContext, model)
                }
            }
            return lastContext
        }
    }

}