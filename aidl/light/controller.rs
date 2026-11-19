/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! The glyph controller chip: the nodes its driver offers, and how to write them.

#[cfg(any(phone2, phone3a, phone4a))]
mod aw20036;
#[cfg(phone1)]
mod aw210xx;

#[cfg(any(phone2, phone3a, phone4a))]
pub use aw20036::AW20036;
#[cfg(phone1)]
pub use aw210xx::AW210XX;

use std::fs;
use std::io::{Result, Write};
use std::path::Path;
use std::sync::Mutex;
use std::time::Duration;

/// The nodes a driver offers.
#[derive(Clone, Copy)]
pub enum Node {
    /// Takes one level per segment.
    Frame,

    /// Takes a single level for every segment at once.
    All,

    /// Takes one LED's index and level.
    Single,
}

/// The nodes that power a controller up and down, where its driver would otherwise drop writes or
/// power it off in suspend.
pub struct Power {
    /// Arms level writes, and puts the controller to rest once nothing is lit.
    mode: &'static str,

    /// Keeps the controller on through suspend, for as long as anything is lit.
    retain: &'static str,
}

/// What is lit, and which way the controller was last powered.
struct State {
    strips: bool,
    indicator: bool,
    powered: Option<bool>,
}

impl State {
    const IDLE: State = State { strips: false, indicator: false, powered: None };
}

/// A glyph controller: the names of its nodes, and what they need.
pub struct Controller {
    frame: &'static str,
    all: &'static str,
    single: &'static str,

    /// How the controller is powered, where it needs more than a write.
    power: Option<Power>,

    state: Mutex<State>,

    /// A frame arrives as one call per light. Waiting this long for the rest of it keeps a frame
    /// to a single write, which the driver flushes over i2c.
    pub coalesce_window: Duration,

    /// How often the strips can be redrawn.
    pub min_update_period_millis: i32,
}

impl Controller {
    /// Writes a node in the directory the phone's driver registered, `lit` if it lights anything.
    pub fn write(&self, dir: &str, node: Node, value: &str, lit: bool) -> Result<()> {
        let dir = Path::new(dir);

        // Held throughout, so powering up or down never lands between a level and its write.
        let mut state = self.state.lock().unwrap();

        // Must be armed before the level lands, or the write is dropped.
        if lit && state.powered != Some(true) {
            self.power(dir, true)?;
            state.powered = Some(true);
        }

        let name = match node {
            Node::Frame => self.frame,
            Node::All => self.all,
            Node::Single => self.single,
        };
        let mut file = fs::OpenOptions::new().write(true).open(dir.join(name))?;
        file.write_all(value.as_bytes())?;

        match node {
            Node::Single => state.indicator = lit,
            Node::Frame | Node::All => state.strips = lit,
        }
        if !state.strips && !state.indicator && state.powered != Some(false) {
            self.power(dir, false)?;
            state.powered = Some(false);
        }
        Ok(())
    }

    /// Powers the controller up to light, or down to rest once nothing is lit.
    fn power(&self, dir: &Path, up: bool) -> Result<()> {
        let Some(power) = &self.power else { return Ok(()) };

        let (mode, retain) = if up { ("1", "1") } else { ("2", "0") };
        fs::write(dir.join(power.mode), mode)?;
        fs::write(dir.join(power.retain), retain)
    }
}
