/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.felica

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemProperties
import android.util.Log

object FelicaDisabler {
    private const val TAG = "NothingFelicaDisabler"

    private val FELICA_PACKAGES =
        listOf(
            "com.felicanetworks.mfm.main",
            "com.felicanetworks.mfs",
            "com.felicanetworks.mfc",
            "com.felicanetworks.mfw.a.boot",
        )

    fun enableOrDisableFelica(context: Context) {
        val pm = context.packageManager
        val sku = SystemProperties.get("ro.boot.hardware.sku", "")
        val isJapaneseVariant = sku == "JPN"
        val flag =
            if (isJapaneseVariant) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        for (pkg in FELICA_PACKAGES) {
            try {
                pm.setApplicationEnabledSetting(pkg, flag, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to change state for $pkg: ${e.message}")
            }
        }
    }
}
