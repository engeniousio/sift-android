package io.engenious.sift

import com.github.tarcv.tongs.model.Device
import com.github.tarcv.tongs.runner.rules.TestCaseRuleContext
import com.github.tarcv.tongs.runner.rules.TestCaseRuleFactory

class ListingPlugin: TestCaseRuleFactory<CollectingTestCaseRule> {
    override fun testCaseRules(context: TestCaseRuleContext): Array<out CollectingTestCaseRule> {
        noOpDevices += context.pool.devices
        return arrayOf(collectingTestCaseRule)
    }

    companion object {
        val collectedTests
            get() = collectingTestCaseRule.testCases

        private val noOpDevices = ArrayList<Device>()
        private val collectingTestCaseRule by lazy { CollectingTestCaseRule(noOpDevices) }
    }
}
