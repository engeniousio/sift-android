package io.engenious.sift.run

import io.engenious.sift.FilledTestResult
import io.engenious.sift.TestIdentifier

data class ResultData(
    val runId: Int,
    val results: MutableMap<TestIdentifier, FilledTestResult> = mutableMapOf()
)
