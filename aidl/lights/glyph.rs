/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

use std::fs::{self, OpenOptions};
use std::io::{self, Write};

pub const NUM_LEDS: usize = 36;

pub type Frame = [u8; NUM_LEDS];

const LED_DIR: &str = "/sys/class/leds/aw20036_led";

pub fn color_to_level(color: i32) -> u8 {
    let [_, r, g, b] = color.to_be_bytes();
    r.max(g).max(b)
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Mode {
    Shutdown = 0,
    Active = 1,
    Standby = 2,
}

pub struct Glyph {
    mode: Option<Mode>,
}

impl Glyph {
    pub fn new() -> Self {
        Self { mode: read_mode() }
    }

    pub fn write_frame(&mut self, frame: &Frame, allow_standby: bool) -> io::Result<()> {
        let on = frame.iter().any(|&level| level != 0);
        if on || self.mode != Some(Mode::Standby) {
            self.set_mode(Mode::Active)?;
            let values = frame.iter().map(|level| level.to_string()).collect::<Vec<_>>();
            write_node("frame_brightness", &values.join(" "))?;
        }
        if !on && allow_standby {
            self.set_mode(Mode::Standby)?;
        }
        Ok(())
    }

    fn set_mode(&mut self, mode: Mode) -> io::Result<()> {
        if self.mode == Some(mode) {
            return Ok(());
        }
        write_node("operating_mode", &(mode as u8).to_string())?;
        self.mode = Some(mode);
        Ok(())
    }
}

fn write_node(name: &str, value: &str) -> io::Result<()> {
    OpenOptions::new().write(true).open(format!("{LED_DIR}/{name}"))?.write_all(value.as_bytes())
}

fn read_mode() -> Option<Mode> {
    match fs::read_to_string(format!("{LED_DIR}/operating_mode")).ok()?.trim() {
        "0" => Some(Mode::Shutdown),
        "1" => Some(Mode::Active),
        "2" => Some(Mode::Standby),
        _ => None,
    }
}
