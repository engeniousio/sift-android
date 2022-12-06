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
package com.github.tarcv.tongs.pooling;

import com.github.tarcv.tongs.ManualPooling;
import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.api.devices.Pool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.github.tarcv.tongs.api.devices.Pool.Builder.aDevicePool;
import static java.util.Map.Entry;

/**
 * Load pools specified by -Dtongs.pool.NAME=Serial_1,Serial_2
 */
public class SerialBasedDevicePoolLoader implements DevicePoolLoader {
    private final ManualPooling manualPooling;

    public SerialBasedDevicePoolLoader(ManualPooling manualPooling) {
        this.manualPooling = manualPooling;
    }

    @Override
    public Collection<Pool> loadPools(List<Device> devices) {
        final Collection<Pool> pools = new ArrayList<>();
        for (Entry<String, Collection<String>> pool : manualPooling.groupings.entrySet()) {
            final Pool.Builder poolBuilder = aDevicePool().withName(pool.getKey());
            for (String serial : pool.getValue()) {
                final Device device = devices.stream()
                        .filter(d -> d.getSerial().equals(serial))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Device with the serial '" + serial + "' was not found"));
                if (device != null) {
                    poolBuilder.addDevice(device);
                }
            }
            pools.add(poolBuilder.build());
        }
        return pools;
    }
}
