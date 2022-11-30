/*
 * Copyright 2020 TarCV
 * Copyright 2014 Shazam Entertainment Limited
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
package com.github.tarcv.tongs.summary;

import com.github.tarcv.tongs.api.result.TestCaseRunResult;
import com.github.tarcv.tongs.io.HtmlGenerator;
import com.github.tarcv.tongs.system.io.FileUtils;
import com.google.common.io.Resources;

import org.lesscss.LessCompiler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tarcv.tongs.api.result.StandardFileTypes.DOT_WITHOUT_EXTENSION;
import static com.github.tarcv.tongs.api.result.StandardFileTypes.HTML;
import static com.github.tarcv.tongs.io.Files.copyResource;
import static java.util.function.Function.identity;
import static org.apache.commons.io.FileUtils.writeStringToFile;

public class HtmlSummaryPrinter implements SummaryPrinter {
    private static final String HTML_OUTPUT = "html";
    private static final String STATIC = "static";
    private static final String INDEX_FILENAME = "index.html";
    private static final String[] STATIC_ASSETS = {
            "bootstrap-responsive.min.css",
            "bootstrap.min.css",
            "tongs.css",
            "bootstrap.min.js",
            "ceiling_android.png",
            "ceiling_android-green.png",
            "ceiling_android-red.png",
            "ceiling_android-yellow.png",
            "device.png",
            "icon-devices.png",
            "icon-log.png",
            "jquery.min.js",
            "log.png"
    };
    private final File htmlOutput;
    private final File staticOutput;
    private final HtmlGenerator htmlGenerator;

    public HtmlSummaryPrinter(
            File rootOutput,
            HtmlGenerator htmlGenerator
    ) {
        this.htmlGenerator = htmlGenerator;
        htmlOutput = new File(rootOutput, HTML_OUTPUT);
        staticOutput = new File(htmlOutput, STATIC);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void print(Summary summary) {
        htmlOutput.mkdirs();
        copyAssets();
        generateCssFromLess();
        HtmlSummary htmlSummary = HtmlConverters.toHtmlSummary(summary);
        htmlGenerator.generateHtml("tongspages/index.html", htmlOutput, INDEX_FILENAME, htmlSummary);
        generatePoolHtml(htmlSummary);
        generatePoolTestsHtml(summary, htmlSummary);
    }

    private void copyAssets() {
        for (String asset : STATIC_ASSETS) {
            copyResource("/static/", asset, staticOutput);
        }
    }

    private void generateCssFromLess() {
        try {
            LessCompiler compiler = new LessCompiler();
            String less = Resources.toString(getClass().getResource("/spoon.less"), StandardCharsets.UTF_8);
            String css = compiler.compile(less);
            File cssFile = new File(staticOutput, "spoon.css");
            writeStringToFile(cssFile, css, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates an HTML page for each pool, with multiple tests
     *
     * @param htmlSummary the summary of the pool
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void generatePoolHtml(HtmlSummary htmlSummary) {
        File poolsDir = new File(htmlOutput, "pools");
        poolsDir.mkdirs();
        for (HtmlPoolSummary pool : htmlSummary.getPools()) {
            String name = pool.getPoolName() + ".html";
            htmlGenerator.generateHtml("tongspages/pool.html", poolsDir, name, pool);
        }
    }

    /**
     * Genarates an HTML page for each test of each pool.
     *
     * @param summary     the summary containing the results
     * @param htmlSummary
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void generatePoolTestsHtml(Summary summary, HtmlSummary htmlSummary) {
        Map<String, HtmlPoolSummary> namesToHtmlPools = htmlSummary.getPools().stream()
                .collect(Collectors.toMap(HtmlPoolSummary::getPoolName, identity()));
        for (TestCaseRunResult testResult : summary.getAllTests()) {
            HtmlPoolSummary pool = namesToHtmlPools.get(testResult.getPool().getName());

            File poolTestsDir = new File(htmlOutput, "pools/" + testResult.getPool().getName());
            poolTestsDir.mkdirs();
            String fileNameForTest = FileUtils.createFilenameForTest(testResult.getTestCase(), DOT_WITHOUT_EXTENSION);
            htmlGenerator.generateHtml("tongspages/pooltest.html", poolTestsDir, fileNameForTest + HTML.getSuffix(), testResult, pool);
        }
    }
}
