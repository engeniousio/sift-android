package io.engenious.sift.node.serialization

import com.github.tarcv.tongs.api.result.StandardFileTypes
import com.github.tarcv.tongs.api.result.TestCaseFile
import com.github.tarcv.tongs.api.result.TestCaseFileManager
import java.util.Base64

class TestCaseFileSerializer(private val testFileManager: TestCaseFileManager) {
    companion object {
        fun toSurrogate(value: TestCaseFile): RemoteTestCaseFile {
            val fileBytes = value.toFile().run {
                if (isFile) {
                    val bytes = readBytes()
                    Base64.getMimeEncoder().encodeToString(bytes)
                } else {
                    null
                }
            }

            return RemoteTestCaseFile(
                value.fileType as StandardFileTypes,
                value.suffix,
                fileBytes
            )
        }
    }

    fun fromSurrogate(surrogate: RemoteTestCaseFile): TestCaseFile {
        val testCaseFile = TestCaseFile(
            testFileManager,
            surrogate.fileType,
            surrogate.suffix
        )
        if (surrogate.data64 != null) {
            val bytes = Base64.getMimeDecoder().decode(surrogate.data64)
            testCaseFile
                .create()
                .writeBytes(bytes)
        }
        return testCaseFile
    }
}
