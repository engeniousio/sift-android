/*
 * Copyright 2020 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.system;

import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import org.apache.commons.text.StringEscapeUtils;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Various utils for DDMS
 */
public class DdmsUtils {

    public static final Pattern SINGLE_QUOTE_PATTERN = Pattern.compile("'");
    public static final Pattern SAFE_STRING_PATTERN = Pattern.compile("^[A-Za-z0-9_]*$");

    private DdmsUtils() {}

    /**
     * Properly sets classname and method name for the passed runner
     *
     * {@link RemoteAndroidTestRunner} has bug that test method names are not properly quoted
     * for command line. This method provides a workaround.
     *
     * @param runner Method name to quote
     * @param testClassName Class name to use
     * @param testMethodName Method name to use
     */
    public static void properlySetClassName(
            RemoteAndroidTestRunner runner,
            String testClassName,
            String testMethodName) {
        String escapedMethodName = escapeArgumentForCommandLine(testClassName + "#" + testMethodName);
        runner.setClassName(escapedMethodName);
    }

    /**
     * Properly sets string argument for the passed runner
     *
     * {@link RemoteAndroidTestRunner} has bug that string values are not properly escaped
     * for command line. This method provides a workaround.
     *
     * @param runner Method name to quote
     * @param name  Argument name to use
     * @param value Argument value to use
     */
    public static void properlyAddInstrumentationArg(RemoteAndroidTestRunner runner,
                                                     String name,
                                                     String value) {
        String escapedValue = escapeArgumentForCommandLine(value);
        runner.addInstrumentationArg(name, escapedValue);
    }

    public static String escapeArgumentForCommandLine(String value) {
        if (noEscapingNeeded(value)) {
            return value;
        }

        ArrayList<String> variants = new ArrayList<>(2);
        variants.add(escapeNonAscii(StringEscapeUtils.escapeXSI(value), null));
        variants.add(escapeNonAscii(singleQuote(value), '\''));

        return variants.stream()
                .min((o1, o2) -> Integer.compare(o1.length(), o2.length()))
                .orElseThrow(IllegalStateException::new);
    }

    static boolean noEscapingNeeded(String value) {
        return SAFE_STRING_PATTERN.matcher(value).matches();
    }

    static String singleQuote(String str) {
        final StringBuilder buffer = new StringBuilder("'");
        final Matcher matcher = SINGLE_QUOTE_PATTERN.matcher(str);
        final String replacement = Matcher.quoteReplacement("'\\''");
        while (matcher.find()) {
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        buffer.append("'");

        String result = buffer.toString();
        if (result.startsWith("''")) {
            result = result.substring(2);
        }
        if (result.endsWith("''")) {
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }

    static String escapeNonAscii(String str, @Nullable Character quote) {
        final ArrayList<Integer> result = new ArrayList<>();
        final AtomicBoolean inEscape = new AtomicBoolean(false);
        final Integer quoteCodepoint;
        if (quote != null) {
            assert String.valueOf(quote).codePoints().count() == 1;
            quoteCodepoint = String.valueOf(quote).codePointAt(0);
        } else {
            quoteCodepoint = null;
        }

        str.codePoints().forEachOrdered(code -> {
            if (code < 0x20 || code > 0x7f) {
                if (inEscape.compareAndSet(false, true)) {
                    assert "$".codePoints().count() == 1;
                    assert "'".codePoints().count() == 1;
                    assert "\\".codePoints().count() == 1;
                    assert "x".codePoints().count() == 1;
                    if (quoteCodepoint != null) {
                        result.add(quoteCodepoint);
                    }
                    result.add("$".codePointAt(0));
                    result.add("'".codePointAt(0));
                }

                byte[] encoded = new String(new int[]{code}, 0, 1).getBytes(StandardCharsets.UTF_8);
                for (byte b : encoded) {
                    result.add("\\".codePointAt(0));
                    result.add("x".codePointAt(0));

                    String byteCode = String.format("%02x", (0x100 + b) % 0x100);
                    assert byteCode.codePoints().count() == 2;
                    byteCode.codePoints().forEach(result::add);
                }
            } else {
                boolean endEscaping = inEscape.compareAndSet(true, false);
                if (endEscaping) {
                    assert "'".codePoints().count() == 1;
                    result.add("'".codePointAt(0));
                    if (quoteCodepoint != null) {
                        result.add(quoteCodepoint);
                    }
                }
                result.add(code);
            }
        });
        if (inEscape.get()) {
            assert "'".codePoints().count() == 1;
            result.add("'".codePointAt(0));
            if (quoteCodepoint != null) {
                result.add(quoteCodepoint);
            }
        }
        int[] resultArray = result.stream().mapToInt(i -> i).toArray();
        return new String(resultArray, 0, resultArray.length);
    }
}
