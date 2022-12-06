/*
 * Copyright 2020 TarCV
 * Copyright 2018 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.model;

import com.github.tarcv.tongs.api.devices.Device;
import com.github.tarcv.tongs.api.devices.Pool;
import com.github.tarcv.tongs.api.run.TestCaseEvent;
import org.junit.Before;
import org.junit.Test;

import static com.github.tarcv.tongs.api.run.TestCaseEventExtKt.aTestCaseEvent;
import static com.github.tarcv.tongs.api.testcases.TestCaseExtKt.aTestCase;
import static com.github.tarcv.tongs.model.AndroidDevice.Builder.aDevice;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class PoolTestCaseAccumulatorTestFailure {

    private final Device A_DEVICE = aDevice()
            .withSerial("a_device")
            .build();
    private final Device ANOTHER_DEVICE = aDevice()
            .withSerial("another_device")
            .build();

    private final Pool A_POOL = Pool.Builder.aDevicePool()
            .withName("a_pool")
            .addDevice(A_DEVICE)
            .build();

    private final Pool ANOTHER_POOL = Pool.Builder.aDevicePool()
            .withName("another_pool")
            .addDevice(ANOTHER_DEVICE)
            .build();
    
    private final TestCaseEvent A_TEST_CASE =
            aTestCaseEvent(aTestCase("A_class", "a_method"));
    private final TestCaseEvent ANOTHER_TEST_CASE =
            aTestCaseEvent(aTestCase("A_class", "another_method"));

    PoolTestCaseFailureAccumulator subject;

    @Before
    public void setUp() throws Exception {
        subject = new PoolTestCaseFailureAccumulator();
    }

    @Test
    public void shouldAggregateCountForSameTestCaseAcrossMultipleDevices() throws Exception {

        subject.record(A_POOL, A_TEST_CASE);
        subject.record(A_POOL, A_TEST_CASE);

        int actualCount = subject.getCount(A_TEST_CASE);

        assertThat(actualCount, equalTo(2));
    }

    @Test
    public void shouldCountTestsPerPool() throws Exception {
        subject.record(A_POOL, A_TEST_CASE);
        subject.record(A_POOL, A_TEST_CASE);

        int actualCount = subject.getCount(A_POOL, A_TEST_CASE);

        assertThat(actualCount, equalTo(2));
    }

    @Test
    public void shouldAggregateCountForSameTestCaseAcrossMultiplePools() throws Exception {

        subject.record(A_POOL, A_TEST_CASE);
        subject.record(ANOTHER_POOL, A_TEST_CASE);

        int actualCount = subject.getCount(A_TEST_CASE);

        assertThat(actualCount, equalTo(2));
    }

    @Test
    public void shouldNotReturnTestCasesForDifferentPool() throws Exception {
        subject.record(A_POOL, A_TEST_CASE);

        int actualCountForAnotherDevice = subject.getCount(ANOTHER_POOL, A_TEST_CASE);

        assertThat(actualCountForAnotherDevice, equalTo(0));
    }

    @Test
    public void shouldAccumulateDifferentTestCasesForSamePool() throws Exception {
        subject.record(A_POOL, A_TEST_CASE);
        subject.record(A_POOL, ANOTHER_TEST_CASE);

        int actualCount = subject.getCount(A_POOL, A_TEST_CASE);
        int anotherActualCount = subject.getCount(A_POOL, ANOTHER_TEST_CASE);

        assertThat(actualCount, equalTo(1));
        assertThat(anotherActualCount, equalTo(1));
    }
}
