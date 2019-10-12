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

package com.hmdm.launcher.util;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.pro.ProUtils;

import java.lang.reflect.Method;
import java.util.List;

public class DeviceInfoProvider {

    private final static String LOG_TAG = "HeadwindMdm";

    public static DeviceInfo getDeviceInfo(Context context ) {
        DeviceInfo deviceInfo = new DeviceInfo();
        List< Integer > permissions = deviceInfo.getPermissions();
        List< Application > applications = deviceInfo.getApplications();

        deviceInfo.setModel(Build.MODEL);

        permissions.add(Utils.checkAdminMode(context) ? 1 : 0);
        permissions.add(Utils.canDrawOverlays(context) ? 1 : 0);
        permissions.add(ProUtils.checkUsageStatistics(context) ? 1 : 0);

        PackageManager packageManager = context.getPackageManager();
        SettingsHelper config = SettingsHelper.getInstance( context );
        if ( config.getConfig() != null ) {
            List<Application> requiredApps = SettingsHelper.getInstance( context ).getConfig().getApplications();
            for ( Application application : requiredApps ) {
                try {
                    PackageInfo packageInfo = packageManager.getPackageInfo( application.getPkg(), 0 );

                    Application installedApp = new Application();
                    installedApp.setName( application.getName() );
                    installedApp.setPkg( packageInfo.packageName );
                    installedApp.setVersion( packageInfo.versionName );

                    applications.add( installedApp );
                } catch ( PackageManager.NameNotFoundException e ) {
                    // Application not installed
                }
            }
        }

        deviceInfo.setDeviceId( SettingsHelper.getInstance( context ).getDeviceId() );

        String phone = DeviceInfoProvider.getPhoneNumber(context);
        if (phone == null || phone.equals("")) {
            phone = config.getConfig().getPhone();
        }
        deviceInfo.setPhone(phone);

        String imei = DeviceInfoProvider.getImei(context);
        if (imei == null || imei.equals("")) {
            imei = config.getConfig().getImei();
        }
        deviceInfo.setImei(imei);

        // Battery
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

        Intent batteryStatus = context.registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL) {
            int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            switch (chargePlug) {
                case BatteryManager.BATTERY_PLUGGED_USB:
                    deviceInfo.setBatteryCharging("usb");
                    break;
                case BatteryManager.BATTERY_PLUGGED_AC:
                    deviceInfo.setBatteryCharging("ac");
                    break;
            }
        } else {
            deviceInfo.setBatteryCharging("");
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        deviceInfo.setBatteryLevel(level * 100 / scale);

        deviceInfo.setAndroidVersion(Build.VERSION.RELEASE);

        return deviceInfo;
    }

    public static String getSerialNumber() {
        String serialNumber = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return Build.getSerial();
            } catch (SecurityException e) {
            }
        }
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            serialNumber = (String) get.invoke(c, "ril.serialnumber");
        } catch (Exception ignored) { /*noop*/ }
        if (serialNumber != null && !serialNumber.equals("")) {
            return serialNumber;
        }
        return Build.SERIAL;
    }

    @SuppressLint( { "MissingPermission" } )
    public static String getPhoneNumber(Context context) {
        try {
            TelephonyManager tMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tMgr == null) {
                return null;
            }
            return tMgr.getLine1Number();
        } catch (Exception e) {
            return null;
        }
    }


    @SuppressLint( { "MissingPermission" } )
    public static String getImei(Context context) {
        try {
            TelephonyManager tMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tMgr == null) {
                return null;
            }
            return tMgr.getDeviceId();
        } catch (Exception e) {
            return null;
        }
    }
}
