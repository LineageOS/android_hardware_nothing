/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

use std::fs::{File, OpenOptions};
use std::io::{self, Read};
use std::os::fd::AsRawFd;
use std::os::unix::fs::OpenOptionsExt;
use std::ptr::{self, NonNull};
use std::sync::atomic::{AtomicU8, Ordering};
use std::time::Duration;

use crate::glyph::{Frame, NUM_LEDS};

pub const FRAME_PERIOD_MS: f64 = 1000.0 / 60.0;
pub const FRAMES_PER_BUFFER: usize = BUFFER_DATA_LEN / NUM_LEDS;

const DEVICE: &str = "/dev/led_strips";

const NUM_BUFFERS: usize = 8;
const BUFFER_DATA_LEN: usize = 500;
const MAP_PAGES: usize = 2;
const DATA_VALID: u8 = 0x55;
const DATA_INVALID: u8 = 0xFF;
const MAX_DATA: u32 = 4095;

const fn iow(nr: u32) -> u32 {
    (1 << 30) | ((std::mem::size_of::<libc::c_ulong>() as u32) << 16) | ((b'x' as u32) << 8) | nr
}
const LED_STRIPS_STREAM_MODE: u32 = iow(0x50);
const LED_STRIPS_STOP_MODE: u32 = iow(0x51);

#[allow(dead_code)]
#[repr(C, packed(4))]
struct Buffer {
    status: u8,
    bit: u8,
    length: u16,
    kernel_next: u64,
    user_next: u64,
    brightness: u8,
    data: [u16; BUFFER_DATA_LEN],
}
const _: () = assert!(std::mem::size_of::<Buffer>() == 1024);

pub struct LedStrips {
    file: File,
    map: NonNull<Buffer>,
    map_len: usize,
    next: usize,
}

impl LedStrips {
    pub fn open() -> io::Result<Self> {
        let file =
            OpenOptions::new().read(true).write(true).custom_flags(libc::O_CLOEXEC).open(DEVICE)?;

        // SAFETY: sysconf() has no preconditions.
        let page_size = unsafe { libc::sysconf(libc::_SC_PAGESIZE) } as usize;
        let map_len = MAP_PAGES * page_size;
        assert!(NUM_BUFFERS * std::mem::size_of::<Buffer>() <= map_len);

        // SAFETY: The result is checked before use.
        let addr = unsafe {
            libc::mmap(
                ptr::null_mut(),
                map_len,
                libc::PROT_READ | libc::PROT_WRITE,
                libc::MAP_SHARED,
                file.as_raw_fd(),
                0,
            )
        };
        if addr == libc::MAP_FAILED {
            return Err(io::Error::last_os_error());
        }

        Ok(Self { file, map: NonNull::new(addr.cast()).unwrap(), map_len, next: 0 })
    }

    fn buffer(&self, index: usize) -> *mut Buffer {
        // SAFETY: All buffers fit in the mapping.
        unsafe { self.map.as_ptr().add(index % NUM_BUFFERS) }
    }

    fn status(&self, index: usize) -> &AtomicU8 {
        // SAFETY: The status is shared with the driver, so it is only accessed atomically.
        unsafe { AtomicU8::from_ptr(ptr::addr_of_mut!((*self.buffer(index)).status)) }
    }

    pub fn reset(&mut self) {
        for index in 0..NUM_BUFFERS {
            self.status(index).store(DATA_INVALID, Ordering::Relaxed);
        }
        self.next = 0;
        self.wait_end(Duration::ZERO);
    }

    pub fn queued(&self) -> usize {
        (0..NUM_BUFFERS)
            .filter(|&index| self.status(index).load(Ordering::Acquire) == DATA_VALID)
            .count()
    }

    pub fn has_free_buffer(&self) -> bool {
        self.status(self.next).load(Ordering::Acquire) != DATA_VALID
    }

    pub fn push(&mut self, frames: &[Frame]) {
        assert!(frames.len() <= FRAMES_PER_BUFFER);
        assert!(self.has_free_buffer());

        let mut data = [0u16; BUFFER_DATA_LEN];
        for (chunk, frame) in data.chunks_exact_mut(NUM_LEDS).zip(frames) {
            for (value, &level) in chunk.iter_mut().zip(frame) {
                *value = level_to_data(level);
            }
        }

        let buffer = self.buffer(self.next);
        // SAFETY: The driver doesn't access buffers that aren't valid.
        unsafe {
            ptr::addr_of_mut!((*buffer).data).write_unaligned(data);
            ptr::addr_of_mut!((*buffer).length).write_unaligned((frames.len() * NUM_LEDS) as u16);
            ptr::addr_of_mut!((*buffer).brightness).write_unaligned(u8::MAX);
        }
        self.status(self.next).store(DATA_VALID, Ordering::Release);
        self.next = (self.next + 1) % NUM_BUFFERS;
    }

    pub fn start(&mut self) -> io::Result<()> {
        let channels = NUM_LEDS as u8;
        self.ioctl(LED_STRIPS_STREAM_MODE, ptr::addr_of!(channels).cast())
    }

    pub fn stop(&mut self) -> io::Result<()> {
        self.ioctl(LED_STRIPS_STOP_MODE, ptr::null())?;
        self.wait_end(Duration::from_millis(100));
        Ok(())
    }

    pub fn wait_end(&mut self, timeout: Duration) -> bool {
        let mut pollfd =
            libc::pollfd { fd: self.file.as_raw_fd(), events: libc::POLLIN, revents: 0 };
        let timeout_ms = timeout.as_millis().try_into().unwrap_or(libc::c_int::MAX);
        // SAFETY: pollfd is a valid array of one element.
        let ret = unsafe { libc::poll(&mut pollfd, 1, timeout_ms) };
        if ret <= 0 || pollfd.revents & libc::POLLIN == 0 {
            return false;
        }
        let mut event = [0u8; 1];
        let _ = self.file.read(&mut event);
        true
    }

    fn ioctl(&self, request: u32, arg: *const libc::c_void) -> io::Result<()> {
        // SAFETY: The driver reads at most one byte from arg.
        let ret = unsafe { libc::ioctl(self.file.as_raw_fd(), request as _, arg) };
        if ret < 0 {
            return Err(io::Error::last_os_error());
        }
        Ok(())
    }
}

impl Drop for LedStrips {
    fn drop(&mut self) {
        // SAFETY: The mapping isn't referenced anymore.
        unsafe { libc::munmap(self.map.as_ptr().cast(), self.map_len) };
    }
}

fn level_to_data(level: u8) -> u16 {
    ((u32::from(level) * MAX_DATA).div_ceil(u32::from(u8::MAX))) as u16
}
