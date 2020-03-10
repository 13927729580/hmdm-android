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

package com.hmdm.launcher.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ActivityMainBinding;
import com.hmdm.launcher.databinding.DialogAccessibilityServiceBinding;
import com.hmdm.launcher.databinding.DialogAdministratorModeBinding;
import com.hmdm.launcher.databinding.DialogEnterPasswordBinding;
import com.hmdm.launcher.databinding.DialogFileDownloadingFailedBinding;
import com.hmdm.launcher.databinding.DialogHistorySettingsBinding;
import com.hmdm.launcher.databinding.DialogMiuiPermissionsBinding;
import com.hmdm.launcher.databinding.DialogOverlaySettingsBinding;
import com.hmdm.launcher.databinding.DialogPermissionsBinding;
import com.hmdm.launcher.databinding.DialogSystemSettingsBinding;
import com.hmdm.launcher.databinding.DialogUnknownSourcesBinding;
import com.hmdm.launcher.db.DatabaseHelper;
import com.hmdm.launcher.db.RemoteFileTable;
import com.hmdm.launcher.helper.CryptoHelper;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.json.RemoteFile;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.pro.service.CheckForegroundAppAccessibilityService;
import com.hmdm.launcher.pro.service.CheckForegroundApplicationService;
import com.hmdm.launcher.pro.worker.DetailedInfoWorker;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.service.LocationService;
import com.hmdm.launcher.service.PluginApiService;
import com.hmdm.launcher.task.GetRemoteLogConfigTask;
import com.hmdm.launcher.task.GetServerConfigTask;
import com.hmdm.launcher.task.SendDeviceInfoTask;
import com.hmdm.launcher.util.AppInfo;
import com.hmdm.launcher.util.DeviceInfoProvider;
import com.hmdm.launcher.util.InstallUtils;
import com.hmdm.launcher.util.PushNotificationMqttWrapper;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;
import com.hmdm.launcher.worker.PushNotificationWorker;
import com.squareup.picasso.Picasso;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class MainActivity
        extends BaseActivity
        implements View.OnLongClickListener, AppListAdapter.OnAppChooseListener, View.OnClickListener {

    private static final int PERMISSIONS_REQUEST = 1000;

    private ActivityMainBinding binding;
    private SettingsHelper settingsHelper;

    private Dialog fileNotDownloadedDialog;
    private DialogFileDownloadingFailedBinding dialogFileDownloadingFailedBinding;

    private Dialog enterPasswordDialog;
    private DialogEnterPasswordBinding dialogEnterPasswordBinding;

    private Dialog overlaySettingsDialog;
    private DialogOverlaySettingsBinding dialogOverlaySettingsBinding;

    private Dialog historySettingsDialog;
    private DialogHistorySettingsBinding dialogHistorySettingsBinding;

    private Dialog miuiPermissionsDialog;
    private DialogMiuiPermissionsBinding dialogMiuiPermissionsBinding;

    private Dialog unknownSourcesDialog;
    private DialogUnknownSourcesBinding dialogUnknownSourcesBinding;

    private Dialog administratorModeDialog;
    private DialogAdministratorModeBinding dialogAdministratorModeBinding;

    private Dialog accessibilityServiceDialog;
    private DialogAccessibilityServiceBinding dialogAccessibilityServiceBinding;

    private Dialog systemSettingsDialog;
    private DialogSystemSettingsBinding dialogSystemSettingsBinding;

    private Dialog permissionsDialog;
    private DialogPermissionsBinding dialogPermissionsBinding;

    private Handler handler = new Handler();
    private View applicationNotAllowed;

    private SharedPreferences preferences;

    private static boolean configInitialized = false;
    private static boolean configInitializing = false;
    private static boolean interruptResumeFlow = false;
    private static List< Application > applicationsForInstall = new LinkedList();
    private static List< Application > applicationsForRun = new LinkedList();
    private static List< RemoteFile > filesForInstall = new LinkedList();
    private static final int PAUSE_BETWEEN_AUTORUNS_SEC = 5;
    private static final int SEND_DEVICE_INFO_PERIOD_MINS = 15;
    private static final String WORK_TAG_DEVICEINFO = "com.hmdm.launcher.WORK_TAG_DEVICEINFO";
    private boolean sendDeviceInfoScheduled = false;
    // This flag notifies "download error" dialog what we're downloading: application or file
    // We cannot send this flag as the method parameter because dialog calls MainActivity methods
    private boolean downloadingFile = false;

    private int kioskUnlockCounter = 0;

    private boolean configFault = false;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive( Context context, Intent intent ) {
            switch ( intent.getAction() ) {
                case Const.ACTION_UPDATE_CONFIGURATION:
                    RemoteLogger.log(context, Const.LOG_DEBUG, "Update configuration");
                    updateConfig(false);
                    break;
                case Const.ACTION_HIDE_SCREEN:
                    if ( applicationNotAllowed != null &&
                            (!ProUtils.kioskModeRequired(MainActivity.this) || !ProUtils.isKioskAppInstalled(MainActivity.this)) ) {
                        TextView textView = ( TextView ) applicationNotAllowed.findViewById( R.id.message );
                        textView.setText( String.format( getString(R.string.access_to_app_denied),
                                intent.getStringExtra( Const.PACKAGE_NAME ) ) );

                        applicationNotAllowed.setVisibility( View.VISIBLE );
                        handler.postDelayed( new Runnable() {
                            @Override
                            public void run() {
                                applicationNotAllowed.setVisibility( View.GONE );
                            }
                        }, 5000 );
                    }
                    break;

                case Const.ACTION_DISABLE_BLOCK_WINDOW:
                    if ( applicationNotAllowed != null) {
                        applicationNotAllowed.setVisibility(View.GONE);
                    }
                    break;

                case Const.ACTION_EXIT:
                    finish();
                    break;
            }

        }
    };

    private final BroadcastReceiver stateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                checkSystemSettings(settingsHelper.getConfig());
            } catch (Exception e) {
            }
        }
    };

    private ImageView exitView;
    private ImageView infoView;
    private ImageView updateView;

    private View statusBarView;
    private View rightToolbarView;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );

        // Disable crashes to avoid "select a launcher" popup
        // Crashlytics will show an exception anyway!
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace();
                // Restart launcher if there's a launcher restarter
                Intent intent = getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID);
                if (intent != null) {
                    startActivity(intent);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    finishAffinity();
                }
                System.exit(0);
            }
        });

        // Crashlytics is not included in the open-source version
        ProUtils.initCrashlytics(this);

        Utils.lockSafeBoot(this);

        DetailedInfoWorker.schedule(MainActivity.this);
        if (BuildConfig.ENABLE_PUSH) {
            PushNotificationWorker.schedule(MainActivity.this);
        }

        // Prevent showing the lock screen during the app download/installation
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = DataBindingUtil.setContentView( this, R.layout.activity_main );
        binding.setMessage( getString( R.string.main_start_preparations ) );
        binding.setLoading( true );

        // Try to start services in onCreate(), this may fail, we will try again on each onResume.
        startServicesWithRetry();

        settingsHelper = SettingsHelper.getInstance( this );
        preferences = getSharedPreferences( Const.PREFERENCES, MODE_PRIVATE );
        initReceiver();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(stateChangeReceiver, intentFilter);
    }

    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        int orientation = getResources().getConfiguration().orientation;
        outState.putInt( Const.ORIENTATION, orientation );

        super.onSaveInstanceState( outState );
    }

    private void initReceiver() {
        IntentFilter intentFilter = new IntentFilter( Const.ACTION_UPDATE_CONFIGURATION );
        intentFilter.addAction( Const.ACTION_HIDE_SCREEN );
        intentFilter.addAction( Const.ACTION_EXIT );
        LocalBroadcastManager.getInstance( this ).registerReceiver( receiver, intentFilter );

        // Here we handle the completion of the silent app installation in the device owner mode
        // For some reason, it doesn't work in a common broadcast receiver
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Const.ACTION_INSTALL_COMPLETE)) {
                    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                        confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(confirmationIntent);
                        } catch (Exception e) {
                        }
                    } else if (Utils.isDeviceOwner(MainActivity.this)){
                        // Always grant all dangerous rights to the app
                        // TODO: in the future, the rights must be configurable on the server
                        String packageName = intent.getStringExtra(Const.PACKAGE_NAME);
                        if (packageName != null) {
                            Log.i(Const.LOG_TAG, "Install complete: " + packageName);
                            Utils.autoGrantRequestedPermissions(MainActivity.this, packageName);
                        }
                    }
                    checkAndStartLauncher();
                }
            }
        }, new IntentFilter(Const.ACTION_INSTALL_COMPLETE));

    }

    @Override
    protected void onResume() {
        super.onResume();

        startServicesWithRetry();

        if (interruptResumeFlow) {
            interruptResumeFlow = false;
            return;
        }

        checkAndStartLauncher();
    }

    // Workaround against crash "App is in background" on Android 9: this is an Android OS bug
    // https://stackoverflow.com/questions/52013545/android-9-0-not-allowed-to-start-service-app-is-in-background-after-onresume
    private void startServicesWithRetry() {
        try {
            startServices();
        } catch (Exception e) {
            // Android OS bug!!!
            e.printStackTrace();

            // Repeat an attempt to start services after one second
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    try {
                        startServices();
                    } catch (Exception e) {
                        // Still failed, now give up!
                        // startService may fail after resuming, but the service may be already running (there's a WorkManager)
                        // So if we get an exception here, just ignore it and hope the app will work further
                        e.printStackTrace();
                    }
                }
            }, 1000);
        }
    }

    private void startServices() {
        // Foreground apps checks are not available in a free version: services are the stubs
        startService(new Intent(MainActivity.this, CheckForegroundApplicationService.class));
        startService(new Intent(MainActivity.this, CheckForegroundAppAccessibilityService.class));

        // Moved to onResume!
        // https://stackoverflow.com/questions/51863600/java-lang-illegalstateexception-not-allowed-to-start-service-intent-from-activ
        startService(new Intent(MainActivity.this, PluginApiService.class));

        // Send pending logs to server
        RemoteLogger.sendLogsToServer(MainActivity.this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (Utils.isDeviceOwner(this)) {
                // This may be called on Android 10, not sure why; just continue the flow
                Log.i(Const.LOG_TAG, "Called onRequestPermissionsResult: permissions=" + Arrays.toString(permissions) +
                        ", grantResults=" + Arrays.toString(grantResults));
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                return;
            }
            boolean noPermissions = false;
            for (int n = 0; n < permissions.length; n++) {
                if (permissions[n].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                        // The user didn't allow to determine location, this is not critical, just ignore it
                        preferences.edit().putInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_ON).commit();
                    }
                } else {
                    if (grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                        // This is critical, let's user know
                        createAndShowPermissionsDialog();
                    }
                }
            }
        }
    }

    private void checkAndStartLauncher() {

        boolean deviceOwner = Utils.isDeviceOwner(this);
        preferences.edit().putInt(Const.PREFERENCES_DEVICE_OWNER, deviceOwner ?
            Const.PREFERENCES_ON : Const.PREFERENCES_OFF).commit();
        if (deviceOwner) {
            Utils.autoGrantRequestedPermissions(this, getPackageName());
        }

        int miuiPermissionMode = preferences.getInt(Const.PREFERENCES_MIUI_PERMISSIONS, -1);
        if (miuiPermissionMode == -1) {
            if (checkMiuiPermissions()) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_MIUI_PERMISSIONS, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int unknownSourceMode = preferences.getInt(Const.PREFERENCES_UNKNOWN_SOURCES, -1);
        if (!deviceOwner && unknownSourceMode == -1) {
            if (checkUnknownSources()) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_UNKNOWN_SOURCES, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int administratorMode = preferences.getInt( Const.PREFERENCES_ADMINISTRATOR, - 1 );
        if ( administratorMode == -1 ) {
            if (checkAdminMode()) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int overlayMode = preferences.getInt( Const.PREFERENCES_OVERLAY, - 1 );
        if ( overlayMode == -1 ) {
            if ( checkAlarmWindow() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_OVERLAY, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int usageStatisticsMode = preferences.getInt( Const.PREFERENCES_USAGE_STATISTICS, - 1 );
        if ( usageStatisticsMode == -1 ) {
            if ( checkUsageStatistics() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int accessibilityService = preferences.getInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, - 1 );
        if ( accessibilityService == -1 ) {
            if ( checkAccessibilityService() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_ON ).
                        commit();
            } else {
                createAndShowAccessibilityServiceDialog();
                return;
            }
        }

        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig().getLockStatusBar() != null && settingsHelper.getConfig().getLockStatusBar()) {
            // If the admin requested status bar lock (may be required for some early Samsung devices), block the status bar and right bar (App list) expansion
            statusBarView = ProUtils.preventStatusBarExpansion(this);
            rightToolbarView = ProUtils.preventApplicationsList(this);
        }

        createApplicationNotAllowedScreen();
        startLauncher();
    }

    private void createAndShowPermissionsDialog() {
        dismissDialog(permissionsDialog);
        permissionsDialog = new Dialog( this );
        dialogPermissionsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_permissions,
                null,
                false );
        permissionsDialog.setCancelable( false );
        permissionsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        permissionsDialog.setContentView( dialogPermissionsBinding.getRoot() );
        permissionsDialog.show();
    }

    public void permissionsRetryClicked(View view) {
        dismissDialog(permissionsDialog);
        startLauncher();
    }

    public void permissionsExitClicked(View view) {
        dismissDialog(permissionsDialog);
        finish();
    }

    private void createAndShowAccessibilityServiceDialog() {
        dismissDialog(accessibilityServiceDialog);
        accessibilityServiceDialog = new Dialog( this );
        dialogAccessibilityServiceBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_accessibility_service,
                null,
                false );
        accessibilityServiceDialog.setCancelable( false );
        accessibilityServiceDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        accessibilityServiceDialog.setContentView( dialogAccessibilityServiceBinding.getRoot() );
        accessibilityServiceDialog.show();
    }

    public void skipAccessibilityService( View view ) {
        try { accessibilityServiceDialog.dismiss(); }
        catch ( Exception e ) { e.printStackTrace(); }
        accessibilityServiceDialog = null;

        preferences.
                edit().
                putInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF ).
                commit();

        checkAndStartLauncher();
    }

    public void setAccessibilityService( View view ) {
        try { accessibilityServiceDialog.dismiss(); }
        catch ( Exception e ) { e.printStackTrace(); }
        accessibilityServiceDialog = null;

        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivityForResult(intent, 0);
    }

    // Accessibility services are needed in the Pro-version only
    private boolean checkAccessibilityService() {
        return ProUtils.checkAccessibilityService(this);
    }

    private void createLauncherButtons() {
        createExitButton();
        createInfoButton();
        createUpdateButton();
    }

    private void createButtons() {
        ServerConfig config = settingsHelper.getConfig();
        if (ProUtils.kioskModeRequired(this) && !settingsHelper.getConfig().getMainApp().equals(getPackageName())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays( this )) {
                Toast.makeText(this, R.string.kiosk_mode_requires_overlays, Toast.LENGTH_LONG).show();
                config.setKioskMode(false);
                settingsHelper.updateConfig(config);
                createLauncherButtons();
                return;
            }
            View kioskUnlockButton = ProUtils.createKioskUnlockButton(this);
            kioskUnlockButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    kioskUnlockCounter++;
                    if (kioskUnlockCounter >= Const.KIOSK_UNLOCK_CLICK_COUNT ) {
                        // We are in the main app: let's open launcher activity
                        interruptResumeFlow = true;
                        Intent restoreLauncherIntent = new Intent( MainActivity.this, MainActivity.class );
                        restoreLauncherIntent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT );
                        startActivity( restoreLauncherIntent );
                        createAndShowEnterPasswordDialog();
                        kioskUnlockCounter = 0;
                    }
                }
            });
        } else {
            createLauncherButtons();
        }
    }

    private void startLauncher() {
        createButtons();

        if ( applicationsForInstall.size() > 0 ) {
            loadAndInstallApplications();
        } else if ( !checkPermissions(true)) {
            // Permissions are requested inside checkPermissions, so do nothing here
            Log.i(Const.LOG_TAG, "startLauncher: requesting permissions");
        } else if (!Utils.isDeviceOwner(this) && !settingsHelper.isBaseUrlSet() &&
                (BuildConfig.FLAVOR.equals("master") || BuildConfig.FLAVOR.equals("opensource"))) {
            // For common public version, here's an option to change the server in non-MDM mode
            createAndShowServerDialog(false, settingsHelper.getBaseUrl(), settingsHelper.getServerProject());
        } else if ( settingsHelper.getDeviceId().length() == 0 ) {
            createAndShowEnterDeviceIdDialog( false, null );
        } else if ( ! configInitialized ) {
            Log.i(Const.LOG_TAG, "Updating configuration in startLauncher()");
            if (settingsHelper.getConfig() != null) {
                // If it's not the first start, let's update in the background, show the content first!
                showContent(settingsHelper.getConfig());
            }
            updateConfig( false );
        } else if ( ! configInitializing ) {
            Log.i(Const.LOG_TAG, "Showing content");
            showContent( settingsHelper.getConfig() );
        } else {
            Log.i(Const.LOG_TAG, "Do nothing in startLauncher: configInitializing=true");
        }
    }

    private boolean checkAdminMode() {
        if (!Utils.checkAdminMode(this)) {
            createAndShowAdministratorDialog();
            return false;
        }
        return true;
    }

    // Access to usage statistics is required in the Pro-version only
    private boolean checkUsageStatistics() {
        if (!ProUtils.checkUsageStatistics(this)) {
            createAndShowHistorySettingsDialog();
            return false;
        }
        return true;
    }

    private boolean checkAlarmWindow() {
        if (!Utils.canDrawOverlays(this)) {
            createAndShowOverlaySettingsDialog();
            return false;
        } else {
            return true;
        }
    }

    private boolean checkMiuiPermissions() {
        // Permissions to open popup from background first appears in MIUI 11 (Android 9)
        if (Utils.isMiui(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
            createAndShowMiuiPermissionsDialog();
            // It is not known how to check this permission programmatically, so return true
            return true;
        }
        return true;
    }

    private boolean checkUnknownSources() {
        if ( !Utils.canInstallPackages(this) ) {
            createAndShowUnknownSourcesDialog();
            return false;
        } else {
            return true;
        }
    }

    private void createApplicationNotAllowedScreen() {
        if ( applicationNotAllowed != null ) {
            return;
        }

        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));

        WindowManager.LayoutParams localLayoutParams = new WindowManager.LayoutParams();
        localLayoutParams.type = Utils.OverlayWindowType();
        localLayoutParams.gravity = Gravity.RIGHT;
        localLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        localLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        localLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        localLayoutParams.format = PixelFormat.TRANSPARENT;

        applicationNotAllowed = LayoutInflater.from( this ).inflate( R.layout.layout_application_not_allowed, null );
        applicationNotAllowed.findViewById( R.id.layout_application_not_allowed_continue ).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                applicationNotAllowed.setVisibility( View.GONE );
            }
        } );
        applicationNotAllowed.findViewById( R.id.layout_application_not_allowed_admin ).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                applicationNotAllowed.setVisibility( View.GONE );
                createAndShowEnterPasswordDialog();
            }
        } );

        applicationNotAllowed.setVisibility( View.GONE );

        try {
            manager.addView( applicationNotAllowed, localLayoutParams );
        } catch ( Exception e ) { e.printStackTrace(); }
    }

    // This is an overlay button which is not necessary! We need normal buttons in the launcher window.
    /*private ImageView createManageButton(int imageResource, int imageResourceBlack, int offset) {
        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));

        WindowManager.LayoutParams localLayoutParams = new WindowManager.LayoutParams();
        localLayoutParams.type = Utils.OverlayWindowType();
        localLayoutParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        localLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        localLayoutParams.height = getResources().getDimensionPixelOffset( R.dimen.activity_main_exit_button_size );
        localLayoutParams.width = getResources().getDimensionPixelOffset( R.dimen.activity_main_exit_button_size );
        localLayoutParams.format = PixelFormat.TRANSPARENT;
        localLayoutParams.y = offset;

        boolean dark = true;
        try {
            ServerConfig config = settingsHelper.getConfig();
            if (config.getBackgroundColor() != null) {
                int color = Color.parseColor(config.getBackgroundColor());
                dark = !Utils.isLightColor(color);
            }
        } catch (Exception e) {
        }

        ImageView manageButton = new ImageView( this );
        manageButton.setImageResource(dark ? imageResource : imageResourceBlack);

        try {
            manager.addView( manageButton, localLayoutParams );
        } catch ( Exception e ) { e.printStackTrace(); }
        return manageButton;
    } */

    private ImageView createManageButton(int imageResource, int imageResourceBlack, int offset) {
        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

        boolean dark = true;
        try {
            ServerConfig config = settingsHelper.getConfig();
            if (config.getBackgroundColor() != null) {
                int color = Color.parseColor(config.getBackgroundColor());
                dark = !Utils.isLightColor(color);
            }
        } catch (Exception e) {
        }

        int offsetRight = 0;
        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig().getLockStatusBar() != null && settingsHelper.getConfig().getLockStatusBar()) {
            // If we lock the right bar, let's shift buttons to avoid overlapping
            offsetRight = getResources().getDimensionPixelOffset(R.dimen.prevent_applications_list_width);
        }

        RelativeLayout view = new RelativeLayout(this);
        // Offset is multiplied by 2 because the view is centered. Yeah I know its an Induism)
        view.setPadding(0, offset * 2, offsetRight, 0);
        view.setLayoutParams(layoutParams);

        ImageView manageButton = new ImageView( this );
        manageButton.setImageResource(dark ? imageResource : imageResourceBlack);
        view.addView(manageButton);

        try {
            RelativeLayout root = findViewById(R.id.activity_main);
            root.addView(view);
        } catch ( Exception e ) { e.printStackTrace(); }
        return manageButton;
    }

    private void createExitButton() {
        if ( exitView != null ) {
            return;
        }
        exitView = createManageButton(R.drawable.ic_vpn_key_opaque_24dp, R.drawable.ic_vpn_key_black_24dp, 0);
        exitView.setOnLongClickListener(this);
    }

    private void createInfoButton() {
        if ( infoView != null ) {
            return;
        }
        infoView = createManageButton(R.drawable.ic_info_opaque_24dp, R.drawable.ic_info_black_24dp,
                getResources().getDimensionPixelOffset(R.dimen.info_icon_margin));
        infoView.setOnClickListener(this);
    }

    private void createUpdateButton() {
        if ( updateView != null ) {
            return;
        }
        updateView = createManageButton(R.drawable.ic_system_update_opaque_24dp, R.drawable.ic_system_update_black_24dp,
                (int)(2.05f * getResources().getDimensionPixelOffset(R.dimen.info_icon_margin)));
        updateView.setOnClickListener(this);
    }

    private void updateConfig( final boolean force ) {
        if ( configInitializing ) {
            Log.i(Const.LOG_TAG, "updateConfig(): configInitializing=true, exiting");
            return;
        }

        Log.i(Const.LOG_TAG, "updateConfig(): set configInitializing=true");
        configInitializing = true;
        DetailedInfoWorker.requestConfigUpdate(this);

        // Work around a strange bug with stale SettingsHelper instance: re-read its value
        settingsHelper = SettingsHelper.getInstance(this);

        binding.setMessage( getString( R.string.main_activity_update_config ) );
        GetServerConfigTask task = new GetServerConfigTask( this ) {
            @Override
            protected void onPostExecute( Integer result ) {
                super.onPostExecute( result );
                configInitializing = false;
                Log.i(Const.LOG_TAG, "updateConfig(): set configInitializing=false after getting config");

                switch ( result ) {
                    case Const.TASK_SUCCESS:
                        updateRemoteLogConfig();
                        break;
                    case Const.TASK_ERROR:
                        if ( enterDeviceIdDialog != null ) {
                            enterDeviceIdDialogBinding.setError( true );
                            enterDeviceIdDialog.show();
                        } else {
                            createAndShowEnterDeviceIdDialog( true, settingsHelper.getDeviceId() );
                        }
                        break;
                    case Const.TASK_NETWORK_ERROR:
                        RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to update config: network error");
                        if ( settingsHelper.getConfig() != null && !force ) {
                            updateRemoteLogConfig();
                        } else {
                            createAndShowNetworkErrorDialog(settingsHelper.getBaseUrl(), settingsHelper.getServerProject());
                        }
                        break;
                }
            }
        };
        task.execute();
    }

    private void updateRemoteLogConfig() {
        Log.i(Const.LOG_TAG, "updateRemoteLogConfig(): get logging configuration");

        GetRemoteLogConfigTask task = new GetRemoteLogConfigTask( this ) {
            @Override
            protected void onPostExecute( Integer result ) {
                super.onPostExecute( result );
                Log.i(Const.LOG_TAG, "updateRemoteLogConfig(): result=" + result);
                setupPushService();
            }
        };
        task.execute();
    }

    private void setupPushService() {
        String pushOptions = null;
        if (settingsHelper != null && settingsHelper.getConfig() != null) {
            pushOptions = settingsHelper.getConfig().getPushOptions();
        }
        if (BuildConfig.ENABLE_PUSH && pushOptions != null && (pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_WORKER)
                || pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_ALARM))) {
            try {
                URL url = new URL(settingsHelper.getBaseUrl());
                Runnable nextRunnable = new Runnable() {
                    @Override
                    public void run() {
                        checkFactoryReset();
                    }
                };
                PushNotificationMqttWrapper.getInstance().connect(this, url.getHost(), BuildConfig.MQTT_PORT,
                        pushOptions, settingsHelper.getDeviceId(), nextRunnable, nextRunnable);
            } catch (Exception e) {
                e.printStackTrace();
                checkFactoryReset();
            }
        } else {
            checkFactoryReset();
        }
    }

    private void checkFactoryReset() {
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        if (config != null && config.getFactoryReset() != null && config.getFactoryReset()) {
            // We got a factory reset request, let's confirm and erase everything!
            SendDeviceInfoTask confirmTask = new SendDeviceInfoTask(this) {
                @Override
                protected void onPostExecute( Integer result ) {
                    // Do a factory reset if we can
                    if (Utils.checkAdminMode(MainActivity.this)) {
                        Utils.factoryReset(MainActivity.this);
                    }
                }
            };

            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(this, true, true);
            deviceInfo.setFactoryReset(Utils.checkAdminMode(this));
            confirmTask.execute(deviceInfo);

        } else {
            updateLocationService();
        }
    }

    private void updateLocationService() {
        startLocationServiceWithRetry();
        checkAndUpdateFiles();
    }

    private class RemoteFileStatus {
        public RemoteFile remoteFile;
        public boolean installed;
    }

    private void checkAndUpdateFiles() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                ServerConfig config = settingsHelper.getConfig();
                // This may be a long procedure due to checksum calculation so execute it in the background thread
                InstallUtils.generateFilesForInstallList(MainActivity.this, config.getFiles(), filesForInstall);
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                loadAndInstallFiles();
            }
        }.execute();
    }

    private void loadAndInstallFiles() {
        if ( filesForInstall.size() > 0 ) {
            RemoteFile remoteFile = filesForInstall.remove(0);

            new AsyncTask<RemoteFile, Void, RemoteFileStatus>() {

                @Override
                protected RemoteFileStatus doInBackground(RemoteFile... remoteFiles) {
                    final RemoteFile remoteFile = remoteFiles[0];
                    RemoteFileStatus remoteFileStatus = null;

                    if (remoteFile.isRemove()) {
                        RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Removing file: " + remoteFile.getPath());
                        File file = new File(Environment.getExternalStorageDirectory(), remoteFile.getPath());
                        try {
                            file.delete();
                            RemoteFileTable.deleteByPath(DatabaseHelper.instance(MainActivity.this).getWritableDatabase(), remoteFile.getPath());
                        } catch (Exception e) {
                            RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to remove file: " +
                                    remoteFile.getPath() + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                    } else if (remoteFile.getUrl() != null) {
                        updateMessageForFileDownloading(remoteFile.getPath());

                        File file = null;
                        try {
                            RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Downloading file: " + remoteFile.getPath());
                            file = InstallUtils.downloadFile(MainActivity.this, remoteFile.getUrl(),
                                    new InstallUtils.DownloadProgress() {
                                        @Override
                                        public void onDownloadProgress(final int progress, final long total, final long current) {
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    binding.progress.setMax(100);
                                                    binding.progress.setProgress(progress);

                                                    binding.setFileLength(total);
                                                    binding.setDownloadedLength(current);
                                                }
                                            });
                                        }
                                    });
                        } catch (Exception e) {
                            RemoteLogger.log(MainActivity.this, Const.LOG_WARN,
                                    "Failed to download file " + file.getPath() + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        remoteFileStatus = new RemoteFileStatus();
                        remoteFileStatus.remoteFile = remoteFile;
                        if (file != null) {
                            File finalFile = new File(Environment.getExternalStorageDirectory(), remoteFile.getPath());
                            try {
                                if (finalFile.exists()) {
                                    finalFile.delete();
                                }
                                FileUtils.moveFile(file, finalFile);
                                RemoteFileTable.insert(DatabaseHelper.instance(MainActivity.this).getWritableDatabase(), remoteFile);
                                remoteFileStatus.installed = true;
                            } catch (Exception e) {
                                RemoteLogger.log(MainActivity.this, Const.LOG_WARN,
                                        "Failed to create file " + remoteFile.getPath() + ": " + e.getMessage());
                                e.printStackTrace();
                                remoteFileStatus.installed = false;
                            }
                        } else {
                            remoteFileStatus.installed = false;
                        }
                    }

                    return remoteFileStatus;
                }

                @Override
                protected void onPostExecute(RemoteFileStatus fileStatus) {
                    if (fileStatus != null) {
                        if (!fileStatus.installed) {
                            filesForInstall.add( 0, fileStatus.remoteFile );
                            if (!ProUtils.kioskModeRequired(MainActivity.this)) {
                                // Notify the error dialog that we're downloading a file, not an app
                                downloadingFile = true;
                                createAndShowFileNotDownloadedDialog(fileStatus.remoteFile.getUrl());
                                binding.setDownloading( false );
                            } else {
                                // Avoid user interaction in kiosk mode, just ignore download error and keep the old version
                                // Note: view is not used in this method so just pass null there
                                confirmDownloadFailureClicked(null);
                            }
                            return;
                        }
                    }
                    Log.i(Const.LOG_TAG, "loadAndInstallFiles(): proceed to next file");
                    loadAndInstallFiles();
                }

            }.execute(remoteFile);
        } else {
            Log.i(Const.LOG_TAG, "Proceed to application update");
            checkAndUpdateApplications();
        }
    }

    // Workaround against crash "App is in background" on Android 9: this is an Android OS bug
    // https://stackoverflow.com/questions/52013545/android-9-0-not-allowed-to-start-service-app-is-in-background-after-onresume
    private void startLocationServiceWithRetry() {
        try {
            startLocationService();
        } catch (Exception e) {
            // Android OS bug!!!
            e.printStackTrace();

            // Repeat an attempt to start service after one second
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    try {
                        startLocationService();
                    } catch (Exception e) {
                        // Still failed, now give up!
                        e.printStackTrace();
                    }
                }
            }, 1000);
        }
    }

    private void startLocationService() {
        ServerConfig config = settingsHelper.getConfig();
        Intent intent = new Intent(this, LocationService.class);
        intent.setAction(config.getRequestUpdates() != null ? config.getRequestUpdates() : LocationService.ACTION_STOP);
        startService(intent);
    }

    private void checkAndUpdateApplications() {
        Log.i(Const.LOG_TAG, "checkAndUpdateApplications(): starting update applications");
        binding.setMessage( getString( R.string.main_activity_applications_update ) );

        configInitialized = true;
        configInitializing = false;

        ServerConfig config = settingsHelper.getConfig();
        InstallUtils.generateApplicationsForInstallList(this, config.getApplications(), applicationsForInstall);

        Log.i(Const.LOG_TAG, "checkAndUpdateApplications(): list size=" + applicationsForInstall.size());

        loadAndInstallApplications();
    }

    private class ApplicationStatus {
        public Application application;
        public boolean installed;
    }

    // Here we avoid ConcurrentModificationException by executing all operations with applicationForInstall list in a main thread
    private void loadAndInstallApplications() {
        if ( applicationsForInstall.size() > 0 ) {
            Application application = applicationsForInstall.remove(0);

            new AsyncTask<Application, Void, ApplicationStatus>() {

                @Override
                protected ApplicationStatus doInBackground(Application... applications) {
                    final Application application = applications[0];
                    ApplicationStatus applicationStatus = null;

                    if (application.isRemove()) {
                        // Remove the app
                        RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Removing app: " + application.getPkg());
                        updateMessageForApplicationRemoving( application.getName() );
                        uninstallApplication(application.getPkg());

                    } else if ( application.getUrl() != null && !application.getUrl().startsWith("market://details") ) {
                        updateMessageForApplicationDownloading(application.getName());

                        File file = null;
                        try {
                            RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Downloading app: " + application.getPkg());
                            file = InstallUtils.downloadFile(MainActivity.this, application.getUrl(),
                                    new InstallUtils.DownloadProgress() {
                                        @Override
                                        public void onDownloadProgress(final int progress, final long total, final long current) {
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    binding.progress.setMax(100);
                                                    binding.progress.setProgress(progress);

                                                    binding.setFileLength(total);
                                                    binding.setDownloadedLength(current);
                                                }
                                            });
                                        }
                                    });
                        } catch (Exception e) {
                            RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to download app " + application.getPkg() + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        applicationStatus = new ApplicationStatus();
                        applicationStatus.application = application;
                        if (file != null) {
                            updateMessageForApplicationInstalling(application.getName());
                            installApplication(file, application.getPkg());
                            applicationStatus.installed = true;
                        } else {
                            applicationStatus.installed = false;
                        }
                    } else if (application.getUrl().startsWith("market://details")) {
                        RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Installing app " + application.getPkg() + " from Google Play");
                        installApplicationFromPlayMarket(application.getUrl(), application.getPkg());
                        applicationStatus = new ApplicationStatus();
                        applicationStatus.application = application;
                        applicationStatus.installed = true;
                    } else {
                        handler.post( new Runnable() {
                            @Override
                            public void run() {
                                Log.i(Const.LOG_TAG, "loadAndInstallApplications(): proceed to next app");
                                loadAndInstallApplications();
                            }
                        } );
                    }

                    return applicationStatus;
                }

                @Override
                protected void onPostExecute(ApplicationStatus applicationStatus) {
                    if (applicationStatus != null) {
                        if (applicationStatus.installed) {
                            if (applicationStatus.application.isRunAfterInstall()) {
                                applicationsForRun.add(applicationStatus.application);
                            }
                        } else {
                            applicationsForInstall.add( 0, applicationStatus.application );
                            if (!ProUtils.kioskModeRequired(MainActivity.this)) {
                                // Notify the error dialog that we're downloading an app
                                downloadingFile = false;
                                createAndShowFileNotDownloadedDialog(applicationStatus.application.getName());
                                binding.setDownloading( false );
                            } else {
                                // Avoid user interaction in kiosk mode, just ignore download error and keep the old version
                                // Note: view is not used in this method so just pass null there
                                confirmDownloadFailureClicked(null);
                            }
                        }
                    }
                }

            }.execute(application);
        } else {
            RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Configuration updated");
            Log.i(Const.LOG_TAG, "Showing content from loadAndInstallApplications()");
            showContent( settingsHelper.getConfig() );
        }
    }

    private void installApplicationFromPlayMarket(final String uri, final String packageName) {
        RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Asking user to install app " + packageName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(uri));
        startActivity(intent);
    }

    // This function is called from a background thread
    private void installApplication( File file, final String packageName ) {
        if (packageName.equals(getPackageName()) &&
                getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID) != null) {
            // Restart self in EMUI: there's no auto restart after update in EMUI, we must use a helper app
            startLauncherRestarter();
        }
        if (Utils.isDeviceOwner(this)) {
                RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Silently installing app " + packageName);
            InstallUtils.silentInstallApplication(this, file, packageName, new InstallUtils.InstallErrorHandler() {
                    @Override
                    public void onInstallError() {
                        Log.i(Const.LOG_TAG, "installApplication(): error installing app " + packageName);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(MainActivity.this)
                                    .setMessage(getString(R.string.install_error) + " " + packageName)
                                    .setPositiveButton(R.string.dialog_administrator_mode_continue, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            checkAndStartLauncher();
                                        }
                                    })
                                    .create()
                                    .show();
                            }
                        });
                    }
                });
        } else {
            RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Asking user to install app " + packageName);
            InstallUtils.requestInstallApplication(MainActivity.this, file, new InstallUtils.InstallErrorHandler() {
                @Override
                public void onInstallError() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            checkAndStartLauncher();
                        }
                    });
                }
            });
        }
    }

    private void uninstallApplication(final String packageName) {
        if (Utils.isDeviceOwner(this)) {
            RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Silently uninstall app " + packageName);
            InstallUtils.silentUninstallApplication(this, packageName);
        } else {
            RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Asking user to uninstall app " + packageName);
            InstallUtils.requestUninstallApplication(this, packageName);
        }
    }

    private void updateMessageForApplicationInstalling( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_app_installing) + " " + name );
                binding.setDownloading( false );
            }
        } );
    }

    private void updateMessageForFileDownloading( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_file_downloading) + " " + name );
                binding.setDownloading( true );
            }
        } );
    }

    private void updateMessageForApplicationDownloading( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_app_downloading) + " " + name );
                binding.setDownloading( true );
            }
        } );
    }

    private void updateMessageForApplicationRemoving( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_app_removing) + " " + name );
                binding.setDownloading( false );
            }
        } );
    }

    private boolean checkSystemSettings(ServerConfig config) {
        if (config.getSystemUpdateType() != null &&
                config.getSystemUpdateType() != ServerConfig.SYSTEM_UPDATE_DEFAULT &&
                Utils.isDeviceOwner(this)) {
            Utils.setSystemUpdatePolicy(this, config.getSystemUpdateType(), config.getSystemUpdateFrom(), config.getSystemUpdateTo());
        }

        if (config.getBluetooth() != null) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null) {
                boolean enabled = bluetoothAdapter.isEnabled();
                if (config.getBluetooth() && !enabled) {
                    bluetoothAdapter.enable();
                } else if (!config.getBluetooth() && enabled) {
                    bluetoothAdapter.disable();
                }
            }
        }

        if (config.getWifi() != null) {
            WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                boolean enabled = wifiManager.isWifiEnabled();
                if (config.getWifi() && !enabled) {
                    wifiManager.setWifiEnabled(true);
                } else if (!config.getWifi() && enabled) {
                    wifiManager.setWifiEnabled(false);
                }
            }
        }

        // To delay opening the settings activity
        boolean dialogWillShow = false;

        if (config.getGps() != null) {
            LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                boolean enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (config.getGps() && !enabled) {
                    dialogWillShow = true;
                    postDelayedSystemSettingDialog(getString(R.string.message_turn_on_gps), new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));

                } else if (!config.getGps() && enabled) {
                    dialogWillShow = true;
                    postDelayedSystemSettingDialog(getString(R.string.message_turn_off_gps), new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            }
        }

        if (config.getMobileData() != null) {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && !dialogWillShow) {
                try {
                    boolean enabled = Utils.isMobileDataEnabled(this);
                    //final Intent mobileDataSettingsIntent = new Intent();
                    // One more hack: open the data transport activity
                    // https://stackoverflow.com/questions/31700842/which-intent-should-open-data-usage-screen-from-settings
                    //mobileDataSettingsIntent.setComponent(new ComponentName("com.android.settings",
                    //        "com.android.settings.Settings$DataUsageSummaryActivity"));
                    //Intent mobileDataSettingsIntent = new Intent(Intent.ACTION_MAIN);
                    //mobileDataSettingsIntent.setClassName("com.android.phone", "com.android.phone.NetworkSetting");
                    Intent mobileDataSettingsIntent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                    if (config.getMobileData() && !enabled) {
                        postDelayedSystemSettingDialog(getString(R.string.message_turn_on_mobile_data), mobileDataSettingsIntent);
                    } else if (!config.getMobileData() && enabled) {
                        postDelayedSystemSettingDialog(getString(R.string.message_turn_off_mobile_data), mobileDataSettingsIntent);
                    }
                } catch (Exception e) {
                    // Some problem accessible private API
                }
            }
        }

        if (config.getUsbStorage() != null) {
            Utils.lockUsbStorage(config.getUsbStorage(), this);
        }

        // Null value is processed here, it means unlock brightness
        Utils.setBrightnessPolicy(config.getAutoBrightness(), config.getBrightness(), this);

        if (config.getManageTimeout() != null) {
            Utils.setScreenTimeoutPolicy(config.getManageTimeout(), config.getTimeout(), this);
        }

        if (config.getLockVolume() != null) {
            Utils.lockVolume(config.getLockVolume(), this);
        }

        return true;
    }

    private void showContent( ServerConfig config ) {
        if (!checkSystemSettings(config)) {
            // Here we go when the settings window is opened;
            // Next time we're here after we returned from the Android settings through onResume()
            return;
        }

        scheduleDeviceInfoSending();
        scheduleInstalledAppsRun();

        if (ProUtils.kioskModeRequired(this)) {
            String kioskApp = settingsHelper.getConfig().getMainApp();
            // It is not possible to set Headwind MDM as a main app: the logic is then broken
            if (kioskApp != null && kioskApp.trim().length() > 0 && !kioskApp.equals(getPackageName())) {
                if (ProUtils.startCosuKioskMode(kioskApp, this)) {
                    return;
                } else {
                    Log.e(Const.LOG_TAG, "Kiosk mode failed, proceed with the default flow");
                }
            } else {
                Log.e(Const.LOG_TAG, "Kiosk mode disabled: please setup the main app!");
            }
        } else {
            if (ProUtils.isKioskModeRunning(this)) {
                // Turn off kiosk and show desktop if it is turned off in the configuration
                ProUtils.unlockKiosk(this);
                openDefaultLauncher();
            }
        }

        if ( config.getBackgroundColor() != null ) {
            try {
                binding.activityMainContentWrapper.setBackgroundColor(Color.parseColor(config.getBackgroundColor()));
            } catch (Exception e) {
                // Invalid color
                e.printStackTrace();
                binding.activityMainContentWrapper.setBackgroundColor( getResources().getColor(R.color.defaultBackground));
            }
        } else {
            binding.activityMainContentWrapper.setBackgroundColor( getResources().getColor(R.color.defaultBackground));
        }
        updateTitle(config);

        if ( config.getBackgroundImageUrl() != null && config.getBackgroundImageUrl().length() > 0 ) {
            Picasso.with( this ).
                    load( config.getBackgroundImageUrl() ).
                    into( binding.activityMainBackground );
        } else {
            binding.activityMainBackground.setImageDrawable(null);
        }

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize( size );

        int width = size.x;
        int itemWidth = getResources().getDimensionPixelSize( R.dimen.app_list_item_size );

        int spanCount = ( int ) ( width * 1.0f / itemWidth );

        binding.activityMainContent.setLayoutManager( new GridLayoutManager( this, spanCount ) );
        binding.activityMainContent.setAdapter( new AppListAdapter( this, this ) );
        binding.setShowContent(true);
        // We can now sleep, uh
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // Run default launcher (Headwind MDM) as if the user clicked Home button
    private void openDefaultLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    public static class SendDeviceInfoWorker extends Worker {

        private Context context;
        private SettingsHelper settingsHelper;

        public SendDeviceInfoWorker(
                @NonNull final Context context,
                @NonNull WorkerParameters params) {
            super(context, params);
            this.context = context;
            settingsHelper = SettingsHelper.getInstance(context);
        }

        @Override
        // This is running in a background thread by WorkManager
        public Result doWork() {
            if (settingsHelper == null || settingsHelper.getConfig() == null) {
                return Result.failure();
            }

            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true);

            ServerService serverService = ServerServiceKeeper.getServerServiceInstance(context);
            ServerService secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
            Response<ResponseBody> response = null;

            try {
                response = serverService.sendDevice(settingsHelper.getServerProject(), deviceInfo).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (response == null) {
                    response = secondaryServerService.sendDevice(settingsHelper.getServerProject(), deviceInfo).execute();
                }
                if ( response.isSuccessful() ) {
                    return Result.success();
                }
            }
            catch ( Exception e ) { e.printStackTrace(); }

            return Result.failure();
        }
    }

    private void scheduleDeviceInfoSending() {
        if (sendDeviceInfoScheduled) {
            return;
        }
        sendDeviceInfoScheduled = true;
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(SendDeviceInfoWorker.class, SEND_DEVICE_INFO_PERIOD_MINS, TimeUnit.MINUTES)
                        .addTag(Const.WORK_TAG_COMMON)
                        .build();
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(WORK_TAG_DEVICEINFO, ExistingPeriodicWorkPolicy.REPLACE, request);
    }

    private void scheduleInstalledAppsRun() {
        if (applicationsForRun.size() == 0) {
            return;
        }
        Handler handler = new Handler();
        int pause = PAUSE_BETWEEN_AUTORUNS_SEC;
        while (applicationsForRun.size() > 0) {
            final Application application = applicationsForRun.get(0);
            applicationsForRun.remove(0);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(application.getPkg());
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    }
                }
            }, pause * 1000);
            pause += PAUSE_BETWEEN_AUTORUNS_SEC;
        }
    }

    private void updateTitle(ServerConfig config) {
        String titleType = config.getTitle();
        if (titleType != null && titleType.equals(ServerConfig.TITLE_DEVICE_ID)) {
            if (config.getTextColor() != null) {
                try {
                    binding.activityMainTitle.setTextColor(Color.parseColor(settingsHelper.getConfig().getTextColor()));
                } catch (Exception e) {
                    // Invalid color
                    e.printStackTrace();
                }
            }
            binding.activityMainTitle.setVisibility(View.VISIBLE);
            binding.activityMainTitle.setText(SettingsHelper.getInstance(this).getDeviceId());
        } else {
            binding.activityMainTitle.setVisibility(View.GONE);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));
        if ( applicationNotAllowed != null ) {
            try { manager.removeView( applicationNotAllowed ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( statusBarView != null ) {
            try { manager.removeView( statusBarView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( rightToolbarView != null ) {
            try { manager.removeView( rightToolbarView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( exitView != null ) {
            try { manager.removeView( exitView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( infoView != null ) {
            try { manager.removeView( infoView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( updateView != null ) {
            try { manager.removeView( updateView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        LocalBroadcastManager.getInstance( this ).unregisterReceiver( receiver );
        unregisterReceiver(stateChangeReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();

        dismissDialog(fileNotDownloadedDialog);
        dismissDialog(enterDeviceIdDialog);
        dismissDialog(networkErrorDialog);
        dismissDialog(enterPasswordDialog);
        dismissDialog(historySettingsDialog);
        dismissDialog(unknownSourcesDialog);
        dismissDialog(overlaySettingsDialog);
        dismissDialog(administratorModeDialog);
        dismissDialog(deviceInfoDialog);
        dismissDialog(accessibilityServiceDialog);
        dismissDialog(systemSettingsDialog);
        dismissDialog(permissionsDialog);

        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_SHOW_LAUNCHER ) );
    }

    private void createAndShowAdministratorDialog() {
        dismissDialog(administratorModeDialog);
        administratorModeDialog = new Dialog( this );
        dialogAdministratorModeBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_administrator_mode,
                null,
                false );
        administratorModeDialog.setCancelable( false );
        administratorModeDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        administratorModeDialog.setContentView( dialogAdministratorModeBinding.getRoot() );
        administratorModeDialog.show();
    }

    public void skipAdminMode( View view ) {
        dismissDialog(administratorModeDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_OFF ).
                commit();

        checkAndStartLauncher();
    }

    public void setAdminMode( View view ) {
        dismissDialog(administratorModeDialog);

        Intent intent = new Intent( android.provider.Settings.ACTION_SECURITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void createAndShowFileNotDownloadedDialog(String fileName) {
        dismissDialog(fileNotDownloadedDialog);
        fileNotDownloadedDialog = new Dialog( this );
        dialogFileDownloadingFailedBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_file_downloading_failed,
                null,
                false );
        int errorTextResource = this.downloadingFile ? R.string.main_file_downloading_error : R.string.main_app_downloading_error;
        dialogFileDownloadingFailedBinding.title.setText( getString(errorTextResource) + " " + fileName );
        fileNotDownloadedDialog.setCancelable( false );
        fileNotDownloadedDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        fileNotDownloadedDialog.setContentView( dialogFileDownloadingFailedBinding.getRoot() );
        try {
            fileNotDownloadedDialog.show();
        } catch (Exception e) {
            // BadTokenException ignored
        }
    }

    public void repeatDownloadClicked( View view ) {
        dismissDialog(fileNotDownloadedDialog);
        if (downloadingFile) {
            loadAndInstallFiles();
        } else {
            loadAndInstallApplications();
        }
    }

    public void confirmDownloadFailureClicked( View view ) {
        dismissDialog(fileNotDownloadedDialog);

        if (downloadingFile) {
            if (filesForInstall.size() > 0) {
                RemoteFile remoteFile = filesForInstall.remove(0);
                settingsHelper.removeRemoteFile(remoteFile);
            }
            loadAndInstallFiles();
        } else {
            if (applicationsForInstall.size() > 0) {
                Application application = applicationsForInstall.remove(0);
                settingsHelper.removeApplication(application);
            }
            loadAndInstallApplications();
        }
    }

    private void createAndShowHistorySettingsDialog() {
        dismissDialog(historySettingsDialog);
        historySettingsDialog = new Dialog( this );
        dialogHistorySettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_history_settings,
                null,
                false );
        historySettingsDialog.setCancelable( false );
        historySettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        historySettingsDialog.setContentView( dialogHistorySettingsBinding.getRoot() );
        historySettingsDialog.show();
    }

    public void historyWithoutPermission( View view ) {
        dismissDialog(historySettingsDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF ).
                commit();
        checkAndStartLauncher();
    }

    public void continueHistory( View view ) {
        dismissDialog(historySettingsDialog);

        startActivity( new Intent( Settings.ACTION_USAGE_ACCESS_SETTINGS ) );
    }

    private void createAndShowOverlaySettingsDialog() {
        dismissDialog(overlaySettingsDialog);
        overlaySettingsDialog = new Dialog( this );
        dialogOverlaySettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_overlay_settings,
                null,
                false );
        overlaySettingsDialog.setCancelable( false );
        overlaySettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        overlaySettingsDialog.setContentView( dialogOverlaySettingsBinding.getRoot() );
        overlaySettingsDialog.show();
    }

    public void overlayWithoutPermission( View view ) {
        dismissDialog(overlaySettingsDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_OVERLAY, Const.PREFERENCES_OFF ).
                commit();
        checkAndStartLauncher();
    }

    public void continueOverlay( View view ) {
        dismissDialog(overlaySettingsDialog);

        Intent intent = new Intent( Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse( "package:" + getPackageName() ) );
        startActivityForResult( intent, 1001 );
    }


    public void saveDeviceId( View view ) {
        String deviceId = enterDeviceIdDialogBinding.deviceId.getText().toString();
        if ( "".equals( deviceId ) ) {
            return;
        } else {
            settingsHelper.setDeviceId( deviceId );
            enterDeviceIdDialogBinding.setError( false );

            dismissDialog(enterDeviceIdDialog);

            if ( checkPermissions( true ) ) {
                Log.i(Const.LOG_TAG, "saveDeviceId(): calling updateConfig()");
                updateConfig( false );
            }
        }
    }


    public void saveServerUrl( View view ) {
        if (saveServerUrlBase()) {
            checkAndStartLauncher();
        }
    }


    public void networkErrorRepeatClicked( View view ) {
        dismissDialog(networkErrorDialog);

        Log.i(Const.LOG_TAG, "networkErrorRepeatClicked(): calling updateConfig()");
        updateConfig( false );
    }

    public void networkErrorResetClicked( View view ) {
        dismissDialog(networkErrorDialog);

        Log.i(Const.LOG_TAG, "networkErrorResetClicked(): calling updateConfig()");
        settingsHelper.setDeviceId("");
        settingsHelper.setBaseUrl("");
        settingsHelper.setSecondaryBaseUrl("");
        settingsHelper.setServerProject("");
        createAndShowServerDialog(false, settingsHelper.getBaseUrl(), settingsHelper.getServerProject());
    }

    public void networkErrorCancelClicked(View view) {
        dismissDialog(networkErrorDialog);

        if (configFault) {
            Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, quit");
            Toast.makeText(this, R.string.critical_server_failure, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.i(Const.LOG_TAG, "networkErrorCancelClicked()");
        if ( settingsHelper.getConfig() != null ) {
            showContent( settingsHelper.getConfig() );
        } else {
            Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, retrying");
            Toast.makeText(this, R.string.empty_configuration, Toast.LENGTH_LONG).show();
            configFault = true;
            updateConfig( false );
        }
    }

    private boolean checkPermissions( boolean startSettings ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        // If the user didn't grant permissions, let him know and do not request until he confirms he want to retry
        if (permissionsDialog != null && permissionsDialog.isShowing()) {
            return false;
        }

        if (Utils.isDeviceOwner(this)) {
            // Do not request permissions if we're the device owner
            // They are added automatically
            return true;
        }

        if (preferences.getInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

                if (startSettings) {
                    requestPermissions(new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_PHONE_STATE
                    }, PERMISSIONS_REQUEST);
                }
                return false;
            } else {
                return true;
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

                if (startSettings) {
                    requestPermissions(new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE
                    }, PERMISSIONS_REQUEST);
                }
                return false;
            } else {
                return true;
            }
        }
    }

    private void createAndShowEnterPasswordDialog() {
        dismissDialog(enterPasswordDialog);
        enterPasswordDialog = new Dialog( this );
        dialogEnterPasswordBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_enter_password,
                null,
                false );
        enterPasswordDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );
        enterPasswordDialog.setCancelable( false );

        enterPasswordDialog.setContentView( dialogEnterPasswordBinding.getRoot() );
        dialogEnterPasswordBinding.setLoading( false );
        try {
            enterPasswordDialog.show();
        } catch (Exception e) {
            // Sometimes here we get a Fatal Exception: android.view.WindowManager$BadTokenException
            // Unable to add window -- token android.os.BinderProxy@f307de for displayid = 0 is not valid; is your activity running?
            Toast.makeText(getApplicationContext(), R.string.internal_error, Toast.LENGTH_LONG).show();
        }
    }

    public void closeEnterPasswordDialog( View view ) {
        dismissDialog(enterPasswordDialog);
        if (ProUtils.kioskModeRequired(this)) {
            checkAndStartLauncher();
        }
    }

    public void checkAdministratorPassword( View view ) {
        dialogEnterPasswordBinding.setLoading( true );
        GetServerConfigTask task = new GetServerConfigTask( this ) {
            @Override
            protected void onPostExecute( Integer result ) {
                dialogEnterPasswordBinding.setLoading( false );

                String masterPassword = CryptoHelper.getMD5String( "12345678" );
                if ( settingsHelper.getConfig() != null && settingsHelper.getConfig().getPassword() != null ) {
                    masterPassword = settingsHelper.getConfig().getPassword();
                }

                if ( CryptoHelper.getMD5String( dialogEnterPasswordBinding.password.getText().toString() ).
                        equals( masterPassword ) ) {
                    dismissDialog(enterPasswordDialog);
                    dialogEnterPasswordBinding.setError( false );
                    if (ProUtils.kioskModeRequired(MainActivity.this)) {
                        ProUtils.unlockKiosk(MainActivity.this);
                    }
                    RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Administrator panel opened");
                    startActivity( new Intent( MainActivity.this, AdminActivity.class ) );
                } else {
                    dialogEnterPasswordBinding.setError( true );
                }
            }
        };
        task.execute();
    }

    private void createAndShowUnknownSourcesDialog() {
        dismissDialog(unknownSourcesDialog);
        unknownSourcesDialog = new Dialog( this );
        dialogUnknownSourcesBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_unknown_sources,
                null,
                false );
        unknownSourcesDialog.setCancelable( false );
        unknownSourcesDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        unknownSourcesDialog.setContentView( dialogUnknownSourcesBinding.getRoot() );
        unknownSourcesDialog.show();
    }

    public void continueUnknownSources( View view ) {
        dismissDialog(unknownSourcesDialog);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
        } else {
            // In Android Oreo and above, permission to install packages are set per each app
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        }
    }

    private void createAndShowMiuiPermissionsDialog() {
        dismissDialog(miuiPermissionsDialog);
        miuiPermissionsDialog = new Dialog( this );
        dialogMiuiPermissionsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_miui_permissions,
                null,
                false );
        miuiPermissionsDialog.setCancelable( false );
        miuiPermissionsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        miuiPermissionsDialog.setContentView( dialogMiuiPermissionsBinding.getRoot() );
        miuiPermissionsDialog.show();
    }

    public void continueMiuiPermissions( View view ) {
        dismissDialog(miuiPermissionsDialog);

        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {}

    @Override
    public void onAppChoose( @NonNull AppInfo resolveInfo ) {

    }

    @Override
    public boolean onLongClick( View v ) {
        createAndShowEnterPasswordDialog();
        return true;

    }

    @Override
    public void onClick( View v ) {
        if (v.equals(infoView)) {
            createAndShowInfoDialog();
        } else if (v.equals(updateView)) {
            if (enterDeviceIdDialog != null && enterDeviceIdDialog.isShowing()) {
                Log.i(Const.LOG_TAG, "Occasional update request when device info is entered, ignoring!");
                return;
            }
            Log.i(Const.LOG_TAG, "updating config on request");
            binding.setShowContent(false);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            updateConfig( true );
        }
    }

    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent) {
        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_ENABLE_SETTINGS ) );
        // Delayed start prevents the race of ENABLE_SETTINGS handle and tapping "Next" button
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                createAndShowSystemSettingDialog(message, settingsIntent);
            }
        }, 5000);
    }

    private void createAndShowSystemSettingDialog(final String message, final Intent settingsIntent) {
        dismissDialog(systemSettingsDialog);
        systemSettingsDialog = new Dialog( this );
        dialogSystemSettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_system_settings,
                null,
                false );
        systemSettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );
        systemSettingsDialog.setCancelable( false );

        systemSettingsDialog.setContentView( dialogSystemSettingsBinding.getRoot() );

        dialogSystemSettingsBinding.setMessage(message);

        // Since we need to send Intent to the listener, here we don't use "event" attribute in XML resource as everywhere else
        systemSettingsDialog.findViewById(R.id.continueButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissDialog(systemSettingsDialog);
                // Enable settings once again, because the dialog may be shown more than 3 minutes
                // This is not necessary: the problem is resolved by clicking "Continue" in a popup window
                /*LocalBroadcastManager.getInstance( MainActivity.this ).sendBroadcast( new Intent( Const.ACTION_ENABLE_SETTINGS ) );
                // Open settings with a slight delay so Broadcast would certainly be handled
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(settingsIntent);
                    }
                }, 300);*/
                try {
                    startActivity(settingsIntent);
                } catch (/*ActivityNotFound*/Exception e) {
                    // Open settings by default
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                }
            }
        });

        systemSettingsDialog.show();
    }

    // The following algorithm of launcher restart works in EMUI:
    // Run EMUI_LAUNCHER_RESTARTER activity once and send the old version number to it.
    // The restarter application will check the launcher version each second, and restart it
    // when it is changed.
    private void startLauncherRestarter() {
        // Sending an intent before updating, otherwise the launcher may be terminated at any time
        Intent intent = getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID);
        if (intent == null) {
            Log.i("LauncherRestarter", "No restarter app, please add it in the config!");
            return;
        }
        intent.putExtra(Const.LAUNCHER_RESTARTER_OLD_VERSION, BuildConfig.VERSION_NAME);
        startActivity(intent);
        Log.i("LauncherRestarter", "Calling launcher restarter from the launcher");
    }


}
