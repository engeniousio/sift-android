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
package com.github.tarcv.tongs.api.devices;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Objects;

public abstract class Device {
    private String nameSuffix;

    @NotNull public abstract String getHost();

    public abstract String getSerial();

    public String getSafeSerial() {
        return getSerial().replaceAll(":", "-");
    }

    public abstract String getManufacturer();

    public abstract String getModelName();

    public abstract int getOsApiLevel();

    public abstract String getLongName();

    public abstract Object getDeviceInterface();

    public abstract boolean isTablet();

    @Nullable
    public abstract DisplayGeometry getGeometry();

    public abstract Diagnostics getSupportedVisualDiagnostics();

    public String getName() {
        return getModelName() + nameSuffix;
    }

    public void setNameSuffix(String suffix) {
        nameSuffix = suffix;
    }

    protected abstract Object getUniqueIdentifier();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Device)) return false;
        Device device = (Device) o;
        return getUniqueIdentifier().equals(device.getUniqueIdentifier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUniqueIdentifier());
    }

    public static final Device TEST_DEVICE = new Device() {
        private final Object uniqueIdentifier = new Object();

        @NotNull
        @Override
        public String getHost() {
            return "localhost";
        }

        @Override
        public String getSerial() {
            return "DeviceSerial";
        }

        @Override
        public String getManufacturer() {
            return "DeviceManufacturer";
        }

        @Override
        public String getModelName() {
            return "DeviceModel";
        }

        @Override
        public int getOsApiLevel() {
            return 25;
        }

        @Override
        public String getLongName() {
            return "LongDeviceName";
        }

        @Override
        public Object getDeviceInterface() {
            return new Object();
        }

        @Override
        public boolean isTablet() {
            return false;
        }

        @Override
        @Nullable
        public DisplayGeometry getGeometry() {
            return new DisplayGeometry(300);
        }

        @Override
        public Diagnostics getSupportedVisualDiagnostics() {
            return Diagnostics.VIDEO;
        }

        @Override
        protected Object getUniqueIdentifier() {
            return uniqueIdentifier;
        }
    };
}
