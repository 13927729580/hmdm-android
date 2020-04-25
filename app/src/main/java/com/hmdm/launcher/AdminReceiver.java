/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
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

package com.hmdm.launcher;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PersistableBundle;

import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.util.PreferenceLogger;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.content.Context.MODE_PRIVATE;

/**
 * Created by Ivan Lozenko on 21.02.2017.
 */

public class AdminReceiver extends DeviceAdminReceiver {

    private boolean DEBUG = false;

    @Override
    public void onEnabled(Context context, Intent intent) {
        // We come here after both successful provisioning and manual activation of the device owner
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences( Const.PREFERENCES, MODE_PRIVATE );
        if (DEBUG) PreferenceLogger.log(preferences, "Administrator enabled");
        preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ON).commit();
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences( Const.PREFERENCES, MODE_PRIVATE );
        if (DEBUG) PreferenceLogger.log(preferences, "Profile provisioning complete");

        if ( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            // This function is never called on Android versions less than 5 (in fact, less than 7)
            return;
        }
        try {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(context.getApplicationContext());
            PersistableBundle bundle = intent.getParcelableExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
            String deviceId = null;
            if (DEBUG) PreferenceLogger.log(preferences, "Bundle != null: " + (bundle != null));
            if (bundle != null) {
                deviceId = bundle.getString(Const.QR_DEVICE_ID_ATTR, null);
                if (deviceId == null) {
                    // Also let's try legacy attribute
                    deviceId = bundle.getString(Const.QR_LEGACY_DEVICE_ID_ATTR, null);
                }
            }
            if (deviceId != null) {
                // Device ID is delivered in the QR code!
                // Added: "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {"com.hmdm.DEVICE_ID": "(device id)"}
                if (DEBUG) PreferenceLogger.log(preferences, "DeviceID: " + deviceId);
                settingsHelper.setDeviceId(deviceId);
            }

            String baseUrl = null;
            String secondaryBaseUrl = null;
            String serverProject = null;
            if (bundle != null) {
                baseUrl = bundle.getString(Const.QR_BASE_URL_ATTR, null);
                secondaryBaseUrl = bundle.getString(Const.QR_SECONDARY_BASE_URL_ATTR, null);
                serverProject = bundle.getString(Const.QR_SERVER_PROJECT_ATTR, null);
                if (baseUrl != null) {
                    if (DEBUG) PreferenceLogger.log(preferences, "BaseURL: " + baseUrl);
                    settingsHelper.setBaseUrl(baseUrl);
                    // If we don't set the secondary base URL, it will point to app.h-mdm.com by default which is wrong
                    if (secondaryBaseUrl == null) {
                        secondaryBaseUrl = baseUrl;
                    }
                }
                if (secondaryBaseUrl != null) {
                    if (DEBUG) PreferenceLogger.log(preferences, "SecondaryBaseURL: " + secondaryBaseUrl);
                    settingsHelper.setSecondaryBaseUrl(secondaryBaseUrl);
                }
                if (serverProject != null) {
                    if (DEBUG) PreferenceLogger.log(preferences, "ServerPath: " + serverProject);
                    settingsHelper.setServerProject(serverProject);
                }
                settingsHelper.setQrProvisioning(true);
            }
        } catch (Exception e) {
            // Ignored
            e.printStackTrace();
            if (DEBUG) PreferenceLogger.printStackTrace(preferences, e);
        }
    }
}
