/*
 * Copyright 2020 TarCV
 * Copyright 2015 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */
package com.github.tarcv.tongs.api.result

import java.io.File

interface TestCaseFileManager {
    fun createFile(fileType: FileType): File
    fun createFile(fileType: FileType, sequenceNumber: Int): File
    fun createFile(fileType: FileType, suffix: String): File
    fun getFile(fileType: FileType, suffix: String): File
    fun getRelativeFile(fileType: FileType, suffix: String): File

    fun testCaseFile(ofType: FileType, suffix: String) = TestCaseFile(this, ofType, suffix)
}
