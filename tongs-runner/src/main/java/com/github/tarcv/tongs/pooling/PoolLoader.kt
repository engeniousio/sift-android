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

package com.github.tarcv.tongs.pooling

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.injector.DeviceProviderManager
import com.github.tarcv.tongs.plugin.DeviceProviderContextImpl
import org.slf4j.LoggerFactory
import java.lang.String.format

class PoolLoader(private val configuration: Configuration, private val deviceProviderManager: DeviceProviderManager) {

    @Throws(NoDevicesForPoolException::class, NoPoolLoaderConfiguredException::class)
    fun loadPools(): Collection<Pool> {
        val devices = deviceProviderManager
                .createRulesFrom {
                    configuration ->
                    DeviceProviderContextImpl(configuration)
                }
                .asSequence()
                .map { deviceProvider ->
                    val deviceList = ArrayList(deviceProvider.provideDevices())
                    logger.info("Got {} devices from {}", deviceList.size, deviceProvider.javaClass.simpleName)
                    deviceList
                }
                .flatMap { deviceList -> deviceList.asSequence() }
                .toList()

        if (devices.isEmpty()) {
            throw NoDevicesForPoolException("No devices found.")
        }

        val devicePoolLoader = pickPoolLoader(configuration)
        logger.info("Picked {}", devicePoolLoader.javaClass.simpleName)
        val pools = devicePoolLoader.loadPools(devices)
        pools?.forEach {
            logger.info("Pool name ${it.name}, devices ${it.devices}")
        }
        if (pools.isEmpty()) {
            throw IllegalArgumentException("No pools were found with your configuration. Please review connected devices")
        }
        log(pools)
        for (pool in pools) {
            if (pool.isEmpty) {
                throw NoDevicesForPoolException(format("Pool %s is empty", pool.name))
            }
        }

        return pools
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PoolLoader::class.java)

        private fun log(configuredPools: Collection<Pool>) {
            for (pool in configuredPools) {
                logger.debug(pool.toString())
            }
        }

        @Throws(NoPoolLoaderConfiguredException::class)
        private fun pickPoolLoader(configuration: Configuration): DevicePoolLoader {
            val poolingStrategy = configuration.poolingStrategy

            if (poolingStrategy.manual != null) {
                return SerialBasedDevicePoolLoader(poolingStrategy.manual)
            }

            if (poolingStrategy.splitTablets != null && poolingStrategy.splitTablets) {
                return DefaultAndTabletDevicePoolLoader()
            }

            if (poolingStrategy.computed != null) {
                return ComputedDevicePoolLoader(poolingStrategy.computed)
            }

            if (poolingStrategy.eachDevice != null && poolingStrategy.eachDevice) {
                return EveryoneGetsAPoolLoader()
            }

            throw NoPoolLoaderConfiguredException("Could not determine which how to load pools to use based on your configuration")
        }
    }
}
