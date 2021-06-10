@file:UseSerializers(
    InstantSerializer::class,
    TableJsonSerializer::class,
    StackTraceSerializer::class
)

package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.FileHtmlReportData
import com.github.tarcv.tongs.api.result.FileMonoTextReportData
import com.github.tarcv.tongs.api.result.FileTableReportData
import com.github.tarcv.tongs.api.result.HtmlReportData
import com.github.tarcv.tongs.api.result.ImageReportData
import com.github.tarcv.tongs.api.result.LinkedFileReportData
import com.github.tarcv.tongs.api.result.MonoTextReportData
import com.github.tarcv.tongs.api.result.SimpleHtmlReportData
import com.github.tarcv.tongs.api.result.SimpleMonoTextReportData
import com.github.tarcv.tongs.api.result.SimpleTableReportData
import com.github.tarcv.tongs.api.result.StackTrace
import com.github.tarcv.tongs.api.result.StandardFileTypes
import com.github.tarcv.tongs.api.result.Table
import com.github.tarcv.tongs.api.result.TableReportData
import com.github.tarcv.tongs.api.result.TestCaseFile
import com.github.tarcv.tongs.api.result.TestCaseFileManager
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.result.TestReportData
import com.github.tarcv.tongs.api.result.VideoReportData
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.system.io.TestCaseFileManagerImpl
import io.engenious.sift.node.serialization.RemoteTestCase.Companion.toTestCase
import io.engenious.sift.node.serialization.SurrogateTestReportData.WritableTestReportData.SurrogateHtmlReportData
import io.engenious.sift.node.serialization.SurrogateTestReportData.WritableTestReportData.SurrogateMonoTextReportData
import io.engenious.sift.node.serialization.SurrogateTestReportData.WritableTestReportData.SurrogateTableReportData
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.koin.core.context.KoinContextHandler
import java.time.Instant
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

class TestCaseRunResultSerializer(
    private val pool: Pool
) : SurrogateSerializer<TestCaseRunResult, TestCaseRunResultSerializer.SurrogateTestCaseRunResult>(
    SurrogateTestCaseRunResult.serializer()
) {
    override fun toSurrogate(value: TestCaseRunResult) = SurrogateTestCaseRunResult(
        value.device as RemoteDevice,
        RemoteTestCase.fromTestCase(value.testCase),
        value.status,
        value.stackTraces,
        value.startTimestampUtc,
        value.endTimestampUtc,
        value.netStartTimestampUtc,
        value.netEndTimestampUtc,
        value.totalFailureCount,
        value.additionalProperties,
        value.coverageReport?.let { TestCaseFileSerializer.toSurrogate(it) },
        value.data.map {
            TestReportDataSerializer.toSurrogate(it)
        }
    )

    override fun fromSurrogate(surrogate: SurrogateTestCaseRunResult): TestCaseRunResult {
        val testFileManager = TestCaseFileManagerImpl(
            KoinContextHandler.get().get(),
            pool,
            surrogate.device,
            surrogate.testCase.toTestCase()
        )
        val testCaseFileSerializer = TestCaseFileSerializer(testFileManager)

        val candidate = TestCaseRunResult(
            pool,
            surrogate.device,
            surrogate.testCase.toTestCase(),
            surrogate.status,
            surrogate.stackTraces,
            surrogate.startTimestampUtc,
            surrogate.endTimestampUtc,
            surrogate.netStartTimestampUtc,
            surrogate.netEndTimestampUtc,
            surrogate.totalFailureCount,
            surrogate.additionalProperties,
            surrogate.coverageReport?.let { testCaseFileSerializer.fromSurrogate(it) },
            surrogate.data.map {
                TestReportDataSerializer.fromSurrogate(it, testCaseFileSerializer)
            }
        )

        return if (candidate.totalFailureCount > surrogate.totalFailureCount) {
            candidate.copy(baseTotalFailureCount = surrogate.totalFailureCount - 1)
        } else {
            candidate
        }
    }

    @Serializable
    data class SurrogateTestCaseRunResult(
        val device: RemoteDevice,
        val testCase: RemoteTestCase,

        val status: ResultStatus,
        val stackTraces: List<StackTrace>,
        val startTimestampUtc: Instant,
        val endTimestampUtc: Instant = Instant.EPOCH,
        val netStartTimestampUtc: Instant?,
        val netEndTimestampUtc: Instant?,
        val totalFailureCount: Int,
        val additionalProperties: Map<String, String>,
        val coverageReport: SurrogateTestCaseFile? = null,
        val data: List<SurrogateTestReportData>
    )
}

private object TestReportDataSerializer {
    fun toSurrogate(value: TestReportData): SurrogateTestReportData = when (value) {
        is SimpleMonoTextReportData, is FileMonoTextReportData -> SurrogateMonoTextReportData(
            (value as MonoTextReportData).title,
            value.monoText,
            value.type,
            value
                .takeIf { value is FileMonoTextReportData }
                ?.extractFile("monoTextPath")
        )
        is SimpleHtmlReportData, is FileHtmlReportData -> SurrogateHtmlReportData(
            (value as HtmlReportData).title,
            value.html,
            value
                .takeIf { value is FileHtmlReportData }
                ?.extractFile("htmlPath")
        )
        is SimpleTableReportData, is FileTableReportData -> SurrogateTableReportData(
            (value as TableReportData).title,
            Table.TableJson(
                value.table.headers.map { it.title },
                value.table.rows.map { row ->
                    row.cells.map { it.text }
                }
            ),
            value
                .takeIf { value is FileTableReportData }
                ?.extractFile("tablePath")
        )
        is ImageReportData -> SurrogateTestReportData.SurrogateImageReportData(
            value.title,
            value.extractFile("image")
        )
        is VideoReportData -> SurrogateTestReportData.SurrogateVideoReportData(
            value.title,
            value.extractFile("video")
        )
        is LinkedFileReportData -> SurrogateTestReportData.SurrogateLinkedFileReportData(
            value.title,
            TestCaseFileSerializer.toSurrogate(value.file)
        )
    }

    private fun TestReportData.extractFile(fieldName: String): SurrogateTestCaseFile {
        return TestReportData::class.declaredMemberProperties
            .single { it.name == fieldName }
            .let {
                @Suppress("UNCHECKED_CAST")
                (it as KProperty1<TestReportData, TestCaseFile>)
                    .get(this)
            }
            .let {
                TestCaseFileSerializer.toSurrogate(it)
            }
    }

    fun fromSurrogate(
        surrogate: SurrogateTestReportData,
        testCaseFileSerializer: TestCaseFileSerializer
    ): TestReportData {
        return when (surrogate) {
            is SurrogateMonoTextReportData -> {
                if (surrogate.file != null) {
                    FileMonoTextReportData(
                        surrogate.title,
                        surrogate.type,
                        testCaseFileSerializer.fromSurrogate(surrogate.file)
                    )
                } else {
                    SimpleMonoTextReportData(surrogate.title, surrogate.type, surrogate.monoText)
                }
            }
            is SurrogateHtmlReportData -> {
                if (surrogate.file != null) {
                    FileHtmlReportData(surrogate.title, testCaseFileSerializer.fromSurrogate(surrogate.file))
                } else {
                    SimpleHtmlReportData(surrogate.title, surrogate.html)
                }
            }
            is SurrogateTableReportData -> {
                if (surrogate.file != null) {
                    FileTableReportData(surrogate.title, testCaseFileSerializer.fromSurrogate(surrogate.file)) {
                        Json.decodeFromString(it.readText())
                    }
                } else {
                    SimpleTableReportData(
                        surrogate.title,
                        Table(
                            surrogate.table.headers ?: emptyList(),
                            surrogate.table.rows ?: emptyList()
                        )
                    )
                }
            }
            is SurrogateTestReportData.SurrogateImageReportData -> {
                ImageReportData(surrogate.title, testCaseFileSerializer.fromSurrogate(surrogate.image))
            }
            is SurrogateTestReportData.SurrogateVideoReportData -> {
                VideoReportData(surrogate.title, testCaseFileSerializer.fromSurrogate(surrogate.video))
            }
            is SurrogateTestReportData.SurrogateLinkedFileReportData -> {
                LinkedFileReportData(surrogate.title, testCaseFileSerializer.fromSurrogate(surrogate.file))
            }
        }
    }
}

private class TestCaseFileSerializer(private val testFileManager: TestCaseFileManager) {
    companion object {
        fun toSurrogate(value: TestCaseFile): SurrogateTestCaseFile {
            val fileBytes = value.toFile().run {
                if (isFile) {
                    readBytes()
                } else {
                    null
                }
            }

            return SurrogateTestCaseFile(
                value.fileType as StandardFileTypes,
                value.suffix,
                fileBytes
            )
        }
    }

    fun fromSurrogate(surrogate: SurrogateTestCaseFile): TestCaseFile {
        val testCaseFile = TestCaseFile(
            testFileManager,
            surrogate.fileType,
            surrogate.suffix
        )
        if (surrogate.data != null) {
            testCaseFile
                .create()
                .writeBytes(surrogate.data)
        }
        return testCaseFile
    }
}

@Serializable
sealed class SurrogateTestReportData {
    @Serializable
    sealed class WritableTestReportData : SurrogateTestReportData() {
        abstract val file: SurrogateTestCaseFile?

        @Serializable
        data class SurrogateMonoTextReportData(
            val title: String,
            val monoText: String,
            val type: SimpleMonoTextReportData.Type,
            override val file: SurrogateTestCaseFile?
        ) : WritableTestReportData()

        @Serializable
        data class SurrogateHtmlReportData(
            val title: String,
            val html: String,
            override val file: SurrogateTestCaseFile?
        ) : WritableTestReportData()

        @Serializable
        data class SurrogateTableReportData(
            val title: String,
            val table: Table.TableJson,
            override val file: SurrogateTestCaseFile?
        ) : WritableTestReportData()
    }

    @Serializable
    data class SurrogateImageReportData(
        val title: String,
        val image: SurrogateTestCaseFile
    ) : SurrogateTestReportData()

    @Serializable
    data class SurrogateVideoReportData(
        val title: String,
        val video: SurrogateTestCaseFile
    ) : SurrogateTestReportData()

    @Serializable
    data class SurrogateLinkedFileReportData(
        val title: String,
        val file: SurrogateTestCaseFile
    ) : SurrogateTestReportData()
}

@Serializable
data class SurrogateTestCaseFile(
    val fileType: StandardFileTypes,
    val suffix: String,
    val data: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SurrogateTestCaseFile

        if (fileType != other.fileType) return false
        if (suffix != other.suffix) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileType.hashCode()
        result = 31 * result + suffix.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}
