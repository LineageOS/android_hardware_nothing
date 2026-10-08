/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.utils;

import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.hardware.lights.LightsManager;
import android.hardware.lights.LightsRequest;
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

    private static int toColor(float brightness) {
        int level = Math.round(brightness / Constants.getMaxBrightness() * 255);
        level = Math.max(0, Math.min(255, level));
        return 0xFF000000 | (level << 16) | (level << 8) | level;
    }

    private static LightState toState(float brightness) {
        return new LightState.Builder().setColor(toColor(brightness)).build();
    }
}
