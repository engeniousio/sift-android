/*
 * Copyright 2018 TarCV
 * Copyright 2018 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.runner;

import com.github.tarcv.tongs.api.devices.Pool;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

public class FakeProgressReporterTrackers extends AbstractMap<Pool, PoolProgressTracker> implements Map<Pool, PoolProgressTracker> {

    private PoolProgressTracker poolProgressTracker;

    public static FakeProgressReporterTrackers aFakeProgressReporterTrackers() {
        return new FakeProgressReporterTrackers();
    }

    public FakeProgressReporterTrackers thatAlwaysReturns(PoolProgressTracker poolProgressTracker) {
        this.poolProgressTracker = poolProgressTracker;
        return this;
    }

    @Override
    public Set<Entry<Pool, PoolProgressTracker>> entrySet() {
        return null;
    }

    @Override
    public PoolProgressTracker get(Object key) {
        return poolProgressTracker;
    }

    @Override
    public boolean containsKey(Object key) {
        return true;
    }
}
