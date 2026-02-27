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

package co.aospa.glyph.settings;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import co.aospa.glyph.R;
import co.aospa.glyph.manager.SettingsManager;
import co.aospa.glyph.preference.GlyphAnimationPreference;
import co.aospa.glyph.services.NotificationService;
import co.aospa.glyph.utils.Constants;
import co.aospa.glyph.utils.ResourceUtils;
import co.aospa.glyph.utils.ServiceUtils;

import com.android.internal.util.ArrayUtils;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotifsSettingsFragment extends SettingsBasePreferenceFragment
        implements OnPreferenceChangeListener, OnCheckedChangeListener {

    private PreferenceScreen mScreen;

    private MainSwitchPreference mSwitchBar;
    private PreferenceCategory mCategory;

    private List<String> mEssentialApps = new ArrayList<String>();
    private List<String> mEssentialAppsNames = new ArrayList<String>();

    private PackageManager mPackageManager;

    private ListPreference mListPreference;
    private MultiSelectListPreference mMultiSelectListPreference;

    private GlyphAnimationPreference mGlyphAnimationPreference;

    private Handler mHandler = new Handler();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.glyph_notifs_settings);

        mScreen = this.getPreferenceScreen();
        getActivity().setTitle(R.string.glyph_settings_notifs_toggle_title);

        mSwitchBar = (MainSwitchPreference) findPreference(Constants.GLYPH_NOTIFS_SUB_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(SettingsManager.isGlyphNotifsEnabled());

        mCategory = (PreferenceCategory) findPreference(Constants.GLYPH_NOTIFS_SUB_CATEGORY);

        mListPreference = (ListPreference) findPreference(Constants.GLYPH_NOTIFS_SUB_ANIMATIONS);
        mListPreference.setOnPreferenceChangeListener(this);
        mListPreference.setEntries(ResourceUtils.getNotificationAnimations());
        mListPreference.setEntryValues(ResourceUtils.getNotificationAnimations());
        if (!ArrayUtils.contains(
                ResourceUtils.getNotificationAnimations(), mListPreference.getValue())) {
            mListPreference.setValue(ResourceUtils.getString("glyph_settings_notifs_animations_default"));
        }

        mGlyphAnimationPreference =
                (GlyphAnimationPreference) findPreference(Constants.GLYPH_NOTIFS_SUB_PREVIEW);

        mPackageManager = getActivity().getPackageManager();
        List<ApplicationInfo> mApps = mPackageManager.getInstalledApplications(PackageManager.GET_GIDS);
        Collections.sort(mApps, new ApplicationInfo.DisplayNameComparator(mPackageManager));
        for (ApplicationInfo app : mApps) {
            if (mPackageManager.getLaunchIntentForPackage(app.packageName) != null
                    && !ArrayUtils.contains(
                    Constants.APPS_TO_IGNORE, app.packageName)) { // apps with launcher intent
                SwitchPreference mSwitchPreference = new SwitchPreference(mScreen.getContext());
                mSwitchPreference.setKey(app.packageName);
                mSwitchPreference.setTitle(
                        " "
                                + app.loadLabel(mPackageManager)
                                .toString()); // add this space since the layout looks off otherwise
                mSwitchPreference.setIcon(app.loadIcon(mPackageManager));
                mSwitchPreference.setDefaultValue(true);
                mSwitchPreference.setOnPreferenceChangeListener(this);
                mCategory.addPreference(mSwitchPreference);

                mEssentialApps.add(app.packageName);
                mEssentialAppsNames.add(app.loadLabel(mPackageManager).toString());
            }
        }

        mMultiSelectListPreference =
                (MultiSelectListPreference) findPreference(Constants.GLYPH_NOTIFS_SUB_ESSENTIAL);
        mMultiSelectListPreference.setOnPreferenceChangeListener(this);
        mMultiSelectListPreference.setEntries(mEssentialAppsNames.toArray(new CharSequence[0]));
        mMultiSelectListPreference.setEntryValues(mEssentialApps.toArray(new CharSequence[0]));
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mGlyphAnimationPreference.updateAnimation(
                SettingsManager.isGlyphNotifsEnabled(), SettingsManager.getGlyphNotifsAnimation(), 1500);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();

        if (preferenceKey.equals(Constants.GLYPH_NOTIFS_SUB_ANIMATIONS)) {
            mGlyphAnimationPreference.updateAnimation(
                    SettingsManager.isGlyphNotifsEnabled(), newValue.toString(), 1500);
        }

        if (preferenceKey.equals(Constants.GLYPH_NOTIFS_SUB_ESSENTIAL)) {
            // if (DEBUG) Log.d(TAG, "onPreferenceChange: " + newValue.toString());
        }

        // mHandler.post(() -> ServiceUtils.checkGlyphService());

        return true;
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (Constants.GLYPH_NOTIFS_SUB_ANIMATIONS.equals(preference.getKey())) {
            AnimationListPreferenceDialogFragment fragment =
                    AnimationListPreferenceDialogFragment.newInstance(preference.getKey(), false);
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), "AnimationListPreferenceDialog");
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked && !hasNotificationAccess()) {
            mSwitchBar.setChecked(false);
            Toast.makeText(
                            getContext(), R.string.glyph_settings_notifs_permission_required, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        SettingsManager.setGlyphNotifsEnabled(isChecked);
        ServiceUtils.checkGlyphService();
        mGlyphAnimationPreference.updateAnimation(
                isChecked, SettingsManager.getGlyphNotifsAnimation(), 1500);
    }

    private boolean hasNotificationAccess() {
        NotificationManager notificationManager =
                (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        return notificationManager != null
                && notificationManager.isNotificationListenerAccessGranted(
                new ComponentName(getContext(), NotificationService.class));
    }
}
