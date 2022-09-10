package io.engenious.sift.node.remote.plugins

import com.github.tarcv.tongs.api.result.StandardFileTypes
import com.github.tarcv.tongs.api.run.TestCaseRunRule
import com.github.tarcv.tongs.api.run.TestCaseRunRuleAfterArguments
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.run.TestCaseRunRuleFactory
import org.codehaus.plexus.util.cli.CommandLineUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SetupScriptPlugin(
    private val setUpScript: String?,
    private val tearDownScript: String?
) : TestCaseRunRuleFactory<TestCaseRunRule> {
    init {
        require(setUpScript?.isNotEmpty() != false)
        require(tearDownScript?.isNotEmpty() != false)
    }

    override fun testCaseRunRules(context: TestCaseRunRuleContext): Array<out TestCaseRunRule> {
        return arrayOf(object : TestCaseRunRule {
            val workDir = context.configuration.output
            val androidSdkPath = context.configuration.androidSdk
            val deviceId = context.device.serial
            val testId = context.testCaseEvent.testCase.displayName
            val testDir = context.fileManager.getFile(StandardFileTypes.DOT_WITHOUT_EXTENSION, "")
                .name
                .removeSuffix(".")

            override fun before() {
                setUpScript?.runScript()
            }

            override fun after(arguments: TestCaseRunRuleAfterArguments) {
                tearDownScript?.runScript()
            }

            private fun String.runScript() {
                lateinit var charSet: Charset
                var fileToDeleteAfter: Path? = null
                try {
                    val processBuilder: ProcessBuilder =
                        if (isExecutingOnWindows()) {
                            val isBatchFile = run {
                                val extension = this.substringAfterLast(".")
                                listOf("cmd", "bat").any { extension.equals(it, ignoreCase = true) }
                            }
                            val actualPath = if (isBatchFile) {
                                this
                            } else {
                                fileToDeleteAfter = copyToTempCmdFile(this)
                                fileToDeleteAfter.toString()
                            }

                            charSet = getWindowsConsoleCharset()

                            ProcessBuilder("cmd.exe", "/C", actualPath)
                                .setupProcess(androidSdkPath, deviceId, testId, testDir, workDir)
                        } else {
                            charSet = Charset.defaultCharset()

                            val firstLine = File(this).useLines {
                                it.firstOrNull()
                            }
                            if (firstLine?.startsWith(shebangPrefix) == true) {
                                ProcessBuilder(*convertShebangToArgs(firstLine))
                                    .setupProcess(androidSdkPath, deviceId, testId, testDir, workDir)
                            } else {
                                ProcessBuilder(System.getenv("SHELL"))
                                    .setupProcess(androidSdkPath, deviceId, testId, testDir, workDir)
                                    .redirectInput(File(this))
                            }
                        }

                    processBuilder
                        .start()
                        .run {
                            try {
                                redirectStreamToLog(inputStream, charSet)

                                waitFor(5, TimeUnit.MINUTES)
                                exitValue().let {
                                    require(it == 0) { "Script returned non-zero value $it" }
                                }
                            } finally {
                                if (isAlive) {
                                    destroyForcibly()
                                }
                            }
                        }
                } finally {
                    fileToDeleteAfter
                        ?.let { Files.deleteIfExists(it) }
                }
            }

        })
    }
}

private const val shebangPrefix = "#!"
private val logger = LoggerFactory.getLogger(SetupScriptPlugin::class.java)

private fun redirectStreamToLog(inputStream: InputStream, charSet: Charset) {
    thread(isDaemon = true, start = true) {
        inputStream.reader(charSet)
            .forEachLine { 
                logger.info(it)
            }
    }
}
private fun String.convertShebangToArgs(firstLine: String) = firstLine
    .removePrefix(shebangPrefix)
    .trim()
    .let { CommandLineUtils.translateCommandline(it) }
    .plus(this)

private fun getWindowsConsoleCharset(): Charset {
    return ProcessBuilder("cmd.exe", "/C", "chcp")
        .start()
        .inputStream
        .readAllBytes()
        .let { String(it, Charsets.ISO_8859_1) }
        .substringAfterLast(":")
        .trim()
        .let {
            listOf("cp$it", it)
                .firstOrNull { name -> Charset.isSupported(name) }
                ?.let { name -> Charset.forName(name) }
                ?: Charset.defaultCharset()
        }
}

fun isExecutingOnWindows() = System.getProperty("os.name").contains("windows", ignoreCase = true)

private fun copyToTempCmdFile(originalFile: String): Path {
    return Files.createTempFile(null, ".cmd")
        .apply {
            this.toFile().deleteOnExit()
            Files.write(
                this,
                Files.readAllBytes(Paths.get(originalFile)),
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        }
}

private fun ProcessBuilder.setupProcess(
    androidSdkPath: File, deviceId: String, testName: String, testFileStem: String, workDir: File
): ProcessBuilder {
    return apply {
        val adbPath = androidSdkPath.resolve("platform-tools")

        val env = environment()
        env += mapOf(
            "PATH" to "${adbPath}${File.pathSeparator}${env["PATH"]}",
            "ANDROID_SERIAL" to deviceId,
            "ANDROID_SDK_ROOT" to androidSdkPath.toString(),
            "TEST_NAME" to testName,
            "TEST_FILE_STEM" to testFileStem,
        )
    }
        .directory(workDir)
        .redirectErrorStream(true)
}
