/*
 * Copyright 2022 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.suite

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.logcat.LogCatMessage
import com.android.ddmlib.testrunner.TestIdentifier
import com.github.tarcv.tongs.api.testcases.NoTestCasesFoundException
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.TestCaseProvider
import com.github.tarcv.tongs.api.testcases.TestCaseProviderContext
import com.github.tarcv.tongs.device.clearLogcat
import com.github.tarcv.tongs.model.AndroidDevice
import com.github.tarcv.tongs.runner.AndroidTestRunFactory
import com.github.tarcv.tongs.runner.JsonInfoDecorder
import com.github.tarcv.tongs.runner.TestInfo
import com.github.tarcv.tongs.runner.listeners.LogcatReceiver
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

class JUnitTestCaseProvider(
    private val context: TestCaseProviderContext,
    private val testRunFactory: AndroidTestRunFactory,
    private val apkTestInfoReader: ApkTestInfoReader
) : TestCaseProvider {
    private val logger = LoggerFactory.getLogger(JUnitTestCaseProvider::class.java)

    companion object {
        const val logcatWaiterSleep: Long = 2500
        private val jsonInfoDecoder = JsonInfoDecorder()

        fun calculateDeviceIncludes(input: Sequence<Pair<AndroidDevice, Set<TestIdentifier>>>)
                : Map<TestIdentifier, List<AndroidDevice>> {
            return input
                    .flatMap { (device, tests) ->
                        tests
                                .asSequence()
                                .map { Pair(it, device) }
                    }
                    .groupBy({ it.first }) {
                        it.second
                    }
        }

        fun decodeMessages(testInfoMessages: Collection<LogCatMessage>): List<JsonObject> {
            class MessageKey(val id: String, val lineIndex: String) : Comparable<MessageKey> {

                override fun compareTo(other: MessageKey): Int {
                    return id.compareTo(other.id)
                            .let {
                                if (it == 0) {
                                    lineIndex.compareTo(other.lineIndex)
                                } else {
                                    it
                                }
                            }
                }

                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as MessageKey

                    if (id != other.id) return false
                    if (lineIndex != other.lineIndex) return false

                    return true
                }

                override fun hashCode(): Int {
                    var result = id.hashCode()
                    result = 31 * result + lineIndex.hashCode()
                    return result
                }
            }

            val jsonParser = JsonParser()
            return testInfoMessages.asSequence()
                    .map { it.message }
                    .fold(ArrayList<Pair<MessageKey, String>>()) { acc, message ->
                        val (prefix, line) = message.split(':', limit = 2)
                        val (id, lineIndex) = prefix.split('-', limit = 2)

                        acc.apply {
                            acc.add(MessageKey(id, lineIndex) to line)
                        }
                    }
                    .groupBy({ it.first.id })
                    .values
                    .flatMap { pair ->
                        pair
                                .sortedBy { it.first.lineIndex.toInt(16) }
                                .map { it.second }
                                .let { arrayContent ->
                                    val json = arrayContent.joinToString("", "[", "]")
                                    jsonParser
                                            .parse(json)
                                            .asJsonArray
                                            .asSequence()
                                            .filter { !it.isJsonNull }
                                            .map { it.asJsonObject }
                                            .toList()
                                }
                    }
        }

    }

    @Throws(NoTestCasesFoundException::class)
    override fun loadTestSuite(): Collection<TestCase> = runBlocking {
        context.pool.devices
                .filterIsInstance(AndroidDevice::class.java) // TODO: handle other types of devices
                .map { device ->
                    logger.info("LIST loadTestSuite deviceName - ${device.name}")
                    async {
                        kotlin.runCatching {
                            try {
                                collectTestsFromLogOnlyRun(device)
                            } catch (e: InterruptedException) {
                                throw e
                            } catch (e: Exception) {
                                logger.warn("Didn't collect test cases from ${device.name}", e)
                                throw e
                            }
                        }
                    }
                }
                .awaitAll()
                .let { collectedInfoResults ->
                    val collectedInfos = collectedInfoResults.mapNotNull { it.getOrNull() }
                    if (collectedInfos.isEmpty()) {
                        val lastCause = if (collectedInfoResults.isEmpty()) {
                            null
                        } else {
                            collectedInfoResults.last().exceptionOrNull()
                        }
                        logger.warn("Didn't collect any test cases using Android Debug Bridge", lastCause)
                        return@let emptyList<TestCase>()
                    }

                    collectedInfos.forEach {
                        if (!it.hasOnDeviceLibrary) {
                            logger.warn("Instrumented tests on ${it.device} are linked without 'ondevice' library." +
                                    " Some tests might be NOT executed.")
                        }
                        it.device.setHasOnDeviceLibrary(it.hasOnDeviceLibrary)
                    }
                    val hasOnDeviceLibrary = collectedInfos.any { it.hasOnDeviceLibrary }

                    val annotationInfos = if (!hasOnDeviceLibrary) {
                        logger.warn("It seems '-ondevice' dependency is missing on all devices in ${context.pool.name}." +
                                "Falling back to getting annotation data from bytecode in the instrumentation APK (such data will not be 100% accurate).")

                        val allTests = collectedInfos.asSequence()
                                .map { it.tests }
                                .reduce { acc, set -> acc + set }
                        val instrumentationApk = context.configuration.instrumentationApk.let {
                            if (it == null) {
                                logger.info("Path to the instrumentation APK is not specified. Trying to pull it from a device")
                                try {
                                    withContext(Dispatchers.IO) {
                                        pullTestApkFromDevice(context.pool.devices.first() as AndroidDevice)
                                    }
                                } catch (e: Exception) {
                                    // TODO:
                                    throw RuntimeException("Failed to pull the instrumentation APK from a device", e)
                                }
                            } else {
                                it
                            }
                        }
                        val testInfo = apkTestInfoReader.readTestInfo(
                                instrumentationApk,
                                allTests
                        )

                        testInfo.associateBy { it.identifier }
                    } else {
                        collectedInfos
                                .asSequence()
                                .flatMap { it.infoMessages.entries.asSequence() }
                                .associateBy({ it.key }) {
                                    it.value
                                }
                    }
                    finalizeTestInformation(collectedInfos, annotationInfos)
                }
    }

    private fun pullTestApkFromDevice(device: AndroidDevice): File {
        val adb = (device).deviceInterface
        val pathReceiver = CollectingOutputReceiver()
        adb.executeShellCommand(
                "pm path ${context.configuration.instrumentationPackage}",
                pathReceiver
        )
        val devicePath = pathReceiver.output
                .trim()
                .removePrefix("package:")
                // some devices output '=com.package.test' at the end of this line:
                .replaceFirst(Regex("""=[\w.]+$"""), "")
        val localPath = Files.createTempDirectory("tongs").resolve("test.apk")
        adb.pullFile(devicePath, localPath.toString())
        return localPath.toFile()
    }

    private fun finalizeTestInformation(collectedInfos: List<CollectedInfo>, annotationInfos: Map<TestIdentifier, TestInfo>): Collection<TestCase> {
        val devicesInfo = collectedInfos
                .asSequence()
                .map { it.device to it.tests }
                .let { calculateDeviceIncludes(it) }

        val allTests = devicesInfo.keys

        val testsWithoutInfo = (allTests - annotationInfos.keys)
            .filterNot {
                // this is a synthetic test name meaning something crashed while collecting the list of tests
                it.testName == "initializationError"
            }

        if (testsWithoutInfo.isNotEmpty()) {
            logger.warn(
                    "In pool ${context.pool.name} received no annotation information" +
                            " for ${testsWithoutInfo.joinToString(", ")}"
            )
        }

        return annotationInfos
                .map { (identifier, info) ->
                    TestCase(
                            ApkTestCase::class.java,
                            info.`package`,
                            identifier.className,
                            identifier.testName,
                            info.readablePath,
                            emptyMap(),
                            info.annotations,
                            devicesInfo[identifier]?.toSet()
                    )
                }
    }

    private suspend fun collectTestsFromLogOnlyRun(device: AndroidDevice): CollectedInfo {
        var hasOnDeviceLibrary = true
        var collectionResult = collectTestData(device, hasOnDeviceLibrary)

        collectionResult.second.let {
            if (it is TestCollectingListener.Result.Failed) {
                logger.warn("Failed to collect list of tests using 'ondevice' library," +
                        " retrying without the library." +
                        " Error is ${it.lastFailure}")

                hasOnDeviceLibrary = false
                collectionResult = collectTestData(device, hasOnDeviceLibrary)
            }
        }

        val deviceTests = collectionResult.second.let {
            when (it) {
                is TestCollectingListener.Result.Failed -> {
                    // TODO: specific exception
                    throw RuntimeException("Failed to collect list of tests. Error is ${it.lastFailure}")
                }
                is TestCollectingListener.Result.Successful -> {
                    it.tests
                }
            }
        }
        val testInfoMessages = collectionResult.first

        hasOnDeviceLibrary = hasOnDeviceLibrary && testInfoMessages.isNotEmpty()

        val testInfos = tryCollectingAndDecodingInfos(testInfoMessages)

        return CollectedInfo(device, hasOnDeviceLibrary, deviceTests, testInfos)
    }

    private suspend fun collectTestData(
            device: AndroidDevice,
            withOnDeviceLib: Boolean): Pair<List<LogCatMessage>, TestCollectingListener.Result> = withContext(Dispatchers.IO) {
        val testCollectingListener = TestCollectingListener()
        val logCatCollector = LogcatReceiver(device)
        val testRun = testRunFactory.createCollectingRun(
                device, context.pool, testCollectingListener, withOnDeviceLib)
        logger.info("LIST collectTestData createCollectingRun")
        try {
            clearLogcat(device.deviceInterface)
            logCatCollector.start(this@JUnitTestCaseProvider.javaClass.simpleName)

            testRun.execute()

            delay(logcatWaiterSleep) // make sure all logcat messages are read
            logCatCollector.stop()

            Pair(
                    logCatCollector.messages
                            .filter { logCatMessage -> "Tongs.TestInfo" == logCatMessage.header.tag },
                    testCollectingListener.result
            )
        } finally {
            logCatCollector.stop()
        }
    }

    internal fun tryCollectingAndDecodingInfos(
            testInfoMessages: List<LogCatMessage>
    ): Map<TestIdentifier, TestInfo> {
        return try {
            decodeMessages(testInfoMessages)
                    .let {
                        jsonInfoDecoder.decodeStructure(it.toList())
                    }
                    .asReversed() // make sure the first entry for duplicate keys is used
                    .associateBy { it.identifier }
        } catch (e: Exception) {
            logger.warn("Failed to collect annotation and structure information about tests", e)
            emptyMap()
        }
    }

    private class CollectedInfo(
            val device: AndroidDevice,
            val hasOnDeviceLibrary: Boolean,
            val tests: Set<TestIdentifier>,
            val infoMessages: Map<TestIdentifier, TestInfo>
    )
}
