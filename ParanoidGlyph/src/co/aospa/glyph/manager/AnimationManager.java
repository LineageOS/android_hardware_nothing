/*
 * Copyright (C) 2022-2024 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.aospa.glyph.manager;

import android.util.Log;

import co.aospa.glyph.utils.Constants;
import co.aospa.glyph.utils.FileUtils;
import co.aospa.glyph.utils.ResourceUtils;

import com.android.internal.util.ArrayUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class AnimationManager {

    private static final String TAG = "GlyphAnimationManager";
    private static final boolean DEBUG = true;

    private static volatile int previewOnceSeq = 0;
    private static volatile boolean previewOnceActive = false;

    private static int getDevicePatternLength() {
        int[] lengths = Constants.getSupportedAnimationPatternLengths();
        if (lengths.length == 0) return 0;
        int max = lengths[0];
        for (int i = 1; i < lengths.length; i++) {
            if (lengths[i] > max) max = lengths[i];
        }
        return max;
    }

    private static int mapVolumeStepToIndex(int stepIndex, int steps, int barLen) {
        if (barLen <= 1) return 0;
        if (steps <= 1) return 0;
        double ratio = stepIndex / (double) (steps - 1);
        return Math.min(barLen - 1, (int) Math.floor(ratio * (barLen - 1)));
    }

    private static void updateVolumeFrame(int[] barArray) {
        if (Constants.getDevice().equals("phone3a")) {
            updateLedFrame(
                    ResourceUtils.buildPatternArray(
                            new int[20], ResourceUtils.reverseFrameArray(barArray), new int[5]));
        } else {
            updateLedFrame(barArray);
        }
    }

    private static Future<?> submit(Runnable runnable) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        return executorService.submit(runnable);
    }

    private static boolean check(String name, boolean wait) {
        if (DEBUG)
            Log.d(TAG, "Playing animation | name: " + name + " | waiting: " + Boolean.toString(wait));

        if (StatusManager.isAllLedActive()) {
            if (DEBUG) Log.d(TAG, "All LEDs are active, exiting animation | name: " + name);
            return false;
        }

        if (StatusManager.isCallLedActive()) {
            if (DEBUG)
                Log.d(TAG, "Call animation is currently active, exiting animation | name: " + name);
            return false;
        }

        if (StatusManager.isAnimationActive()) {
            long start = System.currentTimeMillis();
            if (name == "volume" && StatusManager.isVolumeLedActive()) {
                if (DEBUG) Log.d(TAG, "There is already a volume animation playing, update");
                StatusManager.setVolumeLedUpdate(true);
                while (StatusManager.isVolumeLedUpdate()) {
                    if (System.currentTimeMillis() - start >= 2500) return false;
                }
            } else if (wait) {
                if (DEBUG)
                    Log.d(TAG, "There is already an animation playing, wait | name: " + name);
                while (StatusManager.isAnimationActive()) {
                    if (System.currentTimeMillis() - start >= 2500) return false;
                }
            } else {
                if (DEBUG)
                    Log.d(TAG, "There is already an animation playing, exiting | name: " + name);
                return false;
            }
        }

        return true;
    }

    private static boolean checkInterruption(String name) {
        if (StatusManager.isAllLedActive()
                || (name != "call" && StatusManager.isCallLedEnabled())
                || (name == "call" && !StatusManager.isCallLedEnabled())
                || (name == "volume" && StatusManager.isVolumeLedUpdate())) {
            return true;
        }
        return false;
    }

    public static void playCsv(String name) {
        playCsv(name, false);
    }

    public static void playCsv(String name, boolean wait) {
        submit(
                () -> {
                    if (!check(name, wait)) return;

                    StatusManager.setAnimationActive(true);

                    long start = System.currentTimeMillis();

                    try (BufferedReader reader =
                                 new BufferedReader(new InputStreamReader(ResourceUtils.getAnimation(name)))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (checkInterruption("csv")) throw new InterruptedException();
                            line = line.replace(" ", "");
                            line = line.endsWith(",") ? line.substring(0, line.length() - 1) : line;
                            String[] pattern = line.split(",");
                            if (ArrayUtils.contains(
                                    Constants.getSupportedAnimationPatternLengths(), pattern.length)) {
                                updateLedFrame(pattern);
                            } else {
                                if (DEBUG)
                                    Log.d(TAG, "Animation line length mismatch | name: " + name + " | line: " + line);
                                throw new InterruptedException();
                            }
                            long delay = 16666L - (System.currentTimeMillis() - start);
                            Thread.sleep(delay / 1000);
                        }
                    } catch (Exception e) {
                        if (DEBUG)
                            Log.d(
                                    TAG, "Exception while playing animation | name: " + name + " | exception: " + e);
                    } finally {
                        updateLedFrame(new float[5]);
                        StatusManager.setAnimationActive(false);
                        if (DEBUG) Log.d(TAG, "Done playing animation | name: " + name);
                    }
                });
    }

    public static void previewOnce(String name, boolean isCallAnimation) {
        stopPreviewOnce();
        final int seq = ++previewOnceSeq;
        submit(
                () -> {
                    if (StatusManager.isAllLedActive()
                            || StatusManager.isCallLedActive()
                            || (StatusManager.isAnimationActive() && !previewOnceActive)) {
                        return;
                    }

                    previewOnceActive = true;
                    StatusManager.setAnimationActive(true);
                    int lastPatternLength = 0;
                    try (BufferedReader reader =
                                 new BufferedReader(
                                         new InputStreamReader(
                                                 isCallAnimation
                                                         ? ResourceUtils.getCallAnimation(name)
                                                         : ResourceUtils.getNotificationAnimation(name)))) {
                        String line;
                        long start = System.currentTimeMillis();
                        while (seq == previewOnceSeq && (line = reader.readLine()) != null) {
                            line = line.replace(" ", "");
                            line = line.endsWith(",") ? line.substring(0, line.length() - 1) : line;
                            String[] pattern = line.split(",");
                            if (ArrayUtils.contains(
                                    Constants.getSupportedAnimationPatternLengths(), pattern.length)) {
                                int[] frame = Arrays.stream(pattern).mapToInt(Integer::parseInt).toArray();
                                lastPatternLength = frame.length;
                                updateLedFrame(frame);
                            }
                            long delay = 16666L - (System.currentTimeMillis() - start);
                            Thread.sleep(delay / 1000);
                        }
                    } catch (Exception e) {
                        if (DEBUG)
                            Log.d(
                                    TAG,
                                    "Exception while previewing animation | name: " + name + " | exception: " + e);
                    } finally {
                        if (lastPatternLength > 0) {
                            updateLedFrame(new int[lastPatternLength]);
                        }
                        StatusManager.setAnimationActive(false);
                        previewOnceActive = false;
                    }
                });
    }

    public static void stopPreviewOnce() {
        previewOnceSeq++;
    }

    public static void playCharging(int batteryLevel, boolean wait) {
        submit(
                () -> {
                    if (!check("charging", wait)) return;

                    StatusManager.setAnimationActive(true);

                    boolean batteryDot = ResourceUtils.getBoolean("glyph_settings_battery_dot");
                    int[] batteryArray =
                            new int[ResourceUtils.getInteger("glyph_settings_battery_levels_num")];
                    int amount =
                            (int)
                                    (Math.floor((batteryLevel / 100.0) * (batteryArray.length - (batteryDot ? 2 : 1)))
                                            + (batteryDot ? 2 : 1));

                    try {
                        for (int i = 0; i < batteryArray.length; i++) {
                            if (checkInterruption("charging")) throw new InterruptedException();
                            batteryArray[i] = Constants.getBrightness();
                            if (batteryDot && i == 0) continue;
                            if (Constants.getDevice().equals("phone3a")) {
                                updateLedFrame(
                                        ResourceUtils.buildPatternArray(
                                                new int[20], ResourceUtils.reverseFrameArray(batteryArray), new int[5]));
                            } else {
                                updateLedFrame(batteryArray);
                            }
                            Thread.sleep(15);
                        }
                        for (int i = batteryArray.length - 1; i > amount - 1; i--) {
                            if (checkInterruption("charging")) throw new InterruptedException();
                            batteryArray[i] = 0;
                            if (Constants.getDevice().equals("phone3a")) {
                                updateLedFrame(
                                        ResourceUtils.buildPatternArray(
                                                new int[20], ResourceUtils.reverseFrameArray(batteryArray), new int[5]));
                            } else {
                                updateLedFrame(batteryArray);
                            }
                            Thread.sleep(5);
                        }
                        long start = System.currentTimeMillis();
                        while (System.currentTimeMillis() - start <= 2000) {
                            if (checkInterruption("charging")) throw new InterruptedException();
                        }
                        for (int i = amount - 1; i >= 0; i--) {
                            if (checkInterruption("charging")) throw new InterruptedException();
                            batteryArray[i] = 0;
                            if (Constants.getDevice().equals("phone3a")) {
                                updateLedFrame(
                                        ResourceUtils.buildPatternArray(
                                                new int[20], ResourceUtils.reverseFrameArray(batteryArray), new int[5]));
                            } else {
                                updateLedFrame(batteryArray);
                            }
                            Thread.sleep(11);
                        }
                        long start2 = System.currentTimeMillis();
                        while (System.currentTimeMillis() - start2 <= 730) {
                            if (checkInterruption("charging")) throw new InterruptedException();
                        }
                    } catch (InterruptedException e) {
                        if (DEBUG)
                            Log.d(TAG, "Exception while playing animation, interrupted | name: charging");
                        if (!StatusManager.isAllLedActive()) {
                            if (Constants.getDevice().equals("phone3a")) {
                                updateLedFrame(
                                        ResourceUtils.buildPatternArray(
                                                new int[20], new int[batteryArray.length], new int[5]));
                            } else {
                                updateLedFrame(new int[batteryArray.length]);
                            }
                        }
                    } finally {
                        StatusManager.setAnimationActive(false);
                        if (DEBUG) Log.d(TAG, "Done playing animation | name: charging");
                    }
                });
    }

    public static void playVolume(int volumeLevel, boolean wait) {
        submit(
                () -> {
                    if (!check("volume", wait)) return;

                    StatusManager.setVolumeLedActive(true);
                    StatusManager.setAnimationActive(true);

                    int steps = ResourceUtils.getInteger("glyph_settings_volume_levels_num");
                    int patternLength = getDevicePatternLength();
                    int barLen =
                            Constants.getDevice().equals("phone3a")
                                    ? Math.max(1, patternLength - 25)
                                    : patternLength;
                    int[] volumeArray = new int[barLen];
                    int amount = (int) (Math.floor((volumeLevel / 100D) * (steps - 1)) + 1);
                    int targetIndex = mapVolumeStepToIndex(Math.max(0, amount - 1), steps, barLen);
                    int last = StatusManager.getVolumeLedLast();

                    try {
                        if (volumeLevel == 0) {
                            if (checkInterruption("volume")) throw new InterruptedException();
                            StatusManager.setVolumeLedLast(0);
                            updateVolumeFrame(new int[barLen]);
                        } else {
                            for (int i = 0; i <= targetIndex; i++) {
                                if (checkInterruption("volume")) throw new InterruptedException();
                                StatusManager.setVolumeLedLast(i);
                                volumeArray[i] = Constants.getBrightness();
                                if (last == 0 || i == targetIndex) {
                                    updateVolumeFrame(volumeArray);
                                    Thread.sleep(15);
                                }
                            }
                        }
                        if (last != 0 && volumeLevel > 0) {
                            if (checkInterruption("volume")) throw new InterruptedException();
                            updateVolumeFrame(volumeArray);
                        }
                        long start = System.currentTimeMillis();
                        while (System.currentTimeMillis() - start <= 1800) {
                            if (checkInterruption("volume")) throw new InterruptedException();
                        }
                        for (int i = volumeArray.length - 1; i >= 0; i--) {
                            if (checkInterruption("volume")) throw new InterruptedException();
                            if (volumeArray[i] != 0) {
                                StatusManager.setVolumeLedLast(i);
                                volumeArray[i] = 0;
                                updateVolumeFrame(volumeArray);
                                Thread.sleep(15);
                            }
                        }
                        long start2 = System.currentTimeMillis();
                        while (System.currentTimeMillis() - start2 <= 730) {
                            if (checkInterruption("volume")) throw new InterruptedException();
                        }
                    } catch (InterruptedException e) {
                        if (DEBUG)
                            Log.d(TAG, "Exception while playing animation, interrupted | name: volume");
                        if (!StatusManager.isAllLedActive() && !StatusManager.isVolumeLedUpdate()) {
                            updateVolumeFrame(new int[volumeArray.length]);
                        }
                    } finally {
                        if (!StatusManager.isVolumeLedUpdate()) {
                            StatusManager.setVolumeLedLast(0);
                            StatusManager.setAnimationActive(false);
                            StatusManager.setVolumeLedActive(false);
                        }
                        StatusManager.setVolumeLedUpdate(false);
                        if (DEBUG) Log.d(TAG, "Done playing animation | name: volume");
                    }
                });
    }

    public static void playCall(String name) {
        submit(
                () -> {
                    StatusManager.setCallLedEnabled(true);

                    if (!check("call: " + name, true)) return;

                    StatusManager.setCallLedActive(true);

                    long start = System.currentTimeMillis();

                    while (StatusManager.isCallLedEnabled()) {
                        try (BufferedReader reader =
                                     new BufferedReader(new InputStreamReader(ResourceUtils.getCallAnimation(name)))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (checkInterruption("call")) throw new InterruptedException();
                                line = line.replace(" ", "");
                                line = line.endsWith(",") ? line.substring(0, line.length() - 1) : line;
                                String[] pattern = line.split(",");
                                if (ArrayUtils.contains(
                                        Constants.getSupportedAnimationPatternLengths(), pattern.length)) {
                                    updateLedFrame(pattern);
                                } else {
                                    if (DEBUG)
                                        Log.d(
                                                TAG, "Animation line length mismatch | name: " + name + " | line: " + line);
                                    throw new InterruptedException();
                                }
                                long delay = 16666L - (System.currentTimeMillis() - start);
                                Thread.sleep(delay / 1000);
                            }
                        } catch (Exception e) {
                            if (DEBUG)
                                Log.d(
                                        TAG,
                                        "Exception while playing animation | name: " + name + " | exception: " + e);
                        } finally {
                            if (StatusManager.isAllLedActive()) {
                                if (DEBUG)
                                    Log.d(TAG, "All LED active, pause playing animation | name: " + name);
                                while (StatusManager.isAllLedActive()) {
                                }
                            }
                        }
                    }
                    updateLedFrame(new float[5]);
                    StatusManager.setCallLedActive(false);
                    if (DEBUG) Log.d(TAG, "Done playing animation | name: " + name);
                });
    }

    public static void stopCall() {
        if (DEBUG) Log.d(TAG, "Disabling Call Animation");
        StatusManager.setCallLedEnabled(false);
    }

    public static void playEssential() {
        if (DEBUG) Log.d(TAG, "Playing Essential Animation");
        if (!StatusManager.isEssentialLedActive()) {
            submit(
                    () -> {
                        if (!check("essential", true)) return;

                        StatusManager.setAnimationActive(true);

                        try {
                            if (checkInterruption("essential")) throw new InterruptedException();
                            if (Constants.getDevice().equals("phone3a")) {
                                int[] steps = {12, 24, 36, 48, 60};
                                int[] essentialPattern = new int[11];
                                for (int i : steps) {
                                    if (checkInterruption("essential"))
                                        throw new InterruptedException();
                                    int patternBrightness = Constants.getMaxBrightness() / 100 * i;
                                    Arrays.fill(essentialPattern, patternBrightness);
                                    updateLedFrame(
                                            ResourceUtils.buildPatternArray(new int[20], essentialPattern, new int[5]));
                                    Thread.sleep(16, 666000);
                                }
                            } else {
                                int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
                                int[] steps = {1, 2, 4, 7};
                                for (int i : steps) {
                                    if (checkInterruption("essential"))
                                        throw new InterruptedException();
                                    updateLedSingle(led, Constants.getMaxBrightness() / 100 * i);
                                    Thread.sleep(25);
                                }
                                Thread.sleep(250);
                            }
                        } catch (InterruptedException e) {
                        }

                        StatusManager.setAnimationActive(false);
                        StatusManager.setEssentialLedActive(true);
                        if (DEBUG) Log.d(TAG, "Done playing animation | name: essential");
                    });
        } else {
            if (Constants.getDevice().equals("phone3a")) {
                int[] essentialPattern = new int[11];
                int patternBrightness = Constants.getMaxBrightness() / 100 * 60;
                Arrays.fill(essentialPattern, patternBrightness);
                updateLedFrame(ResourceUtils.buildPatternArray(new int[20], essentialPattern, new int[5]));
            } else {
                int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
                updateLedSingle(led, Constants.getMaxBrightness() / 100 * 7);
            }
            return;
        }
    }

    public static void stopEssential() {
        if (DEBUG) Log.d(TAG, "Disabling Essential Animation");
        StatusManager.setEssentialLedActive(false);
        if (!StatusManager.isAnimationActive() && !StatusManager.isAllLedActive()) {
            int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
            updateLedSingle(led, 0);
        }
    }

    public static void playMusic(String name) {
        submit(
                () -> {
                    float maxBrightness = (float) Constants.getMaxBrightness();
                    float[] pattern;

                    if (Constants.getDevice().equals("phone3a")) {
                        float[] zone1 = new float[20]; // largest (left 2)
                        float[] zone2 = new float[11]; // medium (right 1)
                        float[] zone3 = new float[5]; // smallest (left)

                        switch (name) {
                            case "low":
                                Arrays.fill(zone1, maxBrightness);
                                break;
                            case "mid":
                                Arrays.fill(zone2, maxBrightness);
                                break;
                            case "high":
                                Arrays.fill(zone3, maxBrightness);
                                break;
                            default:
                                if (DEBUG)
                                    Log.d(TAG, "Name doesn't match any zone, returning | name: " + name);
                                return;
                        }
                        pattern = ResourceUtils.buildPatternArray(zone1, zone2, zone3);
                    } else {
                        pattern = new float[5];

                        switch (name) {
                            case "low":
                                pattern[4] = maxBrightness;
                                break;
                            case "mid_low":
                                pattern[3] = maxBrightness;
                                break;
                            case "mid":
                                pattern[2] = maxBrightness;
                                break;
                            case "mid_high":
                                pattern[0] = maxBrightness;
                                break;
                            case "high":
                                pattern[1] = maxBrightness;
                                break;
                            default:
                                if (DEBUG)
                                    Log.d(TAG, "Name doesn't match any zone, returning | name: " + name);
                                return;
                        }
                    }

                    try {
                        updateLedFrame(pattern);
                        Thread.sleep(90);
                    } catch (Exception e) {
                        if (DEBUG)
                            Log.d(
                                    TAG,
                                    "Exception while playing animation | name: music: "
                                            + name
                                            + " | exception: "
                                            + e);
                    } finally {
                        if (Constants.getDevice().equals("phone3a")) {
                            updateLedFrame(new float[36]);
                        } else {
                            updateLedFrame(new float[5]);
                        }
                        if (DEBUG) Log.d(TAG, "Done playing animation | name: " + name);
                    }
                });
    }

    private static void updateLedFrame(String[] pattern) {
        updateLedFrame(Arrays.stream(pattern).mapToInt(Integer::parseInt).toArray());
    }

    public static void updateLedFrame(int[] pattern) {
        float[] floatPattern = new float[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            floatPattern[i] = (float) pattern[i];
        }
        updateLedFrame(floatPattern);
    }

    private static void updateLedFrame(float[] pattern) {
        // if (DEBUG) Log.d(TAG, "Updating pattern: " + pattern);
        float maxBrightness = (float) Constants.getMaxBrightness();
        if (StatusManager.isEssentialLedActive()) {
            if (pattern.length == 5) { // Phone (1) pattern
                if (pattern[1] < (maxBrightness / 100 * 7)) {
                    pattern[1] = maxBrightness / 100 * 7;
                }
            } else if (pattern.length == 33) { // Phone (2) pattern
                if (pattern[2] < (maxBrightness / 100 * 7)) {
                    pattern[2] = maxBrightness / 100 * 7;
                }
            } else if (pattern.length == 36) { // Phone (3a) pattern
                if (pattern[21] < (maxBrightness / 100 * 60)) {
                    Arrays.fill(pattern, 20, 31, maxBrightness / 100 * 60);
                }
            }
        }
        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = pattern[i] / maxBrightness * Constants.getBrightness();
        }
        FileUtils.writeFrameLed(pattern);
    }

    private static void updateLedSingle(int led, String brightness) {
        updateLedSingle(led, Float.parseFloat(brightness));
    }

    private static void updateLedSingle(int led, int brightness) {
        updateLedSingle(led, (float) brightness);
    }

    private static void updateLedSingle(int led, float brightness) {
        // if (DEBUG) Log.d(TAG, "Updating led | led: " + led + " | brightness: " + brightness);
        float maxBrightness = (float) Constants.getMaxBrightness();
        int essentialLed = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
        if (StatusManager.isEssentialLedActive()
                && led == essentialLed
                && brightness < (maxBrightness / 100 * 7)) {
            brightness = maxBrightness / 100 * 7;
        }
        FileUtils.writeSingleLed(led, brightness / maxBrightness * Constants.getBrightness());
    }
}
