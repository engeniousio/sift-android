package io.engenious.sift

import io.engenious.sift.run.ResultData

interface Client {
    fun postTests(testCases: Set<TestIdentifier>)
    fun getEnabledTests(testPlan: String, status: Config.TestStatus, allTests: MutableSet<TestIdentifier>): Map<TestIdentifier, Int>
    fun createRun(testPlan: String): Int
    fun postResults(testPlan: String, result: ResultData)
    fun getConfiguration(testPlan: String): Config
}
