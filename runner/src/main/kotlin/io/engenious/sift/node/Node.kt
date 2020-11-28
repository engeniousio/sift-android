@file:UseSerializers(DeviceSerializer::class)

package io.engenious.sift.node

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.Tongs
import com.github.tarcv.tongs.TongsRunner
import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.pooling.NoDevicesForPoolException
import com.github.tarcv.tongs.pooling.PoolLoader
import io.engenious.sift.FileConfig
import io.engenious.sift.Sift
import io.engenious.sift.Sift.Companion.setupCommonTongsConfiguration
import io.engenious.sift.node.serialization.DeviceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.koin.core.context.KoinContextHandler
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import java.nio.file.Files
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class Node(config: FileConfig) {
    init {
        val runnerConfiguration = Configuration.Builder()
            .setupCommonTongsConfiguration(config)
            .withOutput(Files.createTempDirectory(Sift.tempEmptyDirectoryName).toFile())
            .build(true)

        val tongs = Tongs(runnerConfiguration)
        val runnerModule = Tongs::class.memberProperties
            .single { it.name == "runnerModule" }
            .also { it.isAccessible = true }
            .get(tongs) as Module
        startKoin {
            modules(runnerModule)
        }
    }

    private val devices by lazy {
        val tongsRunner by KoinContextHandler.get().inject<TongsRunner>()
        val poolLoader = TongsRunner::class.declaredMemberProperties
            .single { it.name == "poolLoader" }
            .also { it.isAccessible = true }
            .get(tongsRunner) as PoolLoader

        try {
            poolLoader.loadPools()
                .single()
                .devices
        } catch (e: NoDevicesForPoolException) {
            emptyList<Device>()
        }
    }

    fun provideDevices(): ProvidedDevices = ProvidedDevices(devices)
}

@Serializable
data class ProvidedDevices(
    val devices: List<Device>
)