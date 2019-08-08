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

import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ActivityAdminBinding;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.util.AppInfo;
import com.hmdm.launcher.util.LegacyUtils;

public class AdminActivity extends BaseActivity {

    private static final String KEY_APP_INFO = "info";
    private SettingsHelper settingsHelper;
    private ProgressDialog progressDialog;

    @Nullable
    public static AppInfo getAppInfo(Intent intent){
        if (intent == null){
            return null;
        }
        return intent.getParcelableExtra(KEY_APP_INFO);
    }

    ActivityAdminBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_admin);
        binding.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        binding.toolbar.setTitle( R.string.app_name );
        binding.toolbar.setSubtitle( R.string.vendor );

        // If QR code doesn't contain "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED":true
        // the system launcher is turned off, so it's not possible to exit and we must hide the exit button
        // Currently the QR code contains this parameter, so the button is always visible
        //binding.systemLauncherButton.setVisibility(Utils.isDeviceOwner(this) ? View.GONE : View.VISIBLE);

        if ( Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ) {
            binding.rebootButton.setVisibility(View.GONE);
        }

        settingsHelper = SettingsHelper.getInstance( this );
        binding.deviceId.setText(settingsHelper.getDeviceId());
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    public void changeDeviceId(View view) {
        dismissDialog(enterDeviceIdDialog);
        createAndShowEnterDeviceIdDialog(false, settingsHelper.getDeviceId());
    }

    public void allowSettings(View view) {
        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_ENABLE_SETTINGS ) );
        Toast.makeText(this, R.string.settings_allowed, Toast.LENGTH_LONG).show();
        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        //finish();
    }

    public void saveDeviceId(View view ) {
        String deviceId = enterDeviceIdDialogBinding.deviceId.getText().toString();
        if ( "".equals( deviceId ) ) {
            return;
        } else {
            settingsHelper.setDeviceId( deviceId );
            enterDeviceIdDialogBinding.setError( false );

            dismissDialog(enterDeviceIdDialog);

            Log.i(LOG_TAG, "saveDeviceId(): calling updateConfig()");
            updateConfig(view);
        }
    }

    public void updateConfig( View view ) {
        LocalBroadcastManager.getInstance( this ).
                sendBroadcast( new Intent( Const.ACTION_UPDATE_CONFIGURATION ) );
        finish();
    }

    public void exitToSystemLauncher( View view ) {
        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_SERVICE_STOP ) );
        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_EXIT ) );

        // One second delay is required to avoid race between opening a forbidden activity and stopping the locked mode
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setMessage(getString(R.string.switch_off_blockings));
        progressDialog.show();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }

                Intent intent = new Intent( Intent.ACTION_MAIN );
                intent.addCategory( Intent.CATEGORY_HOME );
                intent.addCategory( Intent.CATEGORY_DEFAULT );
                intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );

                startActivity( Intent.createChooser( intent, getString( R.string.select_system_launcher ) ) );
            }
        }, 1000);
    }

    public void reboot(View view) {
        if ( Build.VERSION.SDK_INT > Build.VERSION_CODES.M ) {
            ComponentName deviceAdmin = LegacyUtils.getAdminComponentName(this);
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            try {
                devicePolicyManager.reboot(deviceAdmin);
            } catch (Exception e) {
                Toast.makeText(this, R.string.reboot_failed, Toast.LENGTH_LONG).show();
            }
        }
    }
}
