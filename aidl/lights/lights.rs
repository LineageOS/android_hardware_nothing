/*
 * Copyright (C) 2023 The Android Open Source Project
 * Copyright (C) The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
*/

use std::sync::{Arc, Condvar, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use log::{error, info};

use android_hardware_light::aidl::android::hardware::light::{
    HwLight::HwLight, HwLightEffect::HwLightEffect, HwLightState::HwLightState, ILights::ILights,
    LightType::LightType,
};

use binder::{ExceptionCode, Interface, Status};

use crate::glyph::{Frame, Glyph, NUM_LEDS};

const COALESCE_QUIET: Duration = Duration::from_millis(2);
const COALESCE_MAX: Duration = Duration::from_millis(16);

struct State {
    frame: Frame,
    dirty: bool,
}

struct Shared {
    state: Mutex<State>,
    cond: Condvar,
}

/// Defined so we can implement the ILights AIDL interface.
pub struct LightsService {
    shared: Arc<Shared>,
}

impl Interface for LightsService {}

impl LightsService {
    fn new() -> Self {
        let shared = Arc::new(Shared {
            state: Mutex::new(State { frame: [0; NUM_LEDS], dirty: true }),
            cond: Condvar::new(),
        });

        let writer_shared = Arc::clone(&shared);
        thread::Builder::new()
            .name("glyph_writer".into())
            .spawn(move || writer_loop(&writer_shared))
            .expect("Failed to spawn writer thread");

        Self { shared }
    }

    fn validate_light(id: i32) -> Result<usize, ExceptionCode> {
        usize::try_from(id)
            .ok()
            .filter(|&index| index < NUM_LEDS)
            .ok_or(ExceptionCode::UNSUPPORTED_OPERATION)
    }
}

impl Default for LightsService {
    fn default() -> Self {
        Self::new()
    }
}

impl ILights for LightsService {
    fn setLightState(&self, id: i32, state: &HwLightState) -> binder::Result<()> {
        let index = Self::validate_light(id).map_err(|e| Status::new_exception(e, None))?;
        let level = color_to_level(state.color);

        let mut locked = self.shared.state.lock().unwrap();
        if locked.frame[index] != level {
            locked.frame[index] = level;
            locked.dirty = true;
            self.shared.cond.notify_one();
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

fn writer_loop(shared: &Shared) {
    let mut glyph = Glyph::new();
    let mut locked = shared.state.lock().unwrap();
    loop {
        locked = shared.cond.wait_while(locked, |s| !s.dirty).unwrap();

        let burst_start = Instant::now();
        loop {
            locked.dirty = false;
            let (relocked, timeout) = shared.cond.wait_timeout(locked, COALESCE_QUIET).unwrap();
            locked = relocked;
            if (timeout.timed_out() && !locked.dirty) || burst_start.elapsed() >= COALESCE_MAX {
                break;
            }
        }
        locked.dirty = false;

        let frame = locked.frame;
        drop(locked);
        if let Err(e) = glyph.write_frame(&frame) {
            error!("Failed to write Glyph frame: {e}");
        }
        locked = shared.state.lock().unwrap();
    }
}
