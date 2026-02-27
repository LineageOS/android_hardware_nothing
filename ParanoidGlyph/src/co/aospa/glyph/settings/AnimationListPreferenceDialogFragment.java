/*
 * Copyright (C) 2026 Paranoid Android
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

package co.aospa.glyph.settings;

import android.content.DialogInterface;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.ListPreferenceDialogFragmentCompat;

import co.aospa.glyph.manager.AnimationManager;
import co.aospa.glyph.manager.SettingsManager;

public class AnimationListPreferenceDialogFragment extends ListPreferenceDialogFragmentCompat {

    private static final String ARG_IS_CALL_ANIMATION = "is_call_animation";

    private int mClickedDialogEntryIndex;

    public static AnimationListPreferenceDialogFragment newInstance(
            String key, boolean isCallAnimation) {
        final AnimationListPreferenceDialogFragment fragment =
                new AnimationListPreferenceDialogFragment();
        final Bundle b = new Bundle(2);
        b.putString(ARG_KEY, key);
        b.putBoolean(ARG_IS_CALL_ANIMATION, isCallAnimation);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        AnimationManager.stopPreviewOnce();
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        ListPreference preference = (ListPreference) getPreference();
        CharSequence[] entries = preference.getEntries();
        CharSequence[] entryValues = preference.getEntryValues();

        if (entries == null || entryValues == null || entries.length != entryValues.length) {
            return;
        }

        mClickedDialogEntryIndex = preference.findIndexOfValue(preference.getValue());

        builder.setSingleChoiceItems(
                entries,
                mClickedDialogEntryIndex,
                (dialog, which) -> {
                    mClickedDialogEntryIndex = which;
                    boolean isCallAnimation =
                            getArguments() != null && getArguments().getBoolean(ARG_IS_CALL_ANIMATION, false);
                    if (isCallAnimation
                            ? SettingsManager.isGlyphCallEnabled()
                            : SettingsManager.isGlyphNotifsEnabled()) {
                        AnimationManager.previewOnce(entryValues[which].toString(), isCallAnimation);
                    }
                });
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult && mClickedDialogEntryIndex >= 0) {
            ListPreference preference = (ListPreference) getPreference();
            String value = preference.getEntryValues()[mClickedDialogEntryIndex].toString();
            if (preference.callChangeListener(value)) {
                preference.setValue(value);
            }
        }
    }
}
