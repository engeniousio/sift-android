package io.engenious.sift

import com.github.tarcv.tongs.api.run.PoolRunRule

class RunListenerRule(private val onPoolStart: () -> Unit) : PoolRunRule {
    override fun after() {
        // No-op
    }

    override fun before() {
        onPoolStart.invoke()
    }

}
