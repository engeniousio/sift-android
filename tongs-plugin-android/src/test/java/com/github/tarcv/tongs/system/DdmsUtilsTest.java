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

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class DdmsUtilsTest {

    @Test
    @Parameters({
            "foo123_|foo123_",
            "Foo Bar|Foo\\ Bar",
            "Foo Bar Foobar Bar Foo|'Foo Bar Foobar Bar Foo'",
            "'Foo Bar'|\\'Foo\\ Bar\\'",
            "'Foo Bar Foobar Bar Foo'|\\''Foo Bar Foobar Bar Foo'\\'",
            "foo\u00b6123_|foo$'\\xc2\\xb6'123_"
    })
    public void escapeArgumentForCommandLine(String str, String expectedResult) {
        Assert.assertEquals(expectedResult, DdmsUtils.escapeArgumentForCommandLine(str));
    }

    @Test
    @Parameters({
            "Foo Bar|'Foo Bar'",
            "'Foo Bar|\\''Foo Bar'",
            "Foo Bar'|'Foo Bar'\\'",
            "'Foo Bar'|\\''Foo Bar'\\'",
            "Fo'oB'ar|'Fo'\\''oB'\\''ar'",
    })
    public void singleQuote(String str, String expectedResult) {
        Assert.assertEquals(expectedResult, DdmsUtils.singleQuote(str));
    }

    @Test
    @Parameters({
            "foo123_|true",
            "foo'123_|false",
            "foo#123_|false",
            "foo¶123_|false",
    })
    public void noEscapingNeeded(String str, boolean expected) {
        Assert.assertEquals(expected, DdmsUtils.noEscapingNeeded(str));
    }

    @Test
    @Parameters(method = "provideEscapeNonAsciiTests")
    public void escapeNonAscii(String str, @Nullable Character quote, String expectedResult) {
        Assert.assertEquals(expectedResult, DdmsUtils.escapeNonAscii(str, quote));
    }

    @SuppressWarnings("unused")
    public static Object[][] provideEscapeNonAsciiTests() {
        return new Object[][]{
                new Object[]{"foo123_", '*', "foo123_"},
                new Object[]{"foo123_", null, "foo123_"},
                new Object[]{"foo\u00b6123_", '*', "foo*$'\\xc2\\xb6'*123_"},
                new Object[]{"foo\u00b6123_", null, "foo$'\\xc2\\xb6'123_"},
                new Object[]{"foo\t123\n", null, "foo$'\\x09'123$'\\x0a'"},
        };
    }
}