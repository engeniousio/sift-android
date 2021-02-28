package io.engenious.sift.ondevice;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class ScreenshotOnFailureRule extends TestWatcher {
    private static final String TAG = ScreenshotOnFailureRule.class.getSimpleName();
    private static final String SCREENSHOT_FILE = "failure.png";
    private final Object lock = new Object();

    @GuardedBy("lock")
    private boolean enabled = true;

    private void screenshot() {
        synchronized (lock) {
            if (enabled) {
                enabled = false;
                takeScreenshot();
            }
        }
    }

    private static void takeScreenshot() {
        try {
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

            @SuppressWarnings("deprecation")
            File screenFile = new File(Environment.getExternalStorageDirectory(), SCREENSHOT_FILE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Filesystem access for shell commands is not limited on Android 9+,
                // so the most reliable way to take a screenshot is using them
                instrumentation.getUiAutomation().executeShellCommand("screencap -p " + screenFile.getAbsolutePath());
            } else {
                // executeShellCommand is not available before Android 5.0
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(screenFile))) {
                    Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
                    if (!screenshot.compress(Bitmap.CompressFormat.PNG, 90, out)) {
                        Log.w(TAG, "Failed to compress the failure screenshot");
                    }
                    out.flush();
                }
            }
            Log.i(TAG, "The failure screenshot is written to " + screenFile);
        } catch (Throwable e) {
            Log.w(TAG, "Error while taking the failure screenshot", e);
        }
    }

    public void forceEnabled() {
        synchronized (lock) {
            enabled = true;
        }
    }

    @Override
    protected void starting(Description description) {
        forceEnabled();
    }

    @Override
    protected void failed(Throwable e, Description description) {
        screenshot();
    }
}
