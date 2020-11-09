package io.engenious.sift

import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.run.TestCaseRunRule
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.run.TestCaseRunRuleFactory
import com.github.tarcv.tongs.api.testcases.TestCaseRule
import com.github.tarcv.tongs.api.testcases.TestCaseRuleContext
import com.github.tarcv.tongs.api.testcases.TestCaseRuleFactory
import kotlinx.serialization.SerializationException
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED

class RunPlugin : TestCaseRuleFactory<TestCaseRule>, TestCaseRunRuleFactory<TestCaseRunRule> {
    override fun testCaseRules(context: TestCaseRuleContext): Array<out TestCaseRule> {
        val collectingTestCaseRule = CollectingTestCaseRule()
        val filteringTestCaseRule = FilteringTestCaseRule {
            siftClient.run {
                postTests(collectingTestCaseRule.testCases)
                getEnabledTests(testPlan, status)
            }
        }
        return arrayOf(collectingTestCaseRule, filteringTestCaseRule)
    }

    override fun testCaseRunRules(context: TestCaseRunRuleContext): Array<out TestCaseRunRule> {
        return arrayOf(ResultCollectingTestCaseRunRule(testResults))
    }

    companion object {
        @Suppress("ObjectPropertyName")
        private val _config = AtomicReference<FileConfig>()
        var config: FileConfig
            get() = _config.get() as FileConfig
            set(value) {
                validateConfigForRunning(value)
                _config.set(value)
            }

        val testPlan: String
            get() = validateConfigForRunning(config).first
        val status: FileConfig.TestStatus
            get() = validateConfigForRunning(config).second

        private fun validateConfigForRunning(value: FileConfig): Pair<String, FileConfig.TestStatus> {
            val testPlan = value.testPlan
                ?: throw SerializationException("Field 'testPlan' in the configuration file is required to run tests")
            val status = value.status
                ?: throw SerializationException("Field 'status' in the configuration file is required to run tests")
            return testPlan to status
        }

        val siftClient by lazy(SYNCHRONIZED) { SiftClient(config.token) }

        val testResults: MutableMap<TestIdentifier, Boolean> =
            Collections.synchronizedMap(HashMap<TestIdentifier, Boolean>())

        fun postResults() {
            siftClient.postResults(testResults.toMap())
        }
    }

    private class FilteringTestCaseRule(
        private val enabledTestCasesProvider: () -> Set<TestIdentifier>
    ) : TestCaseRule {
        private val enabledTestCases: Set<TestIdentifier> by lazy(SYNCHRONIZED) {
            enabledTestCasesProvider()
        }

        override fun filter(testCaseEvent: TestCaseEvent): Boolean {
            val testIdentifier = TestIdentifier.fromTestCase(testCaseEvent.testCase)
            return enabledTestCases.contains(testIdentifier)
        }
    }
}
