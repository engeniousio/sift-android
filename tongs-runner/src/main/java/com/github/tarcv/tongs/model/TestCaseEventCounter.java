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

import com.github.tarcv.tongs.api.run.TestCaseEvent;
import com.google.common.base.Objects;

import java.util.concurrent.atomic.AtomicInteger;

public class TestCaseEventCounter {

    public static final TestCaseEventCounter EMPTY = new TestCaseEventCounter(null, 0);

    private final TestCaseEvent testCaseEvent;
    private final AtomicInteger count;

    public TestCaseEventCounter(TestCaseEvent testCaseEvent, int initialCount) {
        this.testCaseEvent = testCaseEvent;
        this.count = new AtomicInteger(initialCount);
    }

    public int increaseCount() {
        return count.incrementAndGet();
    }

    public TestCaseEvent getTestCaseEvent() {
        return testCaseEvent;
    }

    public int getCount() {
        return count.get();
    }

    @Override
    public int hashCode() {
        return testCaseEvent.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        final TestCaseEventCounter other = (TestCaseEventCounter) obj;
        return Objects.equal(this.testCaseEvent, other.testCaseEvent);
    }
}
