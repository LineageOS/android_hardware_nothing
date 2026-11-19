/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! The phone this is built for, and how it uses its glyph controller.

mod camera;
mod layout;
mod shell;

// Selected by the nothing_glyph device soong config.
#[cfg_attr(phone1, path = "device/phone1.rs")]
#[cfg_attr(phone2, path = "device/phone2.rs")]
#[cfg_attr(phone3a, path = "device/phone3a.rs")]
#[cfg_attr(phone4a, path = "device/phone4a.rs")]
mod phone;

#[cfg(not(any(phone1, phone2, phone3a, phone4a)))]
compile_error!("Set the nothing_glyph device soong config to a phone this service supports");

pub use camera::CameraCodes;
pub use layout::{Group, Layout, Segments};
pub use phone::DEVICE;
pub use shell::{Shell, ShellLevel};

use std::fs;
use std::io::Result;
use std::path::Path;

use crate::controller::{Controller, Node};

/// The recording indicator, on an LED of its own that no frame reaches.
#[derive(Clone, Copy)]
pub struct IndicatorLed<Level = u32> {
    /// The LED it sits on.
    pub led: usize,

    /// The level it lights at.
    pub level: Level,

    /// The node that switches the driver to the PWM frequency a camera can film while recording,
    /// where it takes one.
    pub video_node: Option<&'static str>,
}

/// A phone: the controller it carries, and how it wires and uses it. Its levels are given for each
/// shell until it is fitted for its own.
pub struct Device<Level = u32> {
    pub controller: &'static Controller,

    /// The directory the driver registered its nodes in.
    dir: &'static str,

    /// Levels one frame carries.
    pub segments: usize,

    /// Highest level the nodes accept.
    pub max_brightness: Level,

    /// The recording indicator, where the phone has one.
    pub indicator: Option<IndicatorLed<Level>>,

    pub layout: Layout,

    /// What the camera app sends.
    pub camera: CameraCodes,
}

impl Device<ShellLevel> {
    /// This phone fitted for its shell.
    pub fn for_shell(&self, shell: Shell) -> Device {
        Device {
            controller: self.controller,
            dir: self.dir,
            segments: self.segments,
            max_brightness: self.max_brightness.for_shell(shell),
            indicator: self.indicator.map(|indicator| IndicatorLed {
                led: indicator.led,
                level: indicator.level.for_shell(shell),
                video_node: indicator.video_node,
            }),
            layout: self.layout,
            camera: self.camera,
        }
    }
}

impl Device {
    /// Draws one frame: every segment's level, in order.
    pub fn write_frame(&self, levels: &[u32]) -> Result<()> {
        let levels = levels.iter().map(|&level| self.clamp(level)).collect::<Vec<_>>();
        let value = levels.iter().map(u32::to_string).collect::<Vec<_>>().join(" ");
        self.controller.write(self.dir, Node::Frame, &value, levels.iter().any(|&level| level > 0))
    }

    /// Draws every segment at the same level, in one write.
    pub fn write_all(&self, level: u32) -> Result<()> {
        let level = self.clamp(level);
        self.controller.write(self.dir, Node::All, &level.to_string(), level > 0)
    }

    /// Lights one LED on its own, outside any frame.
    pub fn write_led(&self, led: usize, level: u32) -> Result<()> {
        self.controller.write(self.dir, Node::Single, &format!("{led} {level}"), level > 0)
    }

    /// Switches the driver to the frequency a camera can film while recording, and back.
    pub fn write_video_mode(&self, on: bool) -> Result<()> {
        let Some(node) = self.indicator.and_then(|indicator| indicator.video_node) else {
            return Ok(());
        };
        fs::write(Path::new(self.dir).join(node), if on { "1" } else { "0" })
    }

    fn clamp(&self, level: u32) -> u32 {
        level.min(self.max_brightness)
    }
}
