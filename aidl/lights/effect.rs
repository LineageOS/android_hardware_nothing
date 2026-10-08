/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

use std::collections::VecDeque;

use android_hardware_light::aidl::android::hardware::light::{
    HwLightEffect::HwLightEffect, InterpolationType::InterpolationType,
};

use crate::glyph::color_to_level;

#[derive(Clone)]
struct Segment {
    effect: HwLightEffect,
    start: f64,
    end: Option<f64>,
    from: f32,
}

impl Segment {
    fn period(&self) -> f64 {
        period(&self.effect)
    }

    fn last_level(&self) -> f32 {
        self.effect.colors.last().map_or(self.from, |&color| color_to_level(color).into())
    }

    fn level_at(&self, t: f64) -> f32 {
        let period = self.period();
        if period <= 0.0 {
            return self.last_level();
        }
        let elapsed = (t - self.start).max(0.0);
        let iteration = (elapsed / period).floor();
        let from = if iteration == 0.0 { self.from } else { self.last_level() };
        level_in_iteration(&self.effect, from, elapsed - iteration * period)
    }
}

fn period(effect: &HwLightEffect) -> f64 {
    effect.frames.iter().map(|&frames| f64::from(frames)).sum::<f64>()
        * f64::from(effect.framePeriodMillis)
}

fn level_in_iteration(effect: &HwLightEffect, from: f32, t: f64) -> f32 {
    let frame_ms = f64::from(effect.framePeriodMillis);
    let (mut prev_t, mut prev_level) = (0.0, from);
    for (&frames, &color) in effect.frames.iter().zip(&effect.colors) {
        let target_t = prev_t + f64::from(frames) * frame_ms;
        let level = f32::from(color_to_level(color));
        if t < target_t {
            return match effect.interpolationType {
                InterpolationType::LINEAR => {
                    prev_level + (level - prev_level) * ((t - prev_t) / (target_t - prev_t)) as f32
                }
                _ => prev_level,
            };
        }
        (prev_t, prev_level) = (target_t, level);
    }
    prev_level
}

#[derive(Clone, Default)]
pub struct Track {
    base: f32,
    segments: VecDeque<Segment>,
}

impl Track {
    pub fn set_static(&mut self, level: u8) {
        self.segments.clear();
        self.base = level.into();
    }

    pub fn push(&mut self, effect: &HwLightEffect, now: f64) {
        let (start, from) = (now, self.level_at(now));
        self.segments.clear();

        let period = period(effect);
        let end = if effect.iterations > 0 {
            Some(start + period * f64::from(effect.iterations))
        } else if period <= 0.0 {
            Some(start)
        } else {
            None
        };

        self.segments.push_back(Segment { effect: effect.clone(), start, end, from });
    }

    pub fn prune(&mut self, now: f64) {
        while let Some(segment) = self.segments.front() {
            match segment.end {
                Some(end) if end <= now => {
                    self.base = segment.last_level();
                    self.segments.pop_front();
                }
                _ => break,
            }
        }
    }

    pub fn is_animated(&self) -> bool {
        !self.segments.is_empty()
    }

    pub fn level_at(&self, t: f64) -> f32 {
        let mut level = self.base;
        for segment in &self.segments {
            if t < segment.start {
                break;
            }
            level = match segment.end {
                Some(end) if t >= end => segment.last_level(),
                _ => segment.level_at(t),
            };
        }
        level
    }

    pub fn static_level(&self) -> u8 {
        to_level(self.base)
    }
}

pub fn to_level(level: f32) -> u8 {
    level.round().clamp(0.0, f32::from(u8::MAX)) as u8
}
