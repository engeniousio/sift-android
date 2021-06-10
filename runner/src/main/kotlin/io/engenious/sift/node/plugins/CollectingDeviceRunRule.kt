package io.engenious.sift.node.plugins

import com.github.tarcv.tongs.api.devices.Device
import com.github.tarcv.tongs.api.run.DeviceRunRule
import com.github.tarcv.tongs.api.run.DeviceRunRuleContext
import com.github.tarcv.tongs.api.run.DeviceRunRuleFactory
import java.util.Collections

class DeviceCollector(private val consumer: (Set<Device>) -> Unit) :
    DeviceRunRuleFactory<DeviceRunRule> {
    private val devices = Collections.synchronizedSet(mutableSetOf<Device>())

    override fun deviceRules(context: DeviceRunRuleContext): Array<out DeviceRunRule> {
        devices.add(context.device)

        return arrayOf(object : DeviceRunRule {
            override fun before() {
                synchronized(devices) {
                    consumer(devices)
                }
            }

            override fun after() {
                // no op
            }
        })
    }
}
