/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.settings;

import android.os.Bundle;
import android.os.Handler;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import co.aospa.glyph.R;
import co.aospa.glyph.utils.Constants;
import co.aospa.glyph.utils.ServiceUtils;

public class PowershareSettingsFragment extends SettingsBasePreferenceFragment
        implements OnPreferenceChangeListener {

    private final Handler mHandler = new Handler();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.glyph_charging_powershare_settings);

        getActivity().setTitle(R.string.glyph_settings_charging_powershare_title);

        MainSwitchPreference switchBar = findPreference(Constants.GLYPH_CHARGING_POWERSHARE_ENABLE);
        switchBar.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        mHandler.post(() -> ServiceUtils.checkGlyphService());
        return true;
    }
}
