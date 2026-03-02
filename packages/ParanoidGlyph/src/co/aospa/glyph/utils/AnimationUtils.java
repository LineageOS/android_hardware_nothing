/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.utils;

public final class AnimationUtils {

    public static float[] buildPatternArray(float[]... arrays) {
        int totalLength = 0;
        for (float[] arr : arrays) {
            totalLength += arr.length;
        }

        float[] result = new float[totalLength];
        int pos = 0;
        for (float[] arr : arrays) {
            System.arraycopy(arr, 0, result, pos, arr.length);
            pos += arr.length;
        }
        return result;
    }

    public static float[] reverseFrameArray(float[] array) {
        float[] copy = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            copy[i] = array[array.length - 1 - i];
        }
        return copy;
    }
}
