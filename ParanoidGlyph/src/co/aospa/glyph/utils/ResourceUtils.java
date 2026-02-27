/*
 * Copyright (C) 2023-2024 Paranoid Android
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

package co.aospa.glyph.utils;

import android.content.res.AssetManager;
import android.content.res.Resources;

import com.android.internal.util.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;

public final class ResourceUtils {

    private static final String TAG = "GlyphResourceUtils";
    private static final boolean DEBUG = true;

    private static final AssetManager assetManager = Constants.CONTEXT.getAssets();

    private static String[] callAnimations = null;
    private static String[] notificationAnimations = null;

    public static int getIdentifier(String id, String type) {
        return Constants.CONTEXT
                .getResources()
                .getIdentifier(id, type, Constants.CONTEXT.getPackageName());
    }

    public static Boolean getBoolean(String id) {
        int resId = getIdentifier(id, "bool");
        if (resId == 0) return false;
        try {
            return Constants.CONTEXT.getResources().getBoolean(resId);
        } catch (Resources.NotFoundException e) {
            return false;
        }
    }

    public static String getString(String id) {
        int resId = getIdentifier(id, "string");
        if (resId == 0) return "";
        try {
            return Constants.CONTEXT.getResources().getString(resId);
        } catch (Resources.NotFoundException e) {
            return "";
        }
    }

    public static int getInteger(String id) {
        return getInteger(id, 0);
    }

    public static int getInteger(String id, int defaultValue) {
        int resId = getIdentifier(id, "integer");
        if (resId == 0) return defaultValue;
        try {
            return Constants.CONTEXT.getResources().getInteger(resId);
        } catch (Resources.NotFoundException e) {
            return defaultValue;
        }
    }

    public static String[] getStringArray(String id) {
        int resId = getIdentifier(id, "array");
        if (resId == 0) return new String[0];
        try {
            return Constants.CONTEXT.getResources().getStringArray(resId);
        } catch (Resources.NotFoundException e) {
            return new String[0];
        }
    }

    public static int[] getIntArray(String id) {
        int resId = getIdentifier(id, "array");
        if (resId == 0) return new int[0];
        try {
            return Constants.CONTEXT.getResources().getIntArray(resId);
        } catch (Resources.NotFoundException e) {
            return new int[0];
        }
    }

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

    public static int[] buildPatternArray(int[]... arrays) {
        int totalLength = 0;
        for (int[] arr : arrays) {
            totalLength += arr.length;
        }

        int[] result = new int[totalLength];
        int pos = 0;
        for (int[] arr : arrays) {
            System.arraycopy(arr, 0, result, pos, arr.length);
            pos += arr.length;
        }
        return result;
    }

    public static int[] reverseFrameArray(int[] array) {
        int[] copy = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            copy[i] = array[array.length - 1 - i];
        }
        return copy;
    }

    public static float[] reverseFrameArray(float[] array) {
        float[] copy = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            copy[i] = array[array.length - 1 - i];
        }
        return copy;
    }

    public static String[] getCallAnimations() {
        if (callAnimations == null) {
            try {
                String[] assets = assetManager.list("call");
                for (int i = 0; i < assets.length; i++) {
                    assets[i] = assets[i].replaceAll(".csv", "");
                }
                callAnimations = assets;
            } catch (IOException e) {
            }
        }
        return callAnimations;
    }

    public static String[] getNotificationAnimations() {
        if (notificationAnimations == null) {
            try {
                String[] assets = assetManager.list("notification");
                for (int i = 0; i < assets.length; i++) {
                    assets[i] = assets[i].replaceAll(".csv", "");
                }
                notificationAnimations = assets;
            } catch (IOException e) {
            }
        }
        return notificationAnimations;
    }

    public static InputStream getCallAnimation(String name) throws IOException {
        if (callAnimations == null) getCallAnimations();

        if (ArrayUtils.contains(callAnimations, name))
            return assetManager.open("call/" + name + ".csv");

        return assetManager.open(
                "call/" + ResourceUtils.getString("glyph_settings_call_animations_default") + ".csv");
    }

    public static InputStream getNotificationAnimation(String name) throws IOException {
        if (notificationAnimations == null) getNotificationAnimations();

        if (ArrayUtils.contains(notificationAnimations, name))
            return assetManager.open("notification/" + name + ".csv");

        return assetManager.open(
                "call/" + ResourceUtils.getString("glyph_settings_notifs_animations_default") + ".csv");
    }

    public static InputStream getAnimation(String name) throws IOException {
        if (callAnimations == null) getCallAnimations();
        if (notificationAnimations == null) getNotificationAnimations();

        if (ArrayUtils.contains(callAnimations, name)) {
            return getCallAnimation(name);
        }

        if (ArrayUtils.contains(notificationAnimations, name)) {
            return getNotificationAnimation(name);
        }

        return assetManager.open(name + ".csv");
    }
}
