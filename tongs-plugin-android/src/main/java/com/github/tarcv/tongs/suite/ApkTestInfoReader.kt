/*
 * Copyright 2021 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.suite

import com.android.ddmlib.testrunner.TestIdentifier
import com.github.tarcv.tongs.api.testcases.AnnotationInfo
import com.github.tarcv.tongs.runner.TestInfo
import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.dexbacked.DexBackedClassDef
import org.jf.dexlib2.dexbacked.DexBackedMethod
import org.jf.dexlib2.iface.Annotation
import org.jf.dexlib2.iface.BasicAnnotation
import org.jf.dexlib2.iface.value.*
import org.slf4j.LoggerFactory
import java.io.File

class ApkTestInfoReader {
    fun readTestInfo(apk: File, testsToCheck: Collection<TestIdentifier>): List<TestInfo> {
        class FoundMethod(
                val matchingParts: Int,
                val method: DexBackedMethod
        )

        class Test(val testIdentifier: TestIdentifier) {
            var foundMethod: FoundMethod? = null
        }

        val testParts = HashMap<String, List<Test>>()
        val testMatches = ArrayList<Test>()
        testsToCheck
                .forEach { test ->
                    val parts = listOf(test.className) + splitIdentifiers(test.testName)

                    val testInfo = Test(test)
                    testMatches.add(testInfo)

                    parts
                            .forEach { part ->
                                testParts
                                        .computeIfAbsent(part) {
                                            ArrayList()
                                        }
                                        .let {
                                            (it as MutableList).add(testInfo)
                                        }
                            }
                }

        val dexClasses = generateSequence(1) { it + 1}
            .map {
                val indexStr = if (it == 1) {
                    ""
                } else {
                    it.toString()
                }

                try {
                    DexFileFactory.loadDexEntry(apk, "classes${indexStr}.dex", true, null)
                } catch (e: DexFileFactory.DexFileNotFoundException) {
                    null
                }
            }
            .takeWhile { it != null }
            .toList()
            .flatMap { it!!.dexFile.classes }

        val knownClasses = dexClasses
                .filter(Companion::isClass)
                .associateBy { decodeClassName(it.type) }

        dexClasses
                .filter(Companion::isClass)
                .flatMap { clazz ->
                    (clazz.virtualMethods + clazz.directMethods)
                            .filter { it.name !in ignoredMethods }
                            .filter {
                                AccessFlags.ABSTRACT !in it.accessFlags
                                        && AccessFlags.CONSTRUCTOR !in it.accessFlags
                                        && AccessFlags.NATIVE !in it.accessFlags
                            }
                }
                .sortedBy(Companion::methodCompareIndex)
                .forEach { method ->
                    val className = decodeClassName(method.classDef.type)
                    val methodName = method.name
                    (
                            (testParts[className] ?: emptyList()) +
                                    (testParts[methodName] ?: emptyList())
                            )
                            .groupingBy { it }
                            .eachCount()
                            .forEach { (test, matchingParts) ->
                                test.foundMethod.let {
                                    if (it == null || it.matchingParts < matchingParts) {
                                        test.foundMethod = FoundMethod(matchingParts, method)
                                    }
                                }
                            }
                }

        val notFoundTests = testMatches
            .filter { test -> test.foundMethod == null }
            .map { test -> test.testIdentifier.toString() }
        if (notFoundTests.isNotEmpty()) {
            throw RuntimeException("Failed to find info about these tests in APK" +
                    " (Different APK is installed on the device?): $notFoundTests")
        }

        return testMatches
                .filter { test -> test.foundMethod != null }
                .map { test ->
                    val testMethod = test.foundMethod!!.method
                    val testClass = testMethod.classDef
                    val annotations = ArrayList<AnnotationInfo>()

                    // Method annotations override class ones, so they should be added last

                    appendSuperclassAnnotationsRoot(testClass, knownClasses, annotations)
                    appendAnnotationInfos(testClass.annotations, annotations)
                    appendAnnotationInfos(testMethod.annotations, annotations)

                    TestInfo(
                            test.testIdentifier,
                            extractPackage(testClass),
                            emptyList(),
                            annotations
                    )
                }
    }

    private fun extractPackage(testClass: DexBackedClassDef): String {
        return testClass.type
                .substringBeforeLast('/', "")
                .removePrefix("L")
                .replace('/', '.')
    }

    private fun appendSuperclassAnnotationsRoot(testClass: DexBackedClassDef, knownClasses: Map<String, DexBackedClassDef>, out: java.util.ArrayList<AnnotationInfo>) {
        for (iface in testClass.interfaces) {
            appendSuperclassAnnotationsFull(iface, knownClasses, out)
        }

        val superclass: String? = testClass.superclass ?.let { decodeClassName(it) }
        if (superclass != "java.lang.Object" &&
                testClass.superclass != testClass.type &&
                superclass != null) {
            appendSuperclassAnnotationsFull(superclass, knownClasses, out)
        }
    }

    private fun appendSuperclassAnnotationsFull(testClassName: String, knownClasses: Map<String, DexBackedClassDef>, out: java.util.ArrayList<AnnotationInfo>) {
        val testClass = knownClasses[testClassName]
        if (testClass == null) {
            logger.warn("Can't find class '$testClassName' in dex entries")
            return
        }
        appendSuperclassAnnotationsRoot(testClass, knownClasses, out)

        val eligibleAnnotations = testClass.annotations
                .filter { annotation ->
                    val className = decodeClassName(annotation.type)
                    val annotationClass = knownClasses[className]
                    when {
                        annotationClass == null -> {
                            logger.warn("Can't find annotation '$className' in dex entries")
                            true
                        }
                        annotationClass.annotations.any { decodeClassName(it.type) == inheritedAnnotation } -> {
                            true
                        }
                        else -> {
                            false
                        }
                    }
        }

        appendAnnotationInfos(eligibleAnnotations, out)

    }

    private fun appendAnnotationInfos(annotations: Collection<Annotation>, out: MutableList<AnnotationInfo>) {
        annotations.mapTo(out) { annotation ->
            AnnotationInfo(
                    decodeClassName(annotation.type),
                    annotationToMap(annotation)
            )
        }
    }

    private fun annotationToMap(annotation: BasicAnnotation): Map<String, Any?> {
        return annotation.elements
                .associateBy({ it.name }) {
                    decodeValue(it.value)
                }
    }

    private fun decodeValue(encodedValue: EncodedValue): Any? = when (encodedValue) {
        is AnnotationEncodedValue -> annotationToMap(encodedValue)
        is NullEncodedValue -> null
        is ArrayEncodedValue -> {
            encodedValue.value
                    .map { decodeValue(it) }
        }
        is BooleanEncodedValue -> encodedValue.value
        is ByteEncodedValue -> encodedValue.value
        is CharEncodedValue -> encodedValue.value
        is DoubleEncodedValue -> encodedValue.value
        is EnumEncodedValue -> encodedValue.value.name
        is FloatEncodedValue -> encodedValue.value
        is IntEncodedValue -> encodedValue.value
        is LongEncodedValue -> encodedValue.value
        is ShortEncodedValue -> encodedValue.value
        is StringEncodedValue -> encodedValue.value
        is TypeEncodedValue -> decodeClassName(encodedValue.value)
        else -> throw ApkReadingException(
                "Annotation value encoded in unexpected way - ${encodedValue.javaClass.name}")
    }

    private fun decodeClassName(type: String): String {
        return type
                .removeSurrounding("L", ";")
                .replace('/', '.')
                .replace('$', '.')
    }

    companion object {
        private const val inheritedAnnotation = "java.lang.annotation.Inherited"

        private val logger = LoggerFactory.getLogger(ApkTestInfoReader::class.java)

        private val ignoredMethods = listOf("<init>", "<clinit>")

        private fun isClass(classDef: DexBackedClassDef) = classDef.type.startsWith("L")

        private fun methodCompareIndex(methodDef: DexBackedMethod): Int {
            /*
             Matching order:
             - public instance methods
             - protected/default -
             - public static methods
             - protected/default -
             - private instance methods
             - private static
             */

            val accessFlags = methodDef.accessFlags
            val isStatic = AccessFlags.STATIC in accessFlags
            val access = when {
                AccessFlags.PUBLIC in accessFlags -> when {
                    isStatic -> MethodType.PUBLIC_STATIC
                    else -> MethodType.PUBLIC_INSTANCE
                }
                AccessFlags.PRIVATE in accessFlags -> when {
                    isStatic -> MethodType.PRIVATE_STATIC
                    else -> MethodType.PRIVATE_INSTANCE
                }
                else -> when { // AccessFlags.PROTECTED or default
                    isStatic -> MethodType.PROTECTED_STATIC
                    else -> MethodType.PROTECTED_INSTANCE
                }
            }

            return when(access) {
                MethodType.PUBLIC_INSTANCE -> 0
                MethodType.PROTECTED_INSTANCE -> 10
                MethodType.PUBLIC_STATIC -> 20
                MethodType.PROTECTED_STATIC -> 30
                MethodType.PRIVATE_INSTANCE -> 40
                MethodType.PRIVATE_STATIC -> 50
            }
        }

        private enum class MethodType {
            PUBLIC_STATIC,
            PUBLIC_INSTANCE,
            PROTECTED_STATIC, // or default static
            PROTECTED_INSTANCE, // or default instance
            PRIVATE_STATIC,
            PRIVATE_INSTANCE
        }

        private operator fun Int.contains(flag: AccessFlags): Boolean = this and flag.value != 0

        private fun splitIdentifiers(testIdentifier: String): List<String> {
            class Acc {
                val list = ArrayList<String>()
                var expectedBracket: Char? = null
                val builder = StringBuilder(testIdentifier.length)

                fun appendPart() {
                    list.add(builder.toString())
                    builder.delete(0, builder.length)
                }

                fun toList(): List<String> {
                    if (builder.isNotEmpty()) {
                        list.add(builder.toString())
                    }
                    return list
                }
            }
            val bracketPairs = mapOf(
                    '{' to '}',
                    '[' to ']',
                    '(' to ')',
                    '<' to '>'
            )
            return testIdentifier
                    .fold(Acc()) { acc, c ->
                        if (acc.expectedBracket == null) {
                            if (bracketPairs.contains(c)) {
                                acc.expectedBracket = bracketPairs[c]
                                acc.appendPart()
                            } else if (c == '.' || Character.isJavaIdentifierPart(c)) {
                                acc.builder.append(c)
                            } else {
                                acc.appendPart()
                            }
                        } else if (acc.expectedBracket == c) {
                            acc.appendPart()
                        } else {
                            acc.builder.append(c)
                        }
                        acc
                    }
                    .toList()
                    .filter { it.isNotEmpty() }
        }
    }

    class ApkReadingException(message: String) : RuntimeException(message)
}