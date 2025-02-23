package com.nothing.thirdparty;

interface IGlyphService {
    void setFrameColors(in int[] iArray);
    void openSession();
    void closeSession();
    boolean register(in String str);
    boolean registerSDK(in String str1, in String str2);
}