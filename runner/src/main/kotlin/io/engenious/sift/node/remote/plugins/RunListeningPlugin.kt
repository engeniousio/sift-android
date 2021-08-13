package io.engenious.sift.node.remote.plugins

import com.github.tarcv.tongs.api.run.RunRule
import com.github.tarcv.tongs.api.run.RunRuleContext
import com.github.tarcv.tongs.api.run.RunRuleFactory

class RunListeningPlugin(
    private val onBeforeRun: () -> Unit = {},
    private val onAfterRun: () -> Unit = {},
) : RunRuleFactory<RunRule> {
    override fun runRules(context: RunRuleContext): Array<out RunRule> {
        return arrayOf(object : RunRule {
            override fun after() {
                onAfterRun()
            }

            override fun before() {
                onBeforeRun()
            }
        })
    }
}
