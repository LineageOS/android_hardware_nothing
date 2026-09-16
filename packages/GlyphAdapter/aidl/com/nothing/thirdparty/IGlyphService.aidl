//
// Copyright (C) 2025 The LineageOS Project
// Copyright (C) 2026 Paranoid Android
//
// SPDX-License-Identifier: Apache-2.0
//

package com.nothing.thirdparty;

interface IGlyphService {
    // Wire transaction order; append, never insert. Matrix unsupported on Phone (1)/(2)/(2a).
    void setFrameColors(in int[] iArray);
    void openSession();
    void closeSession();
    boolean register(in String str);
    boolean registerSDK(in String str1, in String str2);
    boolean registerMatrixSDK(in String str);
    void setMatrixColors(in int[] iArray);
    void setGlyphMatrixTimeout(boolean active);
    void setAppMatrixColors(in int[] iArray);
    void closeAppMatrix();
}
