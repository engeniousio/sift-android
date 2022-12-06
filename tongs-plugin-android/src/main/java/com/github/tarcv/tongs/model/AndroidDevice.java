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
package com.github.tarcv.tongs.model;

import com.android.ddmlib.IDevice;
import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.api.devices.Diagnostics;
import com.github.tarcv.tongs.api.devices.DisplayGeometry;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.tarcv.tongs.device.DeviceUtilsKt.computeDiagnostics;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Representation of a device and its details.
 */
public class AndroidDevice extends Device {
	private final String serial;
	private final String manufacturer;
	private final String model;
	private final int apiLevel;
	private final transient IDevice deviceInterface;
	private final boolean isTablet;

	// Might be null when this is a temporary object used when determining geometry
	@Nullable
	private final DisplayGeometry geometry;
    private final Diagnostics diagnostics;
	private final AtomicBoolean hasOnDeviceLibrary = new AtomicBoolean(true);

	@NotNull
	@Override
	public String getHost() {
		return "localhost"; // TODO
	}

	@Override
	public String getSerial() {
		return serial;
	}

	@Override
	public String getManufacturer() {
		return manufacturer;
	}

	@Override
	public String getModelName() {
		return model;
	}

	@Override
	public int getOsApiLevel() {
		return apiLevel;
	}

	@Override
	public String getLongName() {
		return serial + " (" + model + ")";
	}

	@Override
	public IDevice getDeviceInterface() {
		return deviceInterface;
	}

	@Override
	public boolean isTablet() {
		return isTablet;
	}

	@Override
	@Nullable
    public DisplayGeometry getGeometry() {
        return geometry;
    }

    @Override
	public Diagnostics getSupportedVisualDiagnostics() {
        return diagnostics;
    }

	/**
	 * Returns an object that uniquely identify underlying device and which has equals and hashCode implementations
	 *  that are reproducible when a new instance of Device is created from the same device
	 *
	 * @return object uniquely identifying underlying device
	 */
	@Override
	protected Object getUniqueIdentifier() {
		return serial;
	}

	public boolean hasOnDeviceLibrary() {
		return hasOnDeviceLibrary.get();
	}

	public void setHasOnDeviceLibrary(boolean newValue) {
		hasOnDeviceLibrary.set(newValue);
	}

    public static class Builder {
        private String serial = "Unspecified serial";
        private String manufacturer = "Unspecified manufacturer";
        private String model = "Unspecified model";
        private String apiLevel;
		private IDevice deviceInterface;
		private boolean isTablet = false;
		private DisplayGeometry geometry;

        public static Builder aDevice() {
			return new Builder();
		}

		public Builder withSerial(String serial) {
			this.serial = serial;
			return this;
		}

		public Builder withManufacturer(String manufacturer) {
			if (!isNullOrEmpty(manufacturer)) {
				this.manufacturer = manufacturer;
			}
			return this;
		}

		public Builder withModel(String model) {
			if (!isNullOrEmpty(model)) {
				this.model = model;
			}
			return this;
		}

		public Builder withApiLevel(String apiLevel) {
			if (!isNullOrEmpty(apiLevel)) {
				this.apiLevel = apiLevel;
			}
			return this;
		}

		public Builder withDeviceInterface(IDevice deviceInterface) {
			this.deviceInterface = deviceInterface;
			return this;
		}

        /**
         * Tablets seem to have property [ro.build.characteristics = tablet], but not all tablets respect that.
         * @param characteristics the characteristics field as reported by the device
         * @return this builder
         */
		public Builder withTabletCharacteristic(String characteristics) {
			if (!isNullOrEmpty(characteristics) && characteristics.contains("tablet")) {
				isTablet = true;
			}
			return this;
		}

		public Builder withDisplayGeometry(@Nullable DisplayGeometry geometry) {
			this.geometry = geometry;
			return this;
		}

		public AndroidDevice build() {
			return new AndroidDevice(this);
		}
	}

	private AndroidDevice(Builder builder) {
		serial = builder.serial;
		manufacturer = builder.manufacturer;
		model = builder.model;
		if (builder.apiLevel != null) {
			apiLevel = Integer.parseInt(builder.apiLevel, 10);
		} else {
			apiLevel = -1;
		}
		deviceInterface = builder.deviceInterface;
		isTablet = builder.isTablet;
		geometry = builder.geometry;
        diagnostics = computeDiagnostics(deviceInterface, apiLevel);
	}
}
