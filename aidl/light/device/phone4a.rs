/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

//! Phone (4a).

use crate::controller::AW20036;

use super::Group::Each;
use super::{CameraCodes, Device, IndicatorLed, Layout, ShellLevel};

pub static DEVICE: Device<ShellLevel> = Device {
    controller: &AW20036,
    dir: "/sys/class/leds/aw20036_led",
    segments: 6,
    max_brightness: ShellLevel { black: 255, white: 255 },
    indicator: Some(IndicatorLed {
        led: 26,
        level: ShellLevel { black: 140, white: 140 },
        video_node: Some("video_mode"),
    }),
    // The six segments of the glyph bar, from the top down.
    layout: Layout {
        camera: &[0, 1, 2, 3, 4, 5],
        glyphs: Each(&[0, 1, 2, 3, 4, 5]),
        volume: Each(&[5, 4, 3, 2, 1, 0]),
        battery: Each(&[5, 4, 3, 2, 1, 0]),
    },
    camera: CameraCodes::DEFAULT,
};
