/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.utils;

import android.hardware.lights.ColorSequence;
import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.hardware.lights.LightsManager;
import android.hardware.lights.LightsRequest;
import android.hardware.lights.MultiLightEffect;
import android.util.Log;

import java.util.Comparator;
import java.util.List;

public final class GlyphLights {

    private static final String TAG = "GlyphLights";
    private static final boolean DEBUG = true;

    private static boolean sInitialized = false;
    private static List<Light> sLights = List.of();
    private static LightsManager.LightsSession sSession;

    private static synchronized boolean init() {
        if (!sInitialized) {
            sInitialized = true;

            LightsManager lightsManager = Constants.CONTEXT.getSystemService(LightsManager.class);
            if (lightsManager != null) {
                sLights = lightsManager.getLights().stream()
                        .filter(light -> light.getType() == Light.LIGHT_TYPE_APPLICATION)
                        .sorted(Comparator.comparingInt(Light::getOrdinal))
                        .toList();
            }
            if (!sLights.isEmpty()) {
                sSession = lightsManager.openSession();
            }
            if (DEBUG) Log.d(TAG, "Glyph LEDs exposed by the lights HAL: " + sLights.size());
        }
        return sSession != null;
    }

    public static boolean supportsEffects() {
        return init() && sLights.stream().allMatch(Light::hasAnimationControl);
    }

    public static void writeFrame(float[] frame) {
        if (!init()) {
            FileUtils.writeFrameLed(frame);
            return;
        }
        LightsRequest.Builder request = new LightsRequest.Builder();
        for (int i = 0; i < frame.length && i < sLights.size(); i++) {
            request.addLight(sLights.get(i), toState(frame[i]));
        }
        sSession.requestLights(request.build());
    }

    public static void writeAll(float brightness) {
        if (!init()) {
            FileUtils.writeAllLed(brightness);
            return;
        }
        LightsRequest.Builder request = new LightsRequest.Builder();
        LightState state = toState(brightness);
        for (Light light : sLights) {
            request.addLight(light, state);
        }
        sSession.requestLights(request.build());
    }

    public static void writeSingle(int led, float brightness) {
        if (!init()) {
            FileUtils.writeSingleLed(led, brightness);
            return;
        }
        if (led < 0 || led >= sLights.size()) {
            Log.w(TAG, "Invalid LED: " + led);
            return;
        }
        sSession.requestLights(new LightsRequest.Builder()
                .addLight(sLights.get(led), toState(brightness))
                .build());
    }

    public static long playFrames(List<float[]> frames, int iterations) {
        if (!supportsEffects() || frames.isEmpty()) {
            return 0;
        }
        long period = sLights.get(0).getMinUpdatePeriodMillis();

        MultiLightEffect.Builder effect = new MultiLightEffect.Builder()
                .setIterations(iterations)
                .setPreemptive(true);
        for (int led = 0; led < sLights.size(); led++) {
            ColorSequence.Builder sequence = new ColorSequence.Builder()
                    .setInterpolationMode(ColorSequence.INTERPOLATION_MODE_NONE);
            int lastFrame = 0;
            int lastColor = toColor(level(frames.get(0), led));
            sequence.addControlPoint(0, lastColor);
            for (int i = 1; i < frames.size(); i++) {
                int color = toColor(level(frames.get(i), led));
                if (color != lastColor) {
                    sequence.addControlPoint((i - lastFrame) * period, color);
                    lastFrame = i;
                    lastColor = color;
                }
            }
            sequence.addControlPoint((frames.size() - lastFrame) * period, lastColor);
            effect.addLightSequence(sLights.get(led), sequence.build());
        }

        sSession.requestLights(new LightsRequest.Builder().setEffect(effect.build()).build());
        return frames.size() * period;
    }

    private static float level(float[] frame, int led) {
        return led < frame.length ? frame[led] : 0;
    }

    private static int toColor(float brightness) {
        int level = Math.round(brightness / Constants.getMaxBrightness() * 255);
        level = Math.max(0, Math.min(255, level));
        return 0xFF000000 | (level << 16) | (level << 8) | level;
    }

    private static LightState toState(float brightness) {
        return new LightState.Builder().setColor(toColor(brightness)).build();
    }
}
