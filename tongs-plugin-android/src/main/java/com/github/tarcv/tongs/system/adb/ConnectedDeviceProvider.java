/*
 * Copyright 2020 TarCV
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Based on com/android/builder/testing/ConnectedDeviceProvider.java from Android Gradle Plugin 3.3.2 source code
 *
 * The "NOTICE" text file relevant to this source file and this source file only is
 * com/github/tarcv/tongs/system/adb/ConnectedDeviceProvider-NOTICE.txt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.tarcv.tongs.system.adb;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.device.DeviceGeometryRetriever;
import com.github.tarcv.tongs.device.DeviceLoader;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DeviceProvider for locally connected devices. Basically returns the list of devices that
 * are currently connected at the time {@link #init()} is called.
 */
public class ConnectedDeviceProvider {

    private final File adbLocation;

    private static final Logger logger = LoggerFactory.getLogger(ConnectedDeviceProvider.class);

    private final DeviceGeometryRetriever deviceGeometryRetriever;

    private final List<Device> localDevices = Lists.newArrayList();

    public ConnectedDeviceProvider(DeviceGeometryRetriever deviceGeometryRetriever, File adbLocation) {
        this.adbLocation = adbLocation;
        this.deviceGeometryRetriever = deviceGeometryRetriever;
    }

    public List<Device> getDevices() {
        return localDevices;
    }

    public void init() {
        int timeOut = 30000;
        DdmPreferences.setTimeOut(timeOut);

        LogAdapter logAdapter = new LogAdapter(logger);
        Log.addLogger(logAdapter);

        DdmPreferences.setLogLevel(LogAdapter.translateLogLevel(logger));
        try {
            AndroidDebugBridge.initIfNeeded(false /*clientSupport*/);

            AndroidDebugBridge bridge =
                    AndroidDebugBridge.createBridge(
                            adbLocation.getAbsolutePath(), false /*forceNewBridge*/);

            if (bridge == null) {
                throw new RuntimeException(
                        "Could not create ADB Bridge. "
                                + "ADB location: "
                                + adbLocation.getAbsolutePath());
            }

            IDevice[] devices = waitForInitialDeviceList(bridge, timeOut);
            if (devices.length == 0) {
                localDevices.clear();
                return;
            }

            final String androidSerialsEnv = System.getenv("ANDROID_SERIAL");
            @Nullable final Set<String> serialsFilter = asSerialsFilter(androidSerialsEnv);

            final List<IDevice> filteredDevices = filterDevices(devices, serialsFilter);

            for (IDevice device : filteredDevices) {
                if (device.getState() == IDevice.DeviceState.ONLINE) {
                    Device deviceInfo = DeviceLoader.loadDeviceCharacteristics(device, deviceGeometryRetriever);
                    localDevices.add(deviceInfo);
                } else {
                    logger.info(
                            "Skipping device '{}' ({}): Device is {}{}.",
                            device.getName(),
                            device.getSerialNumber(),
                            device.getState(),
                            device.getState() == IDevice.DeviceState.UNAUTHORIZED
                                    ? ",\n"
                                            + "    see http://d.android.com/tools/help/adb.html#Enabling"
                                    : "");
                }
            }

            if (localDevices.isEmpty()) {
                if (serialsFilter != null) {
                    throw new RuntimeException(
                            String.format(
                                    "Connected device with serial $1%s is not online.",
                                    androidSerialsEnv));
                } else {
                    throw new RuntimeException("No online devices found.");
                }
            }
            // ensure device names are unique since many reports are keyed off of names.
            makeDeviceNamesUnique();
        } finally {
            Log.removeLogger(logAdapter);
        }
    }

    @Nullable
    private static Set<String> asSerialsFilter(String androidSerialsEnv) {
        final Set<String> serialsFilter;
        if (!Strings.isNullOrEmpty(androidSerialsEnv)) {
            serialsFilter = Sets.newHashSet(Splitter.on(',').trimResults().split(androidSerialsEnv));
        } else {
            serialsFilter = null;
        }
        return serialsFilter;
    }

    @NotNull
    private static List<IDevice> filterDevices(IDevice[] devices, @Nullable Set<String> serialsFilter) {
        if (serialsFilter == null) {
            return Arrays.asList(devices);
        }

        final List<IDevice> filteredDevices;
        filteredDevices = Lists.newArrayListWithCapacity(devices.length);
        for (IDevice iDevice : devices) {
            if (serialsFilter.contains(iDevice.getSerialNumber())) {
                serialsFilter.remove(iDevice.getSerialNumber());
                filteredDevices.add(iDevice);
            }
        }

        if (!serialsFilter.isEmpty()) {
            throw new RuntimeException(
                    String.format(
                            "Connected device with serial%s '%s' not found!",
                            serialsFilter.size() == 1 ? "" : "s", Joiner.on("', '").join(serialsFilter)));
        }

        return filteredDevices;
    }

    private static IDevice[] waitForInitialDeviceList(AndroidDebugBridge bridge, int timeOut) {
        int getDevicesCountdown = timeOut;
        final int sleepTime = 1000;
        while (!bridge.hasInitialDeviceList() && getDevicesCountdown >= 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Getting device list was cancelled", e);
            }

            getDevicesCountdown -= sleepTime;
        }

        if (!bridge.hasInitialDeviceList()) {
            throw new RuntimeException("Timeout getting device list.");
        }

        return bridge.getDevices();
    }

    private boolean hasDevicesWithDuplicateName() {
        Set<String> deviceNames = new HashSet<>();
        for (Device device : localDevices) {
            if (!deviceNames.add(device.getName())) {
                return true;
            }
        }
        return false;
    }

    private void makeDeviceNamesUnique() {
        if (hasDevicesWithDuplicateName()) {
            for (Device device : localDevices) {
                device.setNameSuffix(device.getSerial());
            }
        }
        if (hasDevicesWithDuplicateName()) {
            // still have duplicates :/ just use a counter.
            int counter = 0;
            for (Device device : localDevices) {
                device.setNameSuffix(device.getSerial() + "-" + counter);
                counter ++;
            }
        }

    }

    private static final class LogAdapter implements Log.ILogOutput {

        private final Logger logger;

        private LogAdapter(Logger logger) {
            this.logger = logger;
        }

        static String translateLogLevel(Logger logger) {
            if (logger.isTraceEnabled() || logger.isDebugEnabled()) {
                return Log.LogLevel.VERBOSE.getStringValue();
            } else if (logger.isInfoEnabled()) {
                return Log.LogLevel.INFO.getStringValue();
            } else if (logger.isWarnEnabled()) {
                return Log.LogLevel.WARN.getStringValue();
            } else if (logger.isErrorEnabled()) {
                return Log.LogLevel.ERROR.getStringValue();
            } else {
                throw new IllegalArgumentException("Unknown log level set for " + logger);
            }
        }

        @Override
        public void printLog(Log.LogLevel logLevel, String tag, String message) {
            final String logFormat = "[{}]: {}";
            switch (logLevel) {
                case VERBOSE:
                case DEBUG:
                    logger.debug(logFormat, tag, message);
                    break;
                case INFO:
                    logger.info(logFormat, tag, message);
                    break;
                case WARN:
                    logger.warn(logFormat, tag, message);
                    break;
                case ERROR:
                case ASSERT:
                    logger.error(null, logFormat, tag, message);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown log level " + logLevel);
            }
        }

        @Override
        public void printAndPromptLog(Log.LogLevel logLevel, String tag, String message) {
            printLog(logLevel, tag, message);
        }
    }
}
