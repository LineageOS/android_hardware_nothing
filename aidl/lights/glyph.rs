/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

use std::fs::OpenOptions;
use std::io::{self, Write};

pub const NUM_LEDS: usize = 36;

pub type Frame = [u8; NUM_LEDS];

const LED_DIR: &str = "/sys/class/leds/aw20036_led";

pub fn write_frame(frame: &Frame) -> io::Result<()> {
    let values = frame.iter().map(|level| level.to_string()).collect::<Vec<_>>();
    write_node("frame_brightness", &values.join(" "))
}

fn write_node(name: &str, value: &str) -> io::Result<()> {
    OpenOptions::new().write(true).open(format!("{LED_DIR}/{name}"))?.write_all(value.as_bytes())
}
