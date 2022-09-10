package io.engenious.sift.node.remote.plugins

import com.github.tarcv.tongs.Configuration
import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.api.run.TestCaseEvent
import com.github.tarcv.tongs.api.run.TestCaseRunRuleAfterArguments
import com.github.tarcv.tongs.api.run.TestCaseRunRuleContext
import com.github.tarcv.tongs.api.testcases.TestCase
import com.github.tarcv.tongs.injector.ActualConfiguration
import com.github.tarcv.tongs.system.io.TestCaseFileManagerImpl
import com.github.tarcv.tongs.system.io.TongsFileManager
import io.engenious.sift.node.extractProperty
import io.engenious.sift.node.remote.plugins.blocker.LoopingDevice
import org.apache.log4j.Logger
import org.apache.log4j.SimpleLayout
import org.apache.log4j.WriterAppender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.LoggerFactory
import org.slf4j.impl.Log4jLoggerAdapter
import java.io.File
import java.io.StringWriter
import java.lang.Thread.sleep
import java.time.Instant

class SetupScriptPluginTest {
    private val expectedPackage = "pkg"
    private val expectedClass = "clazz"
    private val expectedMethodWithUnsafeChars = "methodWithUnsafeCharacters-<>!#;"
    private val expectedFullReference = "${expectedPackage}.${expectedClass}#${expectedMethodWithUnsafeChars}"

    private val expectedSdkDir: File by lazy {
        tempDir.newFolder("sdk")
    }
    private val expectedDevice = LoopingDevice

    private val context by lazy {
        val configuration = Configuration.Builder()
            .withAndroidSdk(expectedSdkDir)
            .withApplicationPackage("package.app")
            .withInstrumentationPackage("package.app.test")
            .withTestRunnerClass("Runner")
            .withOutput(File("."))
            .build(false)
            .let { ActualConfiguration(it) }

        val pool = Pool.Builder()
            .addDevice(expectedDevice)
            .withName("testPool")
            .build()
        val testCase = TestCase(
            SetupScriptPluginTest::class.java,
            expectedPackage,
            expectedClass,
            expectedMethodWithUnsafeChars,
            listOf(expectedFullReference),
            emptyMap(),
            emptyList(),
            setOf(expectedDevice),
            Any()
        )

        TestCaseRunRuleContext(
            configuration,
            TestCaseFileManagerImpl(
                TongsFileManager(File("nonExistingFile")),
                pool,
                expectedDevice,
                testCase
            ),
            pool,
            expectedDevice,
            TestCaseEvent(
                testCase,
                emptyList(),
                0
            ),
            Instant.MIN
        )
    }

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun justSetUpScriptWithoutExtensionShouldBeExecuted() {
        val log = takeLog {
            SetupScriptPlugin(scriptFile("setup_scripts/simple_echo"), null)
                .testCaseRunRules(context)
                .single()
                .before()
        }
        assert(log.contains("SCRIPT_WAS_EXECUTED")) {
            "No expected substring in: $log"
        }
    }

    @Test
    fun scriptShouldHaveExpectedVariables() {
        val script = if (isExecutingOnWindows()) {
            "setup_scripts/variables.cmd"
        } else {
            "setup_scripts/variables.sh"
        }
        val log = takeLog(5_000) {
            SetupScriptPlugin(scriptFile(script), null)
                .testCaseRunRules(context)
                .single()
                .before()
        }
        val vars = log.lines()
            .map {
                val parts = it.split("=", limit = 2)
                if (parts.size != 2) {
                    return@map null
                }

                val value = if (parts[0].contains("declare ")) {
                    parts[1].removeSurrounding("\"")
                } else {
                    parts[1]
                }
                val key = parts[0].substringAfterLast(" ")

                key to value
            }
            .filterNotNull()
            .toMap()

        assertEquals(
            "ANDROID_SERIAL",
            expectedDevice.serial,
            vars["ANDROID_SERIAL"]
        )
        assertEquals(
            "ANDROID_SDK_ROOT",
            expectedSdkDir.toString(),
            vars["ANDROID_SDK_ROOT"]
        )
        assertEquals(
            "First component of PATH",
            expectedSdkDir.resolve("platform-tools").toString(),
            vars.getValue("PATH").split(File.pathSeparator).first()
        )
        assertEquals(
            "TEST_NAME",
            expectedFullReference,
            vars["TEST_NAME"]
        )

        run {
            val testFileStem = requireNotNull(vars["TEST_FILE_STEM"])
            assertNotEquals(
                "TEST_FILE_STEM",
                vars["TEST_NAME"],
                testFileStem
            )

            // TEST_FILE_STEM should be a safe filename, thus it should be possible to create a file with the name
            tempDir.newFile(testFileStem)
        }
    }

    @Test
    fun justTearDownScriptWithoutExtensionShouldBeExecuted() {
        val log = takeLog {
            SetupScriptPlugin(null, scriptFile("setup_scripts/simple_echo"))
                .testCaseRunRules(context)
                .single()
                .after(TestCaseRunRuleAfterArguments(TestCaseRunResult(
                    context.pool,
                    context.device,
                    context.testCaseEvent.testCase,
                    ResultStatus.FAIL,
                    emptyList(),
                    Instant.EPOCH,
                    Instant.EPOCH,
                    null,
                    null,
                    1,
                    emptyMap(),
                    null,
                    emptyList()
                )))
        }
        assert(log.contains("SCRIPT_WAS_EXECUTED")) {
            "No expected substring in: $log"
        }
    }

    @Test
    fun scriptWithJavaShebangShouldBeExecuted() {
        assumeTrue("test should be run on *nix compatible OS", !isExecutingOnWindows())

        val log = takeLog {
            SetupScriptPlugin(null, scriptFile("setup_scripts/java_shebang.sh"))
                .testCaseRunRules(context)
                .single()
                .after(
                    TestCaseRunRuleAfterArguments(
                        TestCaseRunResult(
                            context.pool,
                            context.device,
                            context.testCaseEvent.testCase,
                            ResultStatus.FAIL,
                            emptyList(),
                            Instant.EPOCH,
                            Instant.EPOCH,
                            null,
                            null,
                            1,
                            emptyMap(),
                            null,
                            emptyList()
                        )
                    )
                )
        }
        assert(log.contains("Java shebang works")) {
            "No expected substring in: $log"
        }
    }

    private inline fun takeLog(additionalWaitMs: Long = 1_000, block: () -> Unit): String {
        val pluginLogger: Logger = (LoggerFactory.getLogger(SetupScriptPlugin::class.java) as Log4jLoggerAdapter)
            .extractProperty("logger") as Logger
        return StringWriter()
            .use { logWriter ->
                pluginLogger.addAppender(WriterAppender(SimpleLayout(), logWriter))
                block()

                sleep(additionalWaitMs)
                logWriter
            }
            .toString()
    }

    private fun scriptFile(resourcePath: String): String {
        val scriptFile = tempDir.newFile()
        scriptFile.outputStream().use { outputStream ->
            javaClass.classLoader.getResourceAsStream(resourcePath)!!.use { resourceStream ->
                resourceStream.transferTo(
                    outputStream
                )
            }
        }
        return scriptFile.toString()
    }
}