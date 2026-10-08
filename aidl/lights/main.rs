/*
 * Copyright (C) 2023 The Android Open Source Project
 * Copyright (C) The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
*/

use android_hardware_light::aidl::android::hardware::light::ILights::{BnLights, ILights};
use binder::BinderFeatures;

mod effect;
mod glyph;
mod lights;
use lights::LightsService;

const LOG_TAG: &str = "android.hardware.light-service.nothing";

use log::LevelFilter;

fn main() {
    let logger_success = logger::init(
        logger::Config::default().with_tag_on_device(LOG_TAG).with_max_level(LevelFilter::Trace),
    );
    if !logger_success {
        panic!("{LOG_TAG}: Failed to start logger.");
    }

    binder::ProcessState::set_thread_pool_max_thread_count(0);

    let lights_service = LightsService::default();
    let lights_service_binder = BnLights::new_binder(lights_service, BinderFeatures::default());

    let service_name = format!("{}/default", LightsService::get_descriptor());
    binder::add_service(&service_name, lights_service_binder.as_binder())
        .expect("Failed to register service");

    binder::ProcessState::join_thread_pool()
}
