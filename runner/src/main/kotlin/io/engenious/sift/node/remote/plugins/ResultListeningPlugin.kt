package io.engenious.sift.node.remote.plugins

import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.TestCaseRunRule
import com.github.tarcv.tongs.api.run.TestCaseRunRuleAfterArguments
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.run.TestCaseRunRuleFactory

class ResultListeningPlugin(private val resultConsumer: (TestCaseRunResult) -> Unit) : TestCaseRunRuleFactory<TestCaseRunRule> {

    override fun testCaseRunRules(context: TestCaseRunRuleContext): Array<out TestCaseRunRule> =
        arrayOf(object : TestCaseRunRule {
            override fun after(arguments: TestCaseRunRuleAfterArguments) {
                resultConsumer(arguments.result)
            }

            override fun before() {
                // no op
            }
        })
}
