/*
 * Copyright 2021 TarCV
 * Copyright 2018 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.device;

import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.api.devices.Pool;
import com.github.tarcv.tongs.api.run.TestCaseEvent;
import com.github.tarcv.tongs.system.io.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static com.github.tarcv.tongs.api.result.StandardFileTypes.TEST;

public class DeviceTestFilesCleanerImpl implements DeviceTestFilesCleaner {
    private static final Logger logger = LoggerFactory.getLogger(DeviceTestFilesCleanerImpl.class);
    private final FileManager fileManager;
    private final Pool pool;
    private final Device device;

    public DeviceTestFilesCleanerImpl(FileManager fileManager, Pool pool, Device device) {
        this.fileManager = fileManager;
        this.pool = pool;
        this.device = device;
    }

    @Override
    public boolean deleteTraceFiles(TestCaseEvent testIdentifier) {
        File file = fileManager.getFile(TEST, pool, device, testIdentifier.getTestCase(), "");
        boolean isDeleted = file.delete();
        if (!isDeleted) {
            logger.warn("Failed to delete a file {}", file.getAbsolutePath());
        }
        return isDeleted;
    }
}
