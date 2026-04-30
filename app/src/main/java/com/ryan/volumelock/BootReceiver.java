package com.ryan.volumelock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences prefs = context.getSharedPreferences(
                MainActivity.PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(MainActivity.KEY_LOCKED, false)) {
            int percent = prefs.getInt(MainActivity.KEY_PERCENT, 50);
            int mask = prefs.getInt(MainActivity.KEY_STREAMS_MASK, MainActivity.STREAM_MEDIA);
            Intent svc = new Intent(context, VolumeLockService.class);
            svc.setAction("com.ryan.volumelock.SET_VOLUME");
            svc.putExtra("percent", percent);
            svc.putExtra("mask", mask);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        }
    }
}
