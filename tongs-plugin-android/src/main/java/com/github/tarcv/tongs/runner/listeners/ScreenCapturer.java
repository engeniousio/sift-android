/*
 * Copyright 2020 TarCV
 * Copyright 2015 Shazam Entertainment Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.tarcv.tongs.runner.listeners;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.TimeoutException;
import com.github.tarcv.tongs.Utils;
import com.github.tarcv.tongs.api.result.TestCaseFile;
import com.github.tarcv.tongs.api.result.TestCaseFileManager;
import com.madgag.gif.fmsware.AnimatedGifEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.github.tarcv.tongs.api.result.StandardFileTypes.ANIMATION;
import static com.github.tarcv.tongs.api.result.StandardFileTypes.SCREENSHOT;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.lang.System.nanoTime;

class ScreenCapturer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ScreenCapturer.class);
    private final IDevice deviceInterface;
    private final TestCaseFileManager fileManager;

    private final Object lock = new Object();
    private final List<File> files = new ArrayList<>();
    private boolean capturing;
    private boolean hasFailed;
    private final TestCaseFile animationFile;

    ScreenCapturer(IDevice deviceInterface, TestCaseFileManager fileManager) {
        this.deviceInterface = deviceInterface;
        this.fileManager = fileManager;
        this.animationFile = new TestCaseFile(fileManager, ANIMATION, "");
    }

    @Override
    public void run() {
        Thread currentThread = Thread.currentThread();
        synchronized (lock) {
            try {
                int count = 0;
                capturing = true;
                while (capturing) {
                    getScreenshot(count++);
                    pauseTillNextScreenCapture();
                }

                if (hasFailed) {
                    File file = animationFile.create();
                    createGif(files, file);
                }
            } catch (InterruptedException e) {
                logger.warn("Screenshot capturer thread was interrupted", e);
                currentThread.interrupt();
            } finally {
                deleteFiles(files);
                files.clear();
            }
        }
    }

    private static void pauseTillNextScreenCapture() throws InterruptedException {
        Thread.sleep(300);
    }

    private void getScreenshot(int sequenceNumber) {
        try {
            logger.trace("Started getting screenshot");
            long startNanos = nanoTime();
            RawImage screenshot = deviceInterface.getScreenshot();
            File file = fileManager.createFile(SCREENSHOT, sequenceNumber);
            files.add(file);
            ImageIO.write(bufferedImageFrom(screenshot), SCREENSHOT.getSuffix(), file);
            logger.trace("Finished writing screenshot in {}ms to: {}", Utils.millisSinceNanoTime(startNanos), file);
        } catch (TimeoutException | AdbCommandRejectedException | IOException e) {
            logger.error("Error when getting screenshot of device", e);
        }
    }

    public void stopCapturing(boolean hasFailed) {
        synchronized (lock) {
            this.hasFailed = hasFailed;
            capturing = false;
        }
    }

    private static BufferedImage bufferedImageFrom(RawImage rawImage) {
        BufferedImage image = new BufferedImage(rawImage.width, rawImage.height, TYPE_INT_ARGB);

        int index = 0;
        int bytesPerPixel = rawImage.bpp >> 3;
        for (int y = 0; y < rawImage.height; y++) {
            for (int x = 0; x < rawImage.width; x++) {
                image.setRGB(x, y, rawImage.getARGB(index) | 0xff000000);
                index += bytesPerPixel;
            }
        }
        return image;
    }

    private static void createGif(List<File> files, File file) {
        try {
            AnimatedGifEncoder encoder = new AnimatedGifEncoder();
            encoder.start(file.getAbsolutePath());
            encoder.setDelay(1500);
            encoder.setQuality(1);
            encoder.setRepeat(0);
            encoder.setTransparent(Color.WHITE);

            int width = 0;
            int height = 0;
            for (File testScreenshot : files) {
                BufferedImage bufferedImage = ImageIO.read(testScreenshot);
                width = Math.max(bufferedImage.getWidth(), width);
                height = Math.max(bufferedImage.getHeight(), height);
            }
            encoder.setSize(width, height);

            for (File testScreenshot : files) {
                encoder.addFrame(ImageIO.read(testScreenshot));
            }

            encoder.finish();
        } catch (IOException e) {
            logger.error("Error saving animated GIF", e);
        }
    }

    private static void deleteFiles(List<File> files) {
        for (File file : files) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                if (logger.isWarnEnabled()) {
                    logger.warn(String.format("Failed to delete %s", file), e);
                }
            }
        }
    }

    public TestCaseFile getFile() {
        return animationFile;
    }
}
