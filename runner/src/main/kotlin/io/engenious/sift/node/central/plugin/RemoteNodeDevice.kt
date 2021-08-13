package io.engenious.sift.node.central.plugin

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.devices.Diagnostics
import com.github.tarcv.tongs.api.devices.DisplayGeometry
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.RunTesult
import com.github.tarcv.tongs.api.testcases.TestCase
import io.engenious.sift.node.serialization.RemoteDevice
import io.engenious.sift.node.serialization.RemoteTestCaseRunResult.Companion.toTestCaseRunResult

class RemoteNodeDevice(
    private val node: RemoteSshNode,
    private val device: RemoteDevice
) : Device() {
    private val staticUnusedValue = Any()

    private val identifier = Identifier(
        node.uniqueIdentifier,
        device.uniqueIdentifier
    )

    fun runTest(pool: Pool, testCase: TestCase, timeoutMillis: Long): RunTesult {
        return node.client.runTest(device, testCase, timeoutMillis)
            .toTestCaseRunResult(pool)
    }

    override fun getHost(): String = node.name

    override fun getSerial(): String = node.name + "/" + device.serial

    override fun getManufacturer(): String = device.manufacturer

    override fun getModelName(): String = device.modelName

    override fun getOsApiLevel(): Int = device.osApiLevel

    override fun getLongName(): String = device.longName + " at " + node.name

    override fun getDeviceInterface(): Any = staticUnusedValue

    override fun isTablet(): Boolean = device.isTablet

    override fun getGeometry(): DisplayGeometry? = device.geometry

    override fun getSupportedVisualDiagnostics(): Diagnostics = device.supportedVisualDiagnostics

    override fun getUniqueIdentifier(): Identifier = identifier

    data class Identifier(
        val nodeIdentifier: Any,
        val deviceIdentifier: Any
    )
}
