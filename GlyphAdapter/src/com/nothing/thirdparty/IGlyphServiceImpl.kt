package com.nothing.thirdparty

import android.util.Log

class IGlyphServiceImpl : IGlyphService.Stub() {

    override fun setFrameColors(iArray: IntArray?) {
        Log.i("IGlyphServiceImpl", "updateLedFrame - ${iArray.contentToString()}")
    }

    override fun openSession() {
        Log.i("IGlyphServiceImpl", "openSession")
    }

    override fun closeSession() {
        Log.i("IGlyphServiceImpl", "closeSession")
    }


    override fun register(str: String) = true
    override fun registerSDK(str1: String, str2: String) = true
}