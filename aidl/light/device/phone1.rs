/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! Phone (1).

use crate::controller::AW210XX;

use super::Group::{Each, Lights};
use super::{CameraCodes, Device, IndicatorLed, Layout, ShellLevel};

pub static DEVICE: Device<ShellLevel> = Device {
    controller: &AW210XX,
    dir: "/sys/class/leds/aw210xx_led",
    segments: 15,
    max_brightness: ShellLevel { black: 3840, white: 1306 },
    indicator: Some(IndicatorLed {
        led: 17,
        level: ShellLevel { black: 3840, white: 1306 },
        video_node: None,
    }),
    layout: Layout {
        camera: &[0],
        glyphs: Lights(&[&[0], &[1], &[2, 3, 4, 5], &[7, 8, 9, 10, 11, 12, 13, 14], &[6]]),
        volume: Each(&[14, 13, 12, 11, 10, 9, 8, 7]),
        // The driver reads nine levels and lights eight; the ninth is unused.
        battery: Lights(&[&[6, 7], &[8], &[9], &[10], &[11], &[12], &[13], &[14], &[]]),
    },
    camera: CameraCodes::DEFAULT,
};
