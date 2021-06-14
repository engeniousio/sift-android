package io.engenious.sift.node.serialization

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
import com.github.tarcv.tongs.api.result.Table
import com.github.tarcv.tongs.api.result.TableReportData
import com.github.tarcv.tongs.api.result.TestCaseFile
import com.github.tarcv.tongs.api.result.TestReportData
import com.github.tarcv.tongs.api.result.VideoReportData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

object TestReportDataSerializer {
    fun toSurrogate(value: TestReportData): RemoteTestReportData = when (value) {
        is SimpleMonoTextReportData, is FileMonoTextReportData -> RemoteTestReportData.WritableTestReportData.SurrogateMonoTextReportData(
            (value as MonoTextReportData).title,
            value.monoText,
            value.type,
            value
                .takeIf { value is FileMonoTextReportData }
                ?.extractFile("monoTextPath")
        )
        is SimpleHtmlReportData, is FileHtmlReportData -> RemoteTestReportData.WritableTestReportData.SurrogateHtmlReportData(
            (value as HtmlReportData).title,
            value.html,
            value
                .takeIf { value is FileHtmlReportData }
                ?.extractFile("htmlPath")
        )
        is SimpleTableReportData, is FileTableReportData -> RemoteTestReportData.WritableTestReportData.SurrogateTableReportData(
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
        is ImageReportData -> RemoteTestReportData.SurrogateImageReportData(
            value.title,
            value.extractFile("image")
        )
        is VideoReportData -> RemoteTestReportData.SurrogateVideoReportData(
            value.title,
            value.extractFile("video")
        )
        is LinkedFileReportData -> RemoteTestReportData.SurrogateLinkedFileReportData(
            value.title,
            TestCaseFileSerializer.toSurrogate(value.file)
        )
    }

    private fun TestReportData.extractFile(fieldName: String): RemoteTestCaseFile {
        return this::class.declaredMemberProperties
            .single { it.name == fieldName }
            .run {
                @Suppress("UNCHECKED_CAST")
                this as KProperty1<TestReportData, TestCaseFile>

                isAccessible = true
                get(this@extractFile)
            }
            .let {
                TestCaseFileSerializer.toSurrogate(it)
            }
    }

    fun fromSurrogate(
        surrogate: RemoteTestReportData,
        testCaseFileSerializer: TestCaseFileSerializer
    ): TestReportData {
        return when (surrogate) {
            is RemoteTestReportData.WritableTestReportData.SurrogateMonoTextReportData -> {
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
            is RemoteTestReportData.WritableTestReportData.SurrogateHtmlReportData -> {
                if (surrogate.file != null) {
                    FileHtmlReportData(surrogate.title, testCaseFileSerializer.fromSurrogate(surrogate.file))
                } else {
                    SimpleHtmlReportData(surrogate.title, surrogate.html)
                }
            }
            is RemoteTestReportData.WritableTestReportData.SurrogateTableReportData -> {
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
            is RemoteTestReportData.SurrogateImageReportData -> {
                ImageReportData(surrogate.title, testCaseFileSerializer.fromSurrogate(surrogate.image))
            }
            is RemoteTestReportData.SurrogateVideoReportData -> {
                VideoReportData(surrogate.title, testCaseFileSerializer.fromSurrogate(surrogate.video))
            }
            is RemoteTestReportData.SurrogateLinkedFileReportData -> {
                LinkedFileReportData(surrogate.title, testCaseFileSerializer.fromSurrogate(surrogate.file))
            }
        }
    }
}
