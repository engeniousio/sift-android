/*
 * Copyright 2020 TarCV
 * Copyright 2016 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.runner.listeners

import com.android.ddmlib.logcat.LogCatListener
import com.android.ddmlib.logcat.LogCatMessage
import com.android.ddmlib.logcat.LogCatReceiverTask
import com.github.tarcv.tongs.model.AndroidDevice
import java.util.*
import kotlin.collections.ArrayList

class LogcatReceiver(
        private val device: AndroidDevice
) {
    private val logCatReceiverTask = LogCatReceiverTask(device.deviceInterface)
    private val logCatMessages = Collections.synchronizedList(ArrayList<LogCatMessage>())
    private val logCatListener = MessageCollectingLogCatListener(logCatMessages)

    val messages: List<LogCatMessage>
        get() {
            return synchronized(logCatMessages) {
                val size = logCatMessages.size
                val copyOfLogCatMessages: MutableList<LogCatMessage> = ArrayList(size)
                copyOfLogCatMessages.addAll(logCatMessages)

                copyOfLogCatMessages
            }
        }

    fun start(runName: String) {
        logCatReceiverTask.addLogCatListener(logCatListener)
        Thread(logCatReceiverTask, "CatLogger-" + runName + "-" + device.serial).start()
    }

    fun stop() {
        logCatReceiverTask.stop()
        logCatReceiverTask.removeLogCatListener(logCatListener)
    }
}

private class MessageCollectingLogCatListener(
        private val logCatMessages: MutableList<LogCatMessage>
) : LogCatListener {

    override fun log(msgList: List<LogCatMessage>) {
        synchronized(logCatMessages) {
            logCatMessages.addAll(msgList)
        }
    }
}