package com.nothing.thirdparty

import android.app.Service
import android.content.Intent

public class GlyphService : Service() {
    private val binder by lazy { IGlyphServiceImpl(this) }
    override fun onBind(intent: Intent?) = binder
}