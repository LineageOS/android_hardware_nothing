/*
 * Copyright (C) 2022,2025-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <stdint.h>
#ifndef IMPL_V2
#include "fingerprint.h"
#else
#include "fingerprint-v2.h"
#endif

class UdfpsHandler {
  public:
    virtual ~UdfpsHandler() = default;

    virtual void init(fingerprint_device_t* device) {};
    virtual void onFingerDown(uint32_t x, uint32_t y, float minor, float major) {};
    virtual void onFingerUp() {};

    virtual void onAcquired(int32_t result, int32_t vendorCode) {};
    virtual void onAuthenticationSucceeded() {};
    virtual void onAuthenticationFailed() {};
    virtual void cancel() {};

    virtual void onEnroll() {};
    virtual void onAuthenticate() {};
    virtual void onChallengeRevoked() {};
    virtual void onEnumerate() {};
    virtual void onError(int32_t error, int32_t vendorCode) {};
    virtual void onUiReady() {};
    virtual void setIgnoreDisplayTouches(bool shouldIgnore) {};
    virtual void onSessionClosed() {};
};

struct UdfpsHandlerFactory {
    UdfpsHandler* (*create)();
    void (*destroy)(UdfpsHandler* handler);
};

UdfpsHandlerFactory* getUdfpsHandlerFactory();
