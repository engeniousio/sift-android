/*
 * Copyright 2021 TarCV
 * Copyright 2015 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.system.io;

import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.api.devices.Pool;
import com.github.tarcv.tongs.api.result.FileType;
import com.github.tarcv.tongs.api.testcases.TestCase;

import java.io.File;

public interface FileManager {
    File[] getTestFilesForDevice(Pool pool, Device serial);

    File createFile(FileType fileType, Pool pool, Device device, TestCase testCaseEvent);

    File createFile(FileType fileType, Pool pool, Device device,  TestCase testCaseEvent, int sequenceNumber);

    File createFile(FileType fileType, Pool pool, Device device,  TestCase testCaseEvent, String suffix);

    File createSummaryFile();

    File[] getFiles(FileType fileType, Pool pool, Device device, TestCase testIdentifier);

    File getFile(FileType fileType, String pool, String device, TestCase testIdentifier);

    File getFile(FileType fileType, Pool pool, Device device, TestCase testIdentifier, String suffix);

    File getRelativeFile(FileType fileType, Pool pool, Device device, TestCase testIdentifier, String suffix);
}