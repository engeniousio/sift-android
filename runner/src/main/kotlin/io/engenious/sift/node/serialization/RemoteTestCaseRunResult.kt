@file:UseSerializers(
    InstantSerializer::class,
    TableJsonSerializer::class,
    StackTraceSerializer::class
)

package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.devices.Pool
import com.github.tarcv.tongs.api.result.SimpleMonoTextReportData
import com.github.tarcv.tongs.api.result.StackTrace
import com.github.tarcv.tongs.api.result.StandardFileTypes
import com.github.tarcv.tongs.api.result.Table
import com.github.tarcv.tongs.api.result.TestCaseRunResult
import com.github.tarcv.tongs.api.run.ResultStatus
import com.github.tarcv.tongs.system.io.TestCaseFileManagerImpl
import io.engenious.sift.node.serialization.RemoteDevice.Companion.toLocalDevice
import io.engenious.sift.node.serialization.RemoteTestCase.Companion.toTestCase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.koin.core.context.KoinContextHandler
import java.time.Instant

@Serializable
data class RemoteTestCaseRunResult(
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
    val coverageReport: RemoteTestCaseFile? = null,
    val data: List<RemoteTestReportData>
) {
    companion object {
        fun fromTestCaseRunResult(value: TestCaseRunResult) = RemoteTestCaseRunResult(
            RemoteDevice.fromLocalDevice(value.device),
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

        fun RemoteTestCaseRunResult.toTestCaseRunResult(pool: Pool): TestCaseRunResult {
            val testFileManager = TestCaseFileManagerImpl(
                KoinContextHandler.get().get(),
                pool,
                device,
                testCase.toTestCase()
            )
            val testCaseFileSerializer = TestCaseFileSerializer(testFileManager)

            val candidate = TestCaseRunResult(
                pool,
                device.toLocalDevice(),
                testCase.toTestCase(),
                status,
                stackTraces,
                startTimestampUtc,
                endTimestampUtc,
                netStartTimestampUtc,
                netEndTimestampUtc,
                totalFailureCount,
                additionalProperties,
                coverageReport?.let { testCaseFileSerializer.fromSurrogate(it) },
                data.map {
                    TestReportDataSerializer.fromSurrogate(it, testCaseFileSerializer)
                }
            )

            return if (candidate.totalFailureCount > totalFailureCount) {
                candidate.copy(baseTotalFailureCount = totalFailureCount - 1)
            } else {
                candidate
            }
        }
    }
}

@Serializable
sealed class RemoteTestReportData {
    @Serializable
    sealed class WritableTestReportData : RemoteTestReportData() {
        abstract val file: RemoteTestCaseFile?

        @Serializable
        data class SurrogateMonoTextReportData(
            val title: String,
            val monoText: String,
            @SerialName("textType") val type: SimpleMonoTextReportData.Type,
            override val file: RemoteTestCaseFile?
        ) : WritableTestReportData()

        @Serializable
        data class SurrogateHtmlReportData(
            val title: String,
            val html: String,
            override val file: RemoteTestCaseFile?
        ) : WritableTestReportData()

        @Serializable
        data class SurrogateTableReportData(
            val title: String,
            val table: Table.TableJson,
            override val file: RemoteTestCaseFile?
        ) : WritableTestReportData()
    }

    @Serializable
    data class SurrogateImageReportData(
        val title: String,
        val image: RemoteTestCaseFile
    ) : RemoteTestReportData()

    @Serializable
    data class SurrogateVideoReportData(
        val title: String,
        val video: RemoteTestCaseFile
    ) : RemoteTestReportData()

    @Serializable
    data class SurrogateLinkedFileReportData(
        val title: String,
        val file: RemoteTestCaseFile
    ) : RemoteTestReportData()
}

@Serializable
data class RemoteTestCaseFile(
    val fileType: StandardFileTypes,
    val suffix: String,
    val data64: String?
)
