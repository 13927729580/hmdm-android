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

package com.hmdm.launcher.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.hmdm.IMdmApi;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.RemoteLogItem;
import com.hmdm.launcher.util.RemoteLogger;

public class PluginApiService extends Service {
    // Data keys
    public static final String KEY_SERVER_HOST = "SERVER_HOST";
    public static final String KEY_SECONDARY_SERVER_HOST = "SECONDARY_SERVER_HOST";
    public static final String KEY_SERVER_PATH = "SERVER_PATH";
    public static final String KEY_DEVICE_ID = "DEVICE_ID";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IMdmApi.Stub mBinder = new IMdmApi.Stub() {

        @Override
        public Bundle queryConfig() {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(PluginApiService.this);
            if ( settingsHelper.getConfig() == null ) {
                // This shouldn't happen!
                return null;
            } else {
                Bundle bundle = new Bundle();
                bundle.putString(KEY_SERVER_HOST, Const.BASE_URL);
                bundle.putString(KEY_SECONDARY_SERVER_HOST, Const.SECONDARY_BASE_URL);
                bundle.putString(KEY_SERVER_PATH, Const.SERVER_PROJECT);
                bundle.putString(KEY_DEVICE_ID, settingsHelper.getDeviceId());
                return bundle;
            }
        }

        @Override
        public void log(long timestamp, int level, String packageId, String message) {
            Log.i(Const.LOG_TAG, "Got a log item from " + packageId);
            RemoteLogItem item = new RemoteLogItem();
            item.setTimestamp(timestamp);
            item.setLogLevel(level);
            item.setPackageId(packageId);
            item.setMessage(message);
            RemoteLogger.postLog(PluginApiService.this, item);
        }

        @Override
        public String queryAppPreference(String packageId, String attr) {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(PluginApiService.this);
            if ( settingsHelper.getConfig() == null ) {
                // This shouldn't happen!
                return null;
            }
            return settingsHelper.getAppPreference(packageId, attr);
        }

        @Override
        public boolean setAppPreference(String packageId, String attr, String value) {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(PluginApiService.this);
            if ( settingsHelper.getConfig() == null ) {
                // This shouldn't happen!
                return false;
            }
            return settingsHelper.setAppPreference(packageId, attr, value);
        }

        @Override
        public void commitAppPreferences(String packageId) {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(PluginApiService.this);
            if ( settingsHelper.getConfig() == null ) {
                // This shouldn't happen!
                return;
            }
            settingsHelper.commitAppPreferences(packageId);
        }
    };
}
