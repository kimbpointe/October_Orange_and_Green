/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.hugsapp;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private static final HashMap<String, String> attributes = new HashMap();
    public static String HEART_RATE_MEASUREMENT = "19B10000-E8F2-537E-4F6C-D104768A1214";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "19B10001-E8F2-537E-4F6C-D104768A1214";

    static {
        // Sample Services.
        attributes.put("19b10000-e8f2-537e-4f6c-d104768a1214", "Heart Rate Service");
        attributes.put("19b10001-e8f2-537e-4F6c-d104768a1214", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
//        attributes.put("19B10001-E8F2-537E-4F6C-D104768A1214", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
