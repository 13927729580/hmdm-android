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

package com.hmdm.launcher.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedList;
import java.util.List;

@JsonIgnoreProperties( ignoreUnknown = true )
public class ServerConfig {

    private String backgroundColor;
    private String textColor;
    private String backgroundImageUrl;
    private String password;
    private String phone;
    private String imei;
    private Integer iconSize;
    private String title;

    private Boolean gps;
    private Boolean bluetooth;
    private Boolean wifi;
    private Boolean mobileData;

    private Boolean kioskMode;
    private String mainApp;

    private Boolean lockStatusBar;
    private Integer systemUpdateType;
    private String systemUpdateFrom;
    private String systemUpdateTo;

    private Boolean factoryReset;
    private Boolean reboot;
    private Boolean lock;
    private String lockMessage;
    private String passwordReset;

    private String pushOptions;
    private String requestUpdates;

    private Boolean usbStorage;
    private Boolean autoBrightness;
    private Integer brightness;
    private Boolean manageTimeout;
    private Integer timeout;
    private Boolean lockVolume;
    private String passwordMode;

    private Integer orientation;
    private Boolean kioskHome;
    private Boolean kioskRecents;
    private Boolean kioskNotifications;
    private Boolean kioskSystemInfo;
    private Boolean kioskKeyguard;

    private List<Application> applications = new LinkedList();

    private List<ApplicationSetting> applicationSettings = new LinkedList();

    private List<RemoteFile> files = new LinkedList();

    public static final String TITLE_NONE = "none";
    public static final String TITLE_DEVICE_ID = "deviceId";
    public static final int DEFAULT_ICON_SIZE = 100;

    public static final int SYSTEM_UPDATE_DEFAULT = 0;
    public static final int SYSTEM_UPDATE_INSTANT = 1;
    public static final int SYSTEM_UPDATE_SCHEDULE = 2;
    public static final int SYSTEM_UPDATE_MANUAL = 3;

    public static final String PUSH_OPTIONS_MQTT_WORKER = "mqttWorker";
    public static final String PUSH_OPTIONS_MQTT_ALARM = "mqttAlarm";
    public static final String PUSH_OPTIONS_POLLING = "polling";

    public ServerConfig() {}

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor( String backgroundColor ) {
        this.backgroundColor = backgroundColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public void setTextColor( String textColor ) {
        this.textColor = textColor;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }

    public void setBackgroundImageUrl( String backgroundImageUrl ) {
        this.backgroundImageUrl = backgroundImageUrl;
    }

    public List< Application > getApplications() {
        return applications;
    }

    public void setApplications( List< Application > applications ) {
        this.applications = applications;
    }

    public List< ApplicationSetting > getApplicationSettings() {
        return applicationSettings;
    }

    public void setApplicationSettings( List< ApplicationSetting > applicationSettings ) {
        this.applicationSettings = applicationSettings;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword( String password ) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public Integer getIconSize() {
        return iconSize;
    }

    public void setIconSize(Integer iconSize) {
        this.iconSize = iconSize;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getGps() {
        return gps;
    }

    public void setGps(Boolean gps) {
        this.gps = gps;
    }

    public Boolean getBluetooth() {
        return bluetooth;
    }

    public void setBluetooth(Boolean bluetooth) {
        this.bluetooth = bluetooth;
    }

    public Boolean getWifi() {
        return wifi;
    }

    public void setWifi(Boolean wifi) {
        this.wifi = wifi;
    }

    public Boolean getMobileData() {
        return mobileData;
    }

    public void setMobileData(Boolean mobileData) {
        this.mobileData = mobileData;
    }

    public Boolean getKioskMode() {
        return kioskMode;
    }

    public void setKioskMode(Boolean kioskMode) {
        this.kioskMode = kioskMode;
    }

    public String getMainApp() {
        return mainApp;
    }

    public void setMainApp(String mainApp) {
        this.mainApp = mainApp;
    }

    public Boolean getLockStatusBar() {
        return lockStatusBar;
    }

    public void setLockStatusBar(Boolean lockStatusBar) {
        this.lockStatusBar = lockStatusBar;
    }

    public Integer getSystemUpdateType() {
        return systemUpdateType;
    }

    public void setSystemUpdateType(Integer systemUpdateType) {
        this.systemUpdateType = systemUpdateType;
    }

    public String getSystemUpdateFrom() {
        return systemUpdateFrom;
    }

    public void setSystemUpdateFrom(String systemUpdateFrom) {
        this.systemUpdateFrom = systemUpdateFrom;
    }

    public String getSystemUpdateTo() {
        return systemUpdateTo;
    }

    public void setSystemUpdateTo(String systemUpdateTo) {
        this.systemUpdateTo = systemUpdateTo;
    }

    public Boolean getFactoryReset() {
        return factoryReset;
    }

    public void setFactoryReset(Boolean factoryReset) {
        this.factoryReset = factoryReset;
    }

    public Boolean getReboot() {
        return reboot;
    }

    public void setReboot(Boolean reboot) {
        this.reboot = reboot;
    }

    public Boolean getLock() {
        return lock;
    }

    public void setLock(Boolean lock) {
        this.lock = lock;
    }

    public String getLockMessage() {
        return lockMessage;
    }

    public void setLockMessage(String lockMessage) {
        this.lockMessage = lockMessage;
    }

    public String getPasswordReset() {
        return passwordReset;
    }

    public void setPasswordReset(String passwordReset) {
        this.passwordReset = passwordReset;
    }

    public String getPushOptions() {
        return pushOptions;
    }

    public void setPushOptions(String pushOptions) {
        this.pushOptions = pushOptions;
    }

    public String getRequestUpdates() {
        return requestUpdates;
    }

    public void setRequestUpdates(String requestUpdates) {
        this.requestUpdates = requestUpdates;
    }

    public Boolean getUsbStorage() {
        return usbStorage;
    }

    public void setUsbStorage(Boolean usbStorage) {
        this.usbStorage = usbStorage;
    }

    public Boolean getAutoBrightness() {
        return autoBrightness;
    }

    public void setAutoBrightness(Boolean autoBrightness) {
        this.autoBrightness = autoBrightness;
    }

    public Integer getBrightness() {
        return brightness;
    }

    public void setBrightness(Integer brightness) {
        this.brightness = brightness;
    }

    public Boolean getManageTimeout() {
        return manageTimeout;
    }

    public void setManageTimeout(Boolean manageTimeout) {
        this.manageTimeout = manageTimeout;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Boolean getLockVolume() {
        return lockVolume;
    }

    public void setLockVolume(Boolean lockVolume) {
        this.lockVolume = lockVolume;
    }

    public String getPasswordMode() {
        return passwordMode;
    }

    public void setPasswordMode(String passwordMode) {
        this.passwordMode = passwordMode;
    }

    public Integer getOrientation() {
        return orientation;
    }

    public void setOrientation(Integer orientation) {
        this.orientation = orientation;
    }

    public Boolean getKioskHome() {
        return kioskHome;
    }

    public void setKioskHome(Boolean kioskHome) {
        this.kioskHome = kioskHome;
    }

    public Boolean getKioskRecents() {
        return kioskRecents;
    }

    public void setKioskRecents(Boolean kioskRecents) {
        this.kioskRecents = kioskRecents;
    }

    public Boolean getKioskNotifications() {
        return kioskNotifications;
    }

    public void setKioskNotifications(Boolean kioskNotifications) {
        this.kioskNotifications = kioskNotifications;
    }

    public Boolean getKioskSystemInfo() {
        return kioskSystemInfo;
    }

    public void setKioskSystemInfo(Boolean kioskSystemInfo) {
        this.kioskSystemInfo = kioskSystemInfo;
    }

    public Boolean getKioskKeyguard() {
        return kioskKeyguard;
    }

    public void setKioskKeyguard(Boolean kioskKeyguard) {
        this.kioskKeyguard = kioskKeyguard;
    }

    public List<RemoteFile> getFiles() {
        return files;
    }

    public void setFiles(List<RemoteFile> files) {
        this.files = files;
    }
}
