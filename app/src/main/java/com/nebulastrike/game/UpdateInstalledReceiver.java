package com.nebulastrike.game;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 系统在应用升级安装完成后发送 MY_PACKAGE_REPLACED；此时立即删除
 * filesDir/update/ 下的安装包残留，落实“安装完成后删除 APK”。
 */
public class UpdateInstalledReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            UpdateManager.cleanup(context);
        }
    }
}
