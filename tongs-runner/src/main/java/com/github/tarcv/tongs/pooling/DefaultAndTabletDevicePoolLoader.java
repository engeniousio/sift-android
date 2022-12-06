/*
 * Copyright 2018 TarCV
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
package com.github.tarcv.tongs.pooling;

import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.api.devices.Pool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.github.tarcv.tongs.api.devices.Pool.Builder.aDevicePool;

/**
 * Create tablets/other pool based on self-reported ro.build.characteristics = tablet
 */
public class DefaultAndTabletDevicePoolLoader implements DevicePoolLoader {

	private static final String DEFAULT_POOL_NAME = "default_pool";
	private static final String TABLETS = "tablets";

	public DefaultAndTabletDevicePoolLoader() {
    }

	public Collection<Pool> loadPools(List<Device> devices) {
        Collection<Pool> pools = new ArrayList<>();
        Pool.Builder defaultPoolBuilder = aDevicePool().withName(DEFAULT_POOL_NAME);
        Pool.Builder tabletPoolBuilder = aDevicePool().withName(TABLETS);

        for (Device device : devices) {
            if (device.isTablet()) {
                tabletPoolBuilder.addDevice(device);
            } else {
                defaultPoolBuilder.addDevice(device);
            }
        }
        defaultPoolBuilder.addIfNotEmpty(pools);
        tabletPoolBuilder.addIfNotEmpty(pools);
        return pools;
	}
}
