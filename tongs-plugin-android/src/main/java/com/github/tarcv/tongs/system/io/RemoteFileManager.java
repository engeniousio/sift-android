/*
 * Copyright 2020 TarCV
 * Copyright 2014 Shazam Entertainment Limited
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
package com.github.tarcv.tongs.system.io;

import com.android.ddmlib.*;
import com.github.tarcv.tongs.api.testcases.TestCase;
import com.github.tarcv.tongs.system.DdmsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;


public class RemoteFileManager {

    private static final Logger logger = LoggerFactory.getLogger(RemoteFileManager.class);
    private static final String TONGS_DIRECTORY = "/sdcard/tongs"; // TODO: use $EXTERNAL_STORAGE instead of /sdcard
    private static final NullOutputReceiver NO_OP_RECEIVER = new NullOutputReceiver();

    private RemoteFileManager() {}

    public static void removeRemotePath(IDevice device, String remotePath) {
        executeCommand(device, "rm " + remotePath, "Could not delete remote file(s): " + remotePath);
    }

    // TODO: use file index for this too and call this method only once for each test attempt
    public static String getCoverageFileName(TestCase testIdentifier) {
        return remoteFileForTest("/coverage.ec");
    }

    public static void createRemoteDirectory(IDevice device) {
        executeCommand(device, "mkdir " + TONGS_DIRECTORY, "Could not create remote directory: " + TONGS_DIRECTORY);
    }

    public static void removeRemoteDirectory(IDevice device) {
        executeCommand(device, "rm -r " + TONGS_DIRECTORY, "Could not delete remote directory: " + TONGS_DIRECTORY);
    }

    private static void executeCommand(IDevice device, String command, String errorMessage) {
        try {
            device.executeShellCommand(command, NO_OP_RECEIVER);
        } catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException e) {
            logger.error(errorMessage, e);
        }
    }

    private static final AtomicInteger videoIndex = new AtomicInteger();
    public static String remoteVideoForTest() {
        // Incrementing is ok as this method is called only once for each test attempt
        String filename = String.format("scrn%x.mp4", videoIndex.getAndIncrement());

        return DdmsUtils.escapeArgumentForCommandLine(remoteFileForTest(filename));
    }

    private static String remoteFileForTest(String filename) {
        return TONGS_DIRECTORY + "/" + filename;
    }

}
