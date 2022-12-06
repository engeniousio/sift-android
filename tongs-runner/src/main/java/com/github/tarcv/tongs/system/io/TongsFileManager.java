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

package com.github.tarcv.tongs.system.io;

import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.api.devices.Pool;
import com.github.tarcv.tongs.api.result.FileType;
import com.github.tarcv.tongs.api.testcases.TestCase;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;

import static com.github.tarcv.tongs.CommonDefaults.TONGS_SUMMARY_FILENAME_FORMAT;
import static com.github.tarcv.tongs.api.result.StandardFileTypes.TEST;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Paths.get;

public class TongsFileManager implements FileManager {
    private final File output;

    public TongsFileManager(File output) {
        this.output = output;
    }

    @Override
    public File[] getTestFilesForDevice(Pool pool, Device serial) {
        Path path = getDirectory(TEST, pool, serial);
        return path.toFile().listFiles();
    }

    @Override
    public File createFile(FileType fileType, Pool pool, Device device, TestCase testCase) {
        try {
            Path directory = createDirectory(fileType, pool, device);
            String filename = FileUtils.createFilenameForTest(testCase, fileType);
            return createFile(directory, filename);
        } catch (IOException e) {
            throw new CouldNotCreateDirectoryException(e);
        }
    }

    @Override
    public File createFile(FileType fileType, Pool pool, Device device, TestCase testCase, int sequenceNumber) {
        String sequenceSuffix = String.format("%02d", sequenceNumber);
        return createFile(fileType, pool, device, testCase, sequenceSuffix);
    }

    @Override
    public File createFile(FileType fileType, Pool pool, Device device, TestCase testCase, String suffix) {
        try {
            Path directory = createDirectory(fileType, pool, device);
            String filename = FileUtils.createFilenameForTest(testCase, fileType, suffix);
            return createFile(directory, filename);
        } catch (IOException e) {
            throw new CouldNotCreateDirectoryException(e);
        }
    }

    @Override
    public File createSummaryFile() {
        try {
            Path path = get(output.getAbsolutePath(), "summary");
            Path directory = createDirectories(path);
            return createFile(directory, String.format(TONGS_SUMMARY_FILENAME_FORMAT, System.currentTimeMillis()));
        } catch (IOException e) {
            throw new CouldNotCreateDirectoryException(e);
        }
    }

    @Override
    public File[] getFiles(FileType fileType, Pool pool, Device device, TestCase testIdentifier) {
        FileFilter fileFilter = new AndFileFilter(
                new PrefixFileFilter(testIdentifier.toString()),
                new SuffixFileFilter(fileType.getSuffix()));

        File deviceDirectory = get(output.getAbsolutePath(), fileType.getDirectory(), pool.getName(), device.getSafeSerial()).toFile();
        return deviceDirectory.listFiles(fileFilter);
    }

    @Override
    public File getFile(FileType fileType, String pool, String device, TestCase testIdentifier) {
        String filenameForTest = FileUtils.createFilenameForTest(testIdentifier, fileType);
        Path path = get(output.getAbsolutePath(), fileType.getDirectory(), pool, device, filenameForTest);
        return path.toFile();
    }

    @Override
    public File getFile(FileType fileType, Pool pool, Device device, TestCase testIdentifier, String suffix) {
        String filenameForTest = FileUtils.createFilenameForTest(testIdentifier, fileType, suffix);
        Path path = get(output.getAbsolutePath(), fileType.getDirectory(), pool.getName(), device.getSafeSerial(), filenameForTest);
        return path.toFile();
    }

    @Override
    public File getRelativeFile(FileType fileType, Pool pool, Device device, TestCase testIdentifier, String suffix) {
        File absoluteFile = getFile(fileType, pool, device, testIdentifier, suffix);
        return output.getAbsoluteFile().toPath().relativize(absoluteFile.toPath()).toFile();
    }

    private Path createDirectory(FileType test, Pool pool, Device device) throws IOException {
        return createDirectories(getDirectory(test, pool, device));
    }

    private Path getDirectory(FileType fileType, Pool pool, Device device) {
        return get(output.getAbsolutePath(), fileType.getDirectory(), pool.getName(), device.getSafeSerial());
    }

    private File createFile(Path directory, String filename) {
        return new File(directory.toFile(), filename);
    }

}