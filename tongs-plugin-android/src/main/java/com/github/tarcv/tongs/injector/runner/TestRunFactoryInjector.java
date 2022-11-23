/*
 * Copyright 2020 TarCV
 * Copyright 2015 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.injector.runner;

import com.github.tarcv.tongs.api.TongsConfiguration;
import com.github.tarcv.tongs.runner.AndroidTestRunFactory;
import com.github.tarcv.tongs.runner.TestAndroidTestRunFactory;

import static com.github.tarcv.tongs.api.TongsConfiguration.TongsIntegrationTestRunType.STUB_PARALLEL_TESTRUN;

public class TestRunFactoryInjector {

    private TestRunFactoryInjector() {}

    public static AndroidTestRunFactory testRunFactory(TongsConfiguration configuration) {
        if (configuration.getTongsIntegrationTestRunType() == STUB_PARALLEL_TESTRUN) {
            return new TestAndroidTestRunFactory(configuration);
        } else {
            return new AndroidTestRunFactory(configuration);
        }
    }
}
