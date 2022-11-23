/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.pooling

import com.android.ddmlib.*
import com.android.ddmlib.IDevice.Feature.SCREEN_RECORD
import com.android.ddmlib.log.LogReceiver
import com.android.sdklib.AndroidVersion
import com.github.tarcv.tongs.runner.TestAndroidTestRunnerFactory
import com.google.common.util.concurrent.ListenableFuture
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.lang.Thread.sleep
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.annotation.concurrent.GuardedBy

class StubDevice(
        private val serial: String,
        private val manufacturer: String,
        private val model: String,
        private val name: String,
        private val api: Int,
        private val characteristics: String,
        private val testCommandDelay: Long
) : IDevice {
    companion object {
        private val logger = LoggerFactory.getLogger(StubDevice::class.java)
    }

    private val deviceLogFile = File("${serial}_adb.log")

    @GuardedBy("this")
    private var logcatReceiver: IShellOutputReceiver? = null

    override fun startScreenRecorder(remoteFilePath: String, options: ScreenRecorderOptions, receiver: IShellOutputReceiver) {
        synchronized(this) {
            val optionsStr = "{dimen=${options.width}x${options.height}" +
                    ", Mbps=${options.bitrateMbps}" +
                    ", limit=${options.timeLimit} ${options.timeLimitUnits}}"
            FileWriter(deviceLogFile, true).apply {
                write("${System.currentTimeMillis()}\t[START SCREEN RECORDER]" +
                        " $remoteFilePath,$optionsStr${System.lineSeparator()}")
                close()
            }
        }
    }

    override fun getName(): String = name

    override fun executeShellCommand(command: String, receiver: IShellOutputReceiver) {
        @Suppress("DEPRECATION")
        executeShellCommand(command, receiver, DdmPreferences.getTimeOut())
    }

    override fun executeShellCommand(command: String, receiver: IShellOutputReceiver, maxTimeToOutputResponse: Int) {
        executeShellCommand(command, receiver, maxTimeToOutputResponse.toLong(), TimeUnit.MILLISECONDS)
    }

    override fun executeShellCommand(command: String, receiver: IShellOutputReceiver, maxTimeout: Long, maxTimeToOutputResponse: Long, maxTimeUnits: TimeUnit) {
        executeShellCommand(command, receiver, maxTimeToOutputResponse, maxTimeUnits)
    }

    override fun executeShellCommand(command: String, receiver: IShellOutputReceiver, maxTimeToOutputResponse: Long, maxTimeUnits: TimeUnit) {
        FileWriter(deviceLogFile, true).apply {
            write("${System.currentTimeMillis()}\t$command${System.lineSeparator()}")
            close()
        }

        val maxTimeToOutputResponseMillis = maxTimeUnits.toMillis(maxTimeToOutputResponse)

        if (command.contains("am instrument")) {
            if (command.contains("-e log true")) {
                executeCollectingRun()
            } else {
                sleep(testCommandDelay)
            }
        } else if (command.contains("logcat") && !command.contains("-c")) {
            executeLogcatCollectionLoop(command, receiver, maxTimeToOutputResponseMillis)
        } else {
            val outputBytes = "<stub> <stub> <stub> <stub> <stub>".toByteArray()
            receiver.addOutput(outputBytes, 0, outputBytes.size)
            receiver.flush()
        }
    }

    private fun executeLogcatCollectionLoop(command: String, receiver: IShellOutputReceiver, maxTimeToOutputResponseMillis: Long) {
        if (maxTimeToOutputResponseMillis != 0L) {
            throw AssertionError("maxTimeToOutputResponse should be 0 for listen logcat command")
        }

        synchronized(this) {
            logcatReceiver = receiver
        }

        var lastWarningTime = System.currentTimeMillis()
        while (!receiver.isCancelled) {
            sleep(300L) // 'logcat' only stops on Ctrl+C or when device is disconnected
            if (!receiver.isCancelled && System.currentTimeMillis() - lastWarningTime > 10_000L) {
                logger.error("Logcat reader thread is still alive [command: $command]")
                lastWarningTime = System.currentTimeMillis()
            }
        }
    }

    private fun executeCollectingRun() {
        synchronized(this) {
            logcatReceiver?.run {
                val timePrefix = DateTimeFormatter.ofPattern("MM-dd hh:mm:ss")
                        .format(OffsetDateTime.now(ZoneOffset.UTC)) + "."
                TestAndroidTestRunnerFactory.logcatLines
                        .asSequence()
                        .chunked(3)
                        .mapIndexed { index, item ->
                            item.map {
                                it
                                        .replace("LOGCAT_TIME", timePrefix + String.format("%03d", index))
                                        .replace("LOGCAT_INDEX", String.format("%08x", index))
                            }
                        }
                        .flatMap { it.asSequence() }
                        .forEach {
                            val bytes = ("$it\r\n").toByteArray()
                            addOutput(bytes, 0, bytes.size)
                        }
            }
        }
        sleep(TestAndroidTestRunnerFactory.logcatLines.size.toLong())
    }

    override fun getProperty(name: String): String {
        return when (name) {
            "ro.product.manufacturer" -> manufacturer
            "ro.product.model" -> model
            "ro.build.version.sdk" -> api.toString()
            "ro.build.characteristics" -> characteristics
            else -> ""
        }
    }

    @Throws(IOException::class)
    override fun pullFile(remote: String, local: String) {
        synchronized(this) {
            FileWriter(deviceLogFile, true).apply {
                write("${System.currentTimeMillis()}\t[PULL FILE] $remote,$local" +
                        System.lineSeparator())
                close()
            }
        }

        val writer = FileWriter(File(local))
        writer.flush()
        writer.close()
    }

    override fun uninstallPackage(packageName: String): String? {
        synchronized(this) {
            FileWriter(deviceLogFile, true).apply {
                write("${System.currentTimeMillis()}\t[UNINSTALL PACKAGE] $packageName" +
                        System.lineSeparator())
                close()
            }
        }

        return null
    }

    override fun installPackage(
            packageFilePath: String,
            reinstall: Boolean,
            vararg extraArgs: String
    ) {
        synchronized(this) {
            FileWriter(deviceLogFile, true).apply {
                write("${System.currentTimeMillis()}\t[INSTALL PACKAGE] $packageFilePath" +
                        " reinstall=$reinstall extraArgs={${extraArgs.joinToString(" ")}}" +
                        System.lineSeparator())
                close()
            }
        }
    }

    override fun installPackage(
            packageFilePath: String,
            reinstall: Boolean,
            receiver: InstallReceiver,
            vararg extraArgs: String
    ) {
        installPackage(packageFilePath, reinstall, *extraArgs)
    }

    override fun installPackage(
            packageFilePath: String,
            reinstall: Boolean,
            receiver: InstallReceiver,
            maxTimeout: Long,
            maxTimeToOutputResponse: Long,
            maxTimeUnits: TimeUnit,
            vararg extraArgs: String
    ) {
        installPackage(packageFilePath, reinstall, *extraArgs)
    }

    override fun supportsFeature(feature: IDevice.Feature): Boolean {
        return when(feature) {
            SCREEN_RECORD -> true
            else -> false
        }
    }

    override fun getScreenshot(): RawImage {
        TODO("not implemented")
    }

    override fun getVersion(): AndroidVersion = AndroidVersion(api, null)

    override fun getSerialNumber(): String = serial
    override fun getAvdName(): String {
        TODO("Not yet implemented")
    }

    override fun isOnline(): Boolean = true

    override fun getClientName(pid: Int): String = "client$pid"

    // Methods below this line are not used Tongs and therefore doesn't need to be stubbed

    override fun isOffline(): Boolean {
        TODO("not implemented")
    }

    override fun reboot(into: String?) {
        TODO("not implemented")
    }

    override fun getMountPoint(name: String?): String {
        TODO("not implemented")
    }

    override fun getClients(): Array<Client> {
        TODO("not implemented")
    }

    override fun runLogService(logname: String?, receiver: LogReceiver?) {
        TODO("not implemented")
    }

    override fun installRemotePackage(remoteFilePath: String?, reinstall: Boolean, vararg extraArgs: String?) {
        TODO("not implemented")
    }

    override fun installRemotePackage(remoteFilePath: String?, reinstall: Boolean, receiver: InstallReceiver?, vararg extraArgs: String?) {
        TODO("not implemented")
    }

    override fun installRemotePackage(remoteFilePath: String?, reinstall: Boolean, receiver: InstallReceiver?, maxTimeout: Long, maxTimeToOutputResponse: Long, maxTimeUnits: TimeUnit?, vararg extraArgs: String?) {
        TODO("not implemented")
    }

    override fun runEventLogService(receiver: LogReceiver?) {
        TODO("not implemented")
    }

    override fun getLanguage(): String {
        TODO("not implemented")
    }

    override fun root(): Boolean {
        TODO("not implemented")
    }

    override fun getSystemProperty(name: String?): ListenableFuture<String>? {
        TODO("not implemented")
    }

    override fun isBootLoader(): Boolean {
        TODO("not implemented")
    }

    override fun isEmulator(): Boolean {
        TODO("not implemented")
    }

    override fun getFileListingService(): FileListingService {
        TODO("not implemented")
    }

    override fun isRoot(): Boolean {
        TODO("not implemented")
    }

    override fun getBatteryLevel(): Int {
        TODO("Not yet implemented")
    }

    override fun getBatteryLevel(freshnessMs: Long): Int {
        TODO("Not yet implemented")
    }

    override fun createForward(localPort: Int, remotePort: Int) {
        TODO("not implemented")
    }

    override fun createForward(localPort: Int, remoteSocketName: String?, namespace: IDevice.DeviceUnixSocketNamespace?) {
        TODO("not implemented")
    }

    override fun removeForward(localPort: Int, remotePort: Int) {
        TODO("Not yet implemented")
    }

    override fun removeForward(
        localPort: Int,
        remoteSocketName: String?,
        namespace: IDevice.DeviceUnixSocketNamespace?
    ) {
        TODO("Not yet implemented")
    }

    override fun getAbis(): MutableList<String> {
        TODO("not implemented")
    }

    override fun pushFile(local: String?, remote: String?) {
        TODO("not implemented")
    }

    override fun removeRemotePackage(remoteFilePath: String?) {
        TODO("not implemented")
    }

    override fun getClient(applicationName: String?): Client {
        TODO("not implemented")
    }

    override fun getBattery(): Future<Int> {
        TODO("not implemented")
    }

    override fun getBattery(freshnessTime: Long, timeUnit: TimeUnit?): Future<Int> {
        TODO("not implemented")
    }

    override fun hasClients(): Boolean {
        TODO("not implemented")
    }

    override fun getRegion(): String {
        TODO("not implemented")
    }

    override fun getState(): IDevice.DeviceState {
        TODO("not implemented")
    }

    override fun getProperties(): MutableMap<String, String> {
        TODO("Not yet implemented")
    }

    override fun getPropertyCount(): Int {
        TODO("Not yet implemented")
    }

    override fun installPackages(apks: MutableList<File>?, reinstall: Boolean, installOptions: MutableList<String>?, timeout: Long, timeoutUnit: TimeUnit?) {
        TODO("not implemented")
    }

    override fun getDensity(): Int {
        TODO("not implemented")
    }

    override fun getSyncService(): SyncService {
        TODO("not implemented")
    }

    override fun syncPackageToDevice(localFilePath: String?): String {
        TODO("not implemented")
    }

    override fun arePropertiesSet(): Boolean {
        TODO("not implemented")
    }

    override fun getPropertySync(name: String?): String {
        TODO("Not yet implemented")
    }

    override fun getPropertyCacheOrSync(name: String?): String {
        TODO("Not yet implemented")
    }

    override fun supportsFeature(feature: IDevice.HardwareFeature?): Boolean {
        TODO("not implemented")
    }

    override fun getScreenshot(timeout: Long, unit: TimeUnit?): RawImage {
        TODO("not implemented")
    }
}