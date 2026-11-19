/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! Phone (2).

use crate::controller::AW20036;

use super::Group::Each;
use super::{CameraCodes, Device, IndicatorLed, Layout, ShellLevel};

pub static DEVICE: Device<ShellLevel> = Device {
    controller: &AW20036,
    dir: "/sys/class/leds/led_strips",
    segments: 33,
    max_brightness: ShellLevel { black: 254, white: 160 },
    indicator: Some(IndicatorLed {
        led: 13,
        level: ShellLevel { black: 255, white: 140 },
        video_node: None,
    }),
    layout: Layout {
        camera: &[0, 1],
        glyphs: Each(&[
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
            24, 25, 26, 27, 28, 29, 30, 31, 32,
        ]),
        volume: Each(&[3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18]),
        battery: Each(&[24, 25, 26, 27, 28, 29, 30, 31, 32]),
    },
    camera: CameraCodes { countdown_off: 3, ..CameraCodes::DEFAULT },
};
