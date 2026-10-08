/*
 * Copyright (C) 2023 The Android Open Source Project
 * Copyright (C) The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
*/

use std::sync::Mutex;

use log::{error, info};

use android_hardware_light::aidl::android::hardware::light::{
    HwLight::HwLight, HwLightEffect::HwLightEffect, HwLightState::HwLightState, ILights::ILights,
    LightType::LightType,
};

use binder::{ExceptionCode, Interface, Status};

use crate::glyph::{Frame, Glyph, NUM_LEDS};

struct State {
    frame: Frame,
    glyph: Glyph,
}

/// Defined so we can implement the ILights AIDL interface.
pub struct LightsService {
    state: Mutex<State>,
}

impl Interface for LightsService {}

impl LightsService {
    fn validate_light(id: i32) -> Result<usize, ExceptionCode> {
        usize::try_from(id)
            .ok()
            .filter(|&index| index < NUM_LEDS)
            .ok_or(ExceptionCode::UNSUPPORTED_OPERATION)
    }
}

impl Default for LightsService {
    fn default() -> Self {
        Self { state: Mutex::new(State { frame: [0; NUM_LEDS], glyph: Glyph::new() }) }
    }
}

impl ILights for LightsService {
    fn setLightState(&self, id: i32, state: &HwLightState) -> binder::Result<()> {
        let index = Self::validate_light(id).map_err(|e| Status::new_exception(e, None))?;
        let mut locked = self.state.lock().unwrap();
        let locked = &mut *locked;
        locked.frame[index] = color_to_level(state.color);
        if let Err(e) = locked.glyph.write_frame(&locked.frame) {
            error!("Failed to write Glyph frame: {e}");
        }
        Ok(())
    }

    fn setLightEffects(&self, _effects: &[HwLightEffect]) -> binder::Result<()> {
        error!("Lights effects are not supported");
        Err(Status::new_exception(ExceptionCode::UNSUPPORTED_OPERATION, None))
    }

    fn getLights(&self) -> binder::Result<Vec<HwLight>> {
        info!("Lights reporting supported lights");
        Ok((0..NUM_LEDS as i32)
            .map(|id| HwLight {
                id,
                ordinal: id,
                r#type: LightType::APPLICATION,
                minUpdatePeriodMillis: 0,
            })
            .collect())
    }
}

fn color_to_level(color: i32) -> u8 {
    let [_, r, g, b] = color.to_be_bytes();
    r.max(g).max(b)
}
