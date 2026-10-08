/*
 * Copyright (C) 2023 The Android Open Source Project
 * Copyright (C) The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
*/

use std::collections::HashMap;
use std::sync::Mutex;

use log::info;
use log::error;

use android_hardware_light::aidl::android::hardware::light::{
    HwLight::HwLight, HwLightEffect::HwLightEffect, HwLightState::HwLightState, ILights::ILights,
    LightType::LightType,
};

use binder::{ExceptionCode, Interface, Status};

struct Light {
    hw_light: HwLight,
    state: HwLightState,
    effect: HwLightEffect,
}

const NUM_DEFAULT_LIGHTS: i32 = 3;
const MIN_UPDATE_PERIOD_MS: i32 = 33;


/// Defined so we can implement the ILights AIDL interface.
pub struct LightsService {
    lights: Mutex<HashMap<i32, Light>>,
}

impl Interface for LightsService {}

impl LightsService {
    fn new(hw_lights: impl IntoIterator<Item = HwLight>) -> Self {
        let mut lights_map = HashMap::new();

        for hw_light in hw_lights {
            lights_map.insert(hw_light.id,
                              Light {
                                  hw_light,
                                  state: Default::default(),
                                  effect: Default::default(),
                              });
        }

        Self { lights: Mutex::new(lights_map) }
    }

    fn validate_effect(&self, effect: &HwLightEffect) -> ExceptionCode {
        let light;

        // Check that the light exists.
        let binding = self.lights.lock().unwrap();
        if let Some(target_light) = binding.get(&effect.lightId) {
            light = target_light;
        } else {
            return ExceptionCode::UNSUPPORTED_OPERATION;
        }

        // Check that the light supports animations.
        if light.hw_light.minUpdatePeriodMillis == 0 {
            return ExceptionCode::UNSUPPORTED_OPERATION;
        }

        // Check that the time series has minimum length requirements.
        if effect.colors.len() == 0
            || effect.frames.len() == 0
            || effect.frames.len() != effect.colors.len() {
                return ExceptionCode::ILLEGAL_ARGUMENT;
        }

        for i in 0..effect.frames.len() {
            if i == 0 && effect.frames[i] == 0 {
                // First frame is allowed to have a 0 to set initial conditions.
                continue;
            }

            if effect.frames[i] < 1 {
                // All other cases should specify a positive frame count.
                return ExceptionCode::ILLEGAL_ARGUMENT;
            }
        }

        // Has a valid frame rate.
        if effect.framePeriodMillis < light.hw_light.minUpdatePeriodMillis {
            return ExceptionCode::ILLEGAL_ARGUMENT;
        }

        // Has valid number of iterations. 0 is OK and it means infinite.
        if effect.iterations < 0 {
            return ExceptionCode::ILLEGAL_ARGUMENT;
        }

        return ExceptionCode::NONE;
    }
}

impl Default for LightsService {
    fn default() -> Self {
        let id_mapping_closure =
            |light_id| HwLight {
                id: light_id,
                ordinal: light_id,
                r#type: LightType::BACKLIGHT,
                minUpdatePeriodMillis:
                    if light_id == 1 {
                        MIN_UPDATE_PERIOD_MS
                    } else {
                        0
                    },
            };

        Self::new((1..=NUM_DEFAULT_LIGHTS).map(id_mapping_closure))
    }
}

impl ILights for LightsService {
    fn setLightState(&self, id: i32, state: &HwLightState) -> binder::Result<()> {
        info!("Lights setting state for id={} to color {:x}", id, state.color);

        if let Some(light) = self.lights.lock().unwrap().get_mut(&id) {
            light.state = *state;
            return Ok(());
        } else {
            return Err(Status::new_exception(ExceptionCode::UNSUPPORTED_OPERATION, None));
        }
    }

    fn setLightEffects(&self, effects: &[HwLightEffect]) -> binder::Result<()> {
        info!("Lights setting effect for {} lights: {:?}", effects.len(), effects);

        for effect in effects {
            let validation_err = self.validate_effect(effect);
            if validation_err != ExceptionCode::NONE {
                error!("Lights effect for {} is not valid. {:#?}", effect.lightId, validation_err);
                return Err(Status::new_exception(validation_err, None));
            }
        }

        for effect in effects {
            if let Some(light) = self.lights.lock().unwrap().get_mut(&effect.lightId) {
                light.effect = effect.clone();
            }
        }

        return Ok(());
    }

    fn getLights(&self) -> binder::Result<Vec<HwLight>> {
        info!("Lights reporting supported lights");
        Ok(self.lights.lock().unwrap().values().map(|light| light.hw_light).collect())
    }
}
