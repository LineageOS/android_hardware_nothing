/*
 * Copyright (C) 2023 The Android Open Source Project
 * Copyright (C) The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
*/

use std::sync::{Arc, Condvar, Mutex, MutexGuard};
use std::thread;
use std::time::{Duration, Instant};

use log::{error, info, warn};

use android_hardware_light::aidl::android::hardware::light::{
    HwLight::HwLight, HwLightEffect::HwLightEffect, HwLightState::HwLightState, ILights::ILights,
    LightType::LightType,
};

use binder::{ExceptionCode, Interface, Status};

use crate::effect::{to_level, Track};
use crate::glyph::{color_to_level, Frame, Glyph, NUM_LEDS};
use crate::strips::{LedStrips, FRAMES_PER_BUFFER, FRAME_PERIOD_MS};

const MIN_UPDATE_PERIOD_MS: i32 = 17;

const COALESCE_QUIET: Duration = Duration::from_millis(2);
const COALESCE_MAX: Duration = Duration::from_millis(16);

const LOOKAHEAD_BUFFERS: usize = 3;
const REFILL_INTERVAL: Duration = Duration::from_millis(50);

struct State {
    tracks: Vec<Track>,
    generation: u64,
    dirty: bool,
}

struct Shared {
    state: Mutex<State>,
    cond: Condvar,
    epoch: Instant,
}

impl Shared {
    fn now(&self) -> f64 {
        self.epoch.elapsed().as_secs_f64() * 1000.0
    }

    fn update(&self, f: impl FnOnce(&mut [Track])) {
        let mut state = self.state.lock().unwrap();
        f(&mut state.tracks);
        state.generation += 1;
        state.dirty = true;
        self.cond.notify_one();
    }
}

/// Defined so we can implement the ILights AIDL interface.
pub struct LightsService {
    shared: Arc<Shared>,
}

impl Interface for LightsService {}

impl LightsService {
    fn new() -> Self {
        let shared = Arc::new(Shared {
            state: Mutex::new(State {
                tracks: vec![Track::default(); NUM_LEDS],
                generation: 0,
                dirty: true,
            }),
            cond: Condvar::new(),
            epoch: Instant::now(),
        });

        let player_shared = Arc::clone(&shared);
        thread::Builder::new()
            .name("glyph_player".into())
            .spawn(move || Player::new(&player_shared).run())
            .expect("Failed to spawn player thread");

        Self { shared }
    }

    fn validate_light(id: i32) -> Result<usize, ExceptionCode> {
        usize::try_from(id)
            .ok()
            .filter(|&index| index < NUM_LEDS)
            .ok_or(ExceptionCode::UNSUPPORTED_OPERATION)
    }

    fn validate_effect(effect: &HwLightEffect) -> ExceptionCode {
        if Self::validate_light(effect.lightId).is_err() {
            return ExceptionCode::UNSUPPORTED_OPERATION;
        }

        if effect.colors.is_empty()
            || effect.frames.is_empty()
            || effect.frames.len() != effect.colors.len()
        {
            return ExceptionCode::ILLEGAL_ARGUMENT;
        }

        for (i, &frames) in effect.frames.iter().enumerate() {
            if i == 0 && frames == 0 {
                continue;
            }
            if frames < 1 {
                return ExceptionCode::ILLEGAL_ARGUMENT;
            }
        }

        if effect.framePeriodMillis < MIN_UPDATE_PERIOD_MS || effect.iterations < 0 {
            return ExceptionCode::ILLEGAL_ARGUMENT;
        }

        ExceptionCode::NONE
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
        self.shared.update(|tracks| tracks[index].set_static(level));
        Ok(())
    }

    fn setLightEffects(&self, effects: &[HwLightEffect]) -> binder::Result<()> {
        for effect in effects {
            let validation_err = Self::validate_effect(effect);
            if validation_err != ExceptionCode::NONE {
                error!("Lights effect for {} is not valid. {:#?}", effect.lightId, validation_err);
                return Err(Status::new_exception(validation_err, None));
            }
        }

        if effects.is_empty() {
            return Ok(());
        }

        let now = self.shared.now();
        self.shared.update(|tracks| {
            for effect in effects {
                tracks[effect.lightId as usize].push(effect, now);
            }
        });
        Ok(())
    }

    fn getLights(&self) -> binder::Result<Vec<HwLight>> {
        info!("Lights reporting supported lights");
        Ok((0..NUM_LEDS as i32)
            .map(|id| HwLight {
                id,
                ordinal: id,
                r#type: LightType::APPLICATION,
                minUpdatePeriodMillis: MIN_UPDATE_PERIOD_MS,
            })
            .collect())
    }
}

fn render(tracks: &[Track], t: f64) -> Frame {
    std::array::from_fn(|index| to_level(tracks[index].level_at(t)))
}

struct Stream {
    generation: u64,
    start: f64,
    next_frame: u64,
}

impl Stream {
    fn frame_time(&self, frame: u64) -> f64 {
        self.start + frame as f64 * FRAME_PERIOD_MS
    }
}

struct Player<'a> {
    shared: &'a Shared,
    glyph: Glyph,
    strips: Option<LedStrips>,
    stream: Option<Stream>,
    written: Option<u64>,
}

impl<'a> Player<'a> {
    fn new(shared: &'a Shared) -> Self {
        let strips = LedStrips::open()
            .inspect_err(|e| warn!("Failed to open LED strips, animating through sysfs: {e}"))
            .ok();
        Self { shared, glyph: Glyph::new(), strips, stream: None, written: None }
    }

    fn run(mut self) -> ! {
        let mut timeout = None;
        let mut state = self.shared.state.lock().unwrap();
        loop {
            state = match timeout {
                None => self.shared.cond.wait_while(state, |s| !s.dirty).unwrap(),
                Some(timeout) => {
                    self.shared.cond.wait_timeout_while(state, timeout, |s| !s.dirty).unwrap().0
                }
            };
            if state.dirty {
                state = self.coalesce(state);
            }

            let now = self.shared.now();
            for track in state.tracks.iter_mut() {
                track.prune(now);
            }
            let generation = state.generation;

            if state.tracks.iter().any(Track::is_animated) {
                let tracks = state.tracks.clone();
                drop(state);
                self.written = None;
                timeout = Some(self.animate(&tracks, generation, now));
            } else {
                let frame = std::array::from_fn(|index| state.tracks[index].static_level());
                drop(state);
                self.show_static(&frame, generation);
                timeout = None;
            }

            state = self.shared.state.lock().unwrap();
        }
    }

    fn coalesce<'b>(&self, mut state: MutexGuard<'b, State>) -> MutexGuard<'b, State> {
        let burst_start = Instant::now();
        loop {
            state.dirty = false;
            let (relocked, result) = self.shared.cond.wait_timeout(state, COALESCE_QUIET).unwrap();
            state = relocked;
            if (result.timed_out() && !state.dirty) || burst_start.elapsed() >= COALESCE_MAX {
                state.dirty = false;
                return state;
            }
        }
    }

    fn show_static(&mut self, frame: &Frame, generation: u64) {
        self.stop_stream();
        if self.written == Some(generation) {
            return;
        }
        match self.glyph.write_frame(frame, true) {
            Ok(()) => self.written = Some(generation),
            Err(e) => error!("Failed to write Glyph frame: {e}"),
        }
    }

    fn animate(&mut self, tracks: &[Track], generation: u64, now: f64) -> Duration {
        if self.strips.is_none() {
            if let Err(e) = self.glyph.write_frame(&render(tracks, now), false) {
                error!("Failed to write Glyph frame: {e}");
            }
            return Duration::from_secs_f64(FRAME_PERIOD_MS / 1000.0);
        }

        let restart = match &self.stream {
            Some(stream) if stream.generation == generation => {
                self.strips.as_mut().unwrap().wait_end(Duration::ZERO)
            }
            _ => true,
        };
        if restart {
            if let Err(e) = self.start_stream(tracks, generation, now) {
                error!("Failed to start LED strips stream: {e}");
                self.strips = None;
                return Duration::ZERO;
            }
        } else {
            self.fill(tracks);
        }
        REFILL_INTERVAL
    }

    fn start_stream(&mut self, tracks: &[Track], generation: u64, now: f64) -> std::io::Result<()> {
        self.stop_stream();
        self.glyph.wake()?;
        self.strips.as_mut().unwrap().reset();
        self.stream = Some(Stream { generation, start: now, next_frame: 0 });
        self.fill(tracks);
        self.strips.as_mut().unwrap().start()
    }

    fn fill(&mut self, tracks: &[Track]) {
        let (Some(strips), Some(stream)) = (self.strips.as_mut(), self.stream.as_mut()) else {
            return;
        };
        while strips.queued() < LOOKAHEAD_BUFFERS && strips.has_free_buffer() {
            let mut frames = Vec::with_capacity(FRAMES_PER_BUFFER);
            while frames.len() < FRAMES_PER_BUFFER {
                frames.push(render(tracks, stream.frame_time(stream.next_frame)));
                stream.next_frame += 1;
            }
            strips.push(&frames);
        }
    }

    fn stop_stream(&mut self) {
        if self.stream.take().is_none() {
            return;
        }
        if let Some(strips) = self.strips.as_mut() {
            if let Err(e) = strips.stop() {
                error!("Failed to stop LED strips stream: {e}");
            }
        }
    }
}
