/*
 * Copyright 2021 TarCV
 * Copyright 2016 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.injector

import com.github.tarcv.tongs.device.DeviceGeometryRetriever
import com.github.tarcv.tongs.device.DisplayGeometryRetrievalStrategy
import com.github.tarcv.tongs.pooling.geometry.CommandOutputLogger
import com.github.tarcv.tongs.pooling.geometry.RegexDisplayGeometryRetrievalStrategy
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val deviceGeometryModule = module(createdAtStart = modulesCreatedAtStart) {
    factory {
        fun createRegexDisplayGeometryRetrievalStrategy(
            command: String, regex: String
        ): DisplayGeometryRetrievalStrategy {
            val commandOutputLogger by inject<CommandOutputLogger> { parametersOf(command) }
            return RegexDisplayGeometryRetrievalStrategy(
                command,
                commandOutputLogger,
                regex
            )
        }

        /**
         * Nexus S, Samsumg GT-P5110.
         * Also (h\\d+)dp and (w\\d+)dp if you want them...
         */
        fun swxxxdp(): DisplayGeometryRetrievalStrategy {
            return createRegexDisplayGeometryRetrievalStrategy("dumpsys window windows", "\\s(sw\\d+)dp\\s")
        }

        /**
         * Nexus 7 - sw600 with [800 x 1280] * 213 / 160 = [600, 880]dp
         */
        fun baseDisplay(): DisplayGeometryRetrievalStrategy {
            return createRegexDisplayGeometryRetrievalStrategy(
                "dumpsys display",
                "mBaseDisplayInfo=DisplayInfo\\{\"Built-in Screen\",.* largest app (\\d+) x (\\d+).*density (\\d+)"
            )
        }

        /**
         * Changed for Samsumg GT-P9110 - XScale is Touch screen RAW scaling! For Nexus S, scale="" means 1.0
         */
        fun gti91100(): DisplayGeometryRetrievalStrategy {
            return createRegexDisplayGeometryRetrievalStrategy(
                "dumpsys window",
                "(?s)SurfaceWidth: (\\d+)px\\s*SurfaceHeight: (\\d+)px.*?XScale: ()"
            )
        }

        DeviceGeometryRetriever(listOf(
            swxxxdp(), baseDisplay(), gti91100()
        ))
    }
}