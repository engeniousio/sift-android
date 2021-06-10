package io.engenious.sift.node.plugins.blocker

import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.api.testcases.TestCaseProvider
import com.github.tarcv.tongs.api.testcases.TestCaseProviderContext
import com.github.tarcv.tongs.api.testcases.TestCaseProviderFactory

class LoopingTestCaseProvider : TestCaseProvider {
    companion object {
        val loopingTestCase = TestCase(
            LoopingTag.javaClass,
            "looper",
            "Looper",
            "looper",
            listOf("looper", "Looper", "looper"),
            includedDevices = LoopingDeviceProvider.loopingDevices
        )
    }

    override fun loadTestSuite(): Collection<TestCase> = listOf(
        loopingTestCase
    )
}
class LoopingTestCaseProviderFactory : TestCaseProviderFactory<TestCaseProvider> {
    override fun suiteLoaders(context: TestCaseProviderContext): Array<out TestCaseProvider> = arrayOf(
        LoopingTestCaseProvider()
    )
}
