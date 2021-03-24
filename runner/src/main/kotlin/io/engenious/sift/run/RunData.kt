package io.engenious.sift.run

import io.engenious.sift.TestIdentifier

data class RunData(
    val runId: Int,
    val enabledTests: Map<TestIdentifier, Int>
)
