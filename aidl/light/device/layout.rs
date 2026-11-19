/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! Which segments sit behind each light.

/// The segments behind one light.
pub type Segments = &'static [usize];

/// Segments behind each light, per group.
#[derive(Clone, Copy)]
pub struct Layout {
    /// The glyph around the camera, which the countdown draws on.
    pub camera: Segments,
    pub glyphs: Group,
    pub volume: Group,
    pub battery: Group,
}

/// The lights of one group, and the segments each covers.
#[derive(Clone, Copy)]
pub enum Group {
    /// One light per segment, in the order listed.
    Each(Segments),

    /// Lights that may each cover several segments. Not every phone has any.
    #[cfg_attr(any(phone2, phone3a, phone4a), allow(dead_code))]
    Lights(&'static [Segments]),
}

impl Group {
    /// How many lights the group has.
    pub fn len(&self) -> usize {
        match self {
            Group::Each(segments) => segments.len(),
            Group::Lights(lights) => lights.len(),
        }
    }

    /// The segments behind a light.
    pub fn get(&self, index: usize) -> Option<Segments> {
        match *self {
            Group::Each(segments) => segments.get(index..=index),
            Group::Lights(lights) => lights.get(index).copied(),
        }
    }
}
