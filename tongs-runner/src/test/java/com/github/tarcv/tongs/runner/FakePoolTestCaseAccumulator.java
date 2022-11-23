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
import com.github.tarcv.tongs.model.PoolTestCaseAccumulator;
import com.github.tarcv.tongs.api.run.TestCaseEvent;

public class FakePoolTestCaseAccumulator implements PoolTestCaseAccumulator {

    private int count = 0;

    public static FakePoolTestCaseAccumulator aFakePoolTestCaseAccumulator(){
        return new FakePoolTestCaseAccumulator();
    }

    public FakePoolTestCaseAccumulator thatAlwaysReturns(int count){
        this.count = count;
        return this;
    }

    @Override
    public void record(Pool pool, TestCaseEvent testCaseEvent) {
    }

    @Override
    public int getCount(Pool pool, TestCaseEvent testCaseEvent) {
        return count;
    }

    @Override
    public int getCount(TestCaseEvent testCaseEvent) {
        return count;
    }
}
