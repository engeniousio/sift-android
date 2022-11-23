/*
 * Copyright 2021 TarCV
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
package com.github.tarcv.tongs.injector

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.api.devices.DeviceProvider
import com.github.tarcv.tongs.api.devices.DeviceProviderContext
import com.github.tarcv.tongs.api.devices.DeviceProviderFactory
import com.github.tarcv.tongs.plugin.android.LocalDeviceProviderFactory
import com.github.tarcv.tongs.pooling.PoolLoader
import com.github.tarcv.tongs.pooling.geometry.CommandOutputLogger
import com.github.tarcv.tongs.pooling.geometry.GeometryCommandOutputLogger
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent

val poolingModule = module(createdAtStart = modulesCreatedAtStart) {
    factory<CommandOutputLogger> { (command: String?) ->
        GeometryCommandOutputLogger(get<Configuration>().output, command)
    }

    factory {
        fun createProviders(ruleManagerFactory: RuleManagerFactory): DeviceProviderManager {
            val defaultProviderFactories: List<DeviceProviderFactory<DeviceProvider>> = listOf(
                LocalDeviceProviderFactory()
            )
            return ruleManagerFactory.create(
                DeviceProviderFactory::class.java,
                defaultProviderFactories
            ) { factory, context: DeviceProviderContext -> factory.deviceProviders(context) }
        }

        val loaders = createProviders(get())
        PoolLoader(KoinJavaComponent.get(Configuration::class.java), loaders)
    }
}
