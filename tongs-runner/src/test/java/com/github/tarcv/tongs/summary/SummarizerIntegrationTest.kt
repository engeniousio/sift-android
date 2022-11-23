/*
 * Copyright 2021 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.summary

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.aConfigurationBuilder
import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.FileTableReportData
import com.github.tarcv.tongs.api.result.FileType
import com.github.tarcv.tongs.api.result.ImageReportData
import com.github.tarcv.tongs.api.result.LinkedFileReportData
import com.github.tarcv.tongs.api.result.SimpleHtmlReportData
import com.github.tarcv.tongs.api.result.SimpleMonoTextReportData
import com.github.tarcv.tongs.api.result.SimpleTableReportData
import com.github.tarcv.tongs.api.result.StandardFileTypes
import com.github.tarcv.tongs.api.result.Table
import com.github.tarcv.tongs.api.result.TestCaseFile
import com.github.tarcv.tongs.api.result.TestCaseFileManager
import com.github.tarcv.tongs.api.result.TestReportData
import com.github.tarcv.tongs.api.result.VideoReportData
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.koinRule
import com.github.tarcv.tongs.model.AndroidDevice
import com.github.tarcv.tongs.suite.ApkTestCase
import com.github.tarcv.tongs.system.io.FileManager
import com.github.tarcv.tongs.system.io.TestCaseFileManagerImpl
import com.github.tarcv.tongs.system.io.TongsFileManager
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import org.apache.commons.io.IOUtils
import org.hamcrest.CoreMatchers.startsWith
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.koin.core.context.KoinContextHandler
import org.koin.java.KoinJavaComponent.get
import java.io.File
import java.lang.reflect.Type
import java.nio.file.Paths

class SummarizerIntegrationTest {
    private val temporaryFolder = TemporaryFolder()

    @get:Rule
    val rules: TestRule = RuleChain.emptyRuleChain()
        .around(temporaryFolder)
        .around(koinRule { createConfiguration() })

    private val resourcedRoot = "/summarizerIntegration"
    private val linkedFilesSubDir = "linked"

    private val linkedFolderRoot by lazy {
        temporaryFolder.newFolder(linkedFilesSubDir)
    }
    private val fileManager by lazy {
        TongsFileManager(linkedFolderRoot)
    }

    private val gson = Summarizer.testRecorderGsonBuilder()
            .registerTypeAdapter(Device::class.java, ForceClassDeserializer(AndroidDevice::class.java))
            .registerTypeAdapter(TestCaseFileManager::class.java, TestCaseFileManagerDeserializer())
            .registerTypeAdapter(TestCase::class.java, TestCaseDeserializer())
            .registerTypeAdapter(TestReportData::class.java, TestReportDataDeserializer())
            .registerTypeAdapter(FileManager::class.java, ForceClassDeserializer(TongsFileManager::class.java))
            .registerTypeAdapter(FileType::class.java, ComplexEnumDeserializer(StandardFileTypes.values()))
            .create()

    @Test
    fun summarize() {
        initLinkedFolder()


        /*
              Preparation steps before adding new summarizeInputs.json to the repo:
              - all instances of path to tests\app directory is replaced with __ROOT_DIR__ for tests
              - all paths to logcat jsons are replaced with the one from
               com.github.tarcv.test.happy.DangerousNamesTest#test[param = |&;<>()$`?[]#~=%|&;<>()$`?[]#~=%|&;<>()$`?[]#~=%|&;<>()$`?[]#~=%|&;<>()$`?[]#~=%|&;<>()$`?[]#~=%|&;<>()$`?[]#~=%]
               (its name guarantees interesting HTML content)
             */

        val outcomeAggregator by KoinContextHandler.get().inject<OutcomeAggregator>()
        val summaryCompiler by KoinContextHandler.get().inject<SummaryCompiler>()
        val summaryPrinter  by KoinContextHandler.get().inject<SummaryPrinter>()
        val summarizer = Summarizer(
     get(Configuration::class.java), summaryCompiler, summaryPrinter,
     outcomeAggregator
 )
        SummarizerIntegrationTest::class.java.getResourceAsStream("/summarizerIntegration/summarizeInputs.json")
            .let { requireNotNull(it) { "Failed to read summary inputs" } }
            .bufferedReader().use {
            summarizer.summarizeFromRecordedJson(it, gson)
        }

        checkGeneratedFileTree()
    }

    private fun checkGeneratedFileTree() {
        val actualFilePaths = subDirectoriesPaths(temporaryFolder.root).sorted()

        val expectedResourcesRoot = "$resourcedRoot/expected"
        val commonResourcePathPrefix = "$expectedResourcesRoot/"
        val expectedFilePaths = subResourcesPaths(expectedResourcesRoot, "html")
                .map {
                    Assert.assertThat(it, startsWith(commonResourcePathPrefix))
                    it.removePrefix(commonResourcePathPrefix)
                }
                .sorted()
        Assert.assertEquals("All expected HTML report files are created", expectedFilePaths, actualFilePaths)
        checkFileContents(actualFilePaths, expectedResourcesRoot, temporaryFolder.root)
    }

    private fun initLinkedFolder() {
        subResourcesPaths(resourcedRoot, linkedFilesSubDir)
                .filterNot(Companion::isResourceAFolder)
                .forEach { resourcePath ->
                    val outFile = resourcePath
                            .removePrefix("$resourcedRoot/$linkedFilesSubDir")
                            .split("/")
                            .fold(linkedFolderRoot) { file, part ->
                                file.resolve(part)
                            }
                    outFile
                            .also {
                                it.parentFile.mkdirs()
                            }
                            .outputStream()
                            .use { fileOut ->
                                Companion::class.java.getResourceAsStream(resourcePath).use { resourceIn ->
                                    IOUtils.copyLarge(resourceIn, fileOut)
                                }
                            }
                }
    }

    private fun createConfiguration(): Configuration {
        return aConfigurationBuilder()
                .withApplicationPackage("com.github.tarcv.tongstestapp.f2")
                .withInstrumentationPackage("com.github.tarcv.tongstestapp.test")
                .withTestRunnerClass("android.support.test.runner.AndroidJUnitRunner")
                .withTestRunnerArguments(mapOf(
                        "test_argument" to "default",
                        "test_argument" to "args\"ForF2",
                        "filter" to "com.github.tarcv.test.F2Filter"
                ))
                .withOutput(temporaryFolder.root)
                .build(true)
    }

    internal class ComplexEnumDeserializer<T : Enum<*>>(val constants: Array<T>) : JsonDeserializer<T> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T {
            val enumName = json.asString
            return constants.single { it.name == enumName }
        }
    }

    private inner class TestReportDataDeserializer : JsonDeserializer<TestReportData> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TestReportData {
            val obj = json.asJsonObject
            val title = obj.get("title").asString
            return when {
                obj.has("html") -> SimpleHtmlReportData(title, obj.get("html").asString)
                obj.has("table") -> SimpleTableReportData(title, context.deserialize(obj.get("table"), Table::class.java))
                obj.has("tablePath") -> FileTableReportData(
                        title, context.deserialize(obj.get("tablePath"), TestCaseFile::class.java),
                        { tableFile ->
                            tableFile.bufferedReader().use {
                                gson.fromJson(it, Table.TableJson::class.java)
                            }
                        }
                )
                obj.has("image") -> ImageReportData(title, context.deserialize(obj.get("image"), TestCaseFile::class.java))
                obj.has("video") -> VideoReportData(title, context.deserialize(obj.get("video"), TestCaseFile::class.java))
                obj.has("file") -> LinkedFileReportData(title, context.deserialize(obj.get("file"), TestCaseFile::class.java))
                obj.has("monoText") -> SimpleMonoTextReportData(
                        title,
                        context.deserialize(obj.get("type"), SimpleMonoTextReportData.Type::class.java),
                        obj.get("monoText").asString
                )
                else -> throw IllegalStateException("Unknown TestReportData class")
            }
        }

    }

    private inner class TestCaseFileManagerDeserializer : JsonDeserializer<TestCaseFileManager> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TestCaseFileManager {
            val jsonObject = json.asJsonObject
            return TestCaseFileManagerImpl(
                    fileManager,
                    context.deserialize(jsonObject.get("pool"), Pool::class.java),
                    context.deserialize(jsonObject.get("device"), Device::class.java),
                    context.deserialize(jsonObject.get("testCaseEvent"), TestCase::class.java)
            )
        }

    }

    class TestCaseDeserializer : JsonDeserializer<TestCase> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TestCase {
            val jsonObject = json.asJsonObject

            // For backward compatibility with old JSON
            val typeTag = jsonObject.get("typeTag").let {
                if (it == null) {
                    ApkTestCase::class.java
                } else {
                    context.deserialize(it, Class::class.java)
                }
            }

            return TestCase(
                    typeTag,
                    jsonObject.get("testPackage").asString,
                    jsonObject.get("testClass").asString,
                    jsonObject.get("testMethod").asString,
                    context.deserialize(jsonObject.get("readablePath"), List::class.java),
                    context.deserialize(jsonObject.get("properties"), Map::class.java),
                    context.deserialize(jsonObject.get("annotations"), List::class.java),
                    null,
                    Any()
            )
        }

    }

    private class ForceClassDeserializer<T>(val forcedClass: Class<T>) : JsonDeserializer<T> {
        @Throws(JsonParseException::class)
        override fun deserialize(element: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T {
            return context.deserialize(element, forcedClass)
        }
    }

    companion object {
        private fun checkFileContents(relativeFilePaths: List<String>, expectedResourcesRoot: String, actualFilesRoot: File) {
            relativeFilePaths
                    .filter { it.endsWith("/").not() }
                    .map {
                        val aFile = Paths.get(actualFilesRoot.absolutePath, *it.split("/").toTypedArray())
                                .toFile()
                        val aResource = "$expectedResourcesRoot/$it"
                        aFile to aResource
                    }
                    .forEach { (aFile, aResource) ->
                        val actualText = aFile.readLines().joinToString(System.lineSeparator())
                        val expectedBody = Companion::class.java.getResourceAsStream(aResource)
                                .let { requireNotNull(it) { "Failed to read $aResource" } }
                                .bufferedReader()
                                .use{ it.readLines() }
                                .joinToString(System.lineSeparator())
                        Assert.assertFalse("${aFile.path} should not contain unresolved symbols",
                                actualText.contains(DefaultHelper.placeholderForUnresolvedSymbols))

                        Assert.assertEquals("${aFile.path} should have expected contents",
                                expectedBody, actualText)
                    }
        }

        private fun subDirectoriesPaths(base: File): List<String> {
            return base
                    .walkTopDown().toSortedSet()
                    .filter { it.relativeTo(base).invariantSeparatorsPath.startsWith("html") }
                    .map {
                        val relativePath = it.relativeTo(base).invariantSeparatorsPath
                        if (it.isDirectory) {
                            "$relativePath/"
                        } else {
                            relativePath
                        }
                    }
        }

        private fun subResourcesPaths(parentPath: String, itemName: String): List<String> {
            val basePath = "$parentPath/$itemName"
            val firstFileStream = isResourceAFolder(basePath)
            if (firstFileStream) {
                val output = ArrayList<String>().apply {
                    add("$basePath/")
                }
                Companion::class.java.getResourceAsStream("$basePath/")
                        .let { requireNotNull(it) { "Failed to read $basePath/" } }
                        .bufferedReader()
                        .lineSequence()
                        .flatMap {
                            subResourcesPaths(basePath, it).asSequence()
                        }
                        .toCollection(output)
                return output
            } else {
                return listOf(basePath)
            }
        }

        private fun isResourceAFolder(path: String): Boolean {
            val firstFile = Companion::class.java.getResourceAsStream("$path/")
                    .let { requireNotNull(it) { "Failed to read $path/" } }
                    .bufferedReader()
                    .readLine()
            val firstFileStream = Companion::class.java.getResourceAsStream("$path/$firstFile")
            return if (firstFileStream != null) {
                firstFileStream.close()
                true
            } else {
                false
            }
        }
    }
}
