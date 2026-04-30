package com.ryan.volumelock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

public class VolumeLockService extends Service {

    private static final String CHANNEL_ID = "volume_lock_channel";
    private static final int NOTIF_ID = 4343;

    private int lockedPercent = 50;
    private int streamsMask = 0;
    private AudioManager audio;
    private ContentObserver observer;
    private Handler handler;
    private boolean active = false;

    private final Runnable enforceLoop = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            applyAllStreams();
            handler.postDelayed(this, 1500);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // System restart after kill
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
            if (prefs.getBoolean(MainActivity.KEY_LOCKED, false)) {
                lockedPercent = prefs.getInt(MainActivity.KEY_PERCENT, 50);
                streamsMask = prefs.getInt(MainActivity.KEY_STREAMS_MASK, MainActivity.STREAM_MEDIA);
                startLock();
            } else {
                stopSelf();
            }
            return START_STICKY;
        }

        String action = intent.getAction();
        if ("com.ryan.volumelock.SET_VOLUME".equals(action)) {
            int p = intent.getIntExtra("percent", 50);
            int m = intent.getIntExtra("mask", MainActivity.STREAM_MEDIA);
            if (p < 0) p = 0;
            if (p > 100) p = 100;
            lockedPercent = p;
            streamsMask = m;
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                    .putInt(MainActivity.KEY_PERCENT, p)
                    .putInt(MainActivity.KEY_STREAMS_MASK, m)
                    .putBoolean(MainActivity.KEY_LOCKED, true)
                    .apply();
            startLock();
        } else if ("com.ryan.volumelock.STOP_LOCK".equals(action)) {
            stopLock();
            stopSelf();
        }
        return START_STICKY;
    }

    private void startLock() {
        startForegroundCompat();
        applyAllStreams();
        registerObserver();
        active = true;
        handler.removeCallbacks(enforceLoop);
        handler.postDelayed(enforceLoop, 1500);
    }

    private void applyAllStreams() {
        for (int s = 0; s <= 5; s++) {
            int bit = 1 << s;
            if ((streamsMask & bit) != 0) {
                applyVolume(s);
            }
        }
    }

    private void applyVolume(int stream) {
        try {
            int max = audio.getStreamMaxVolume(stream);
            int target = Math.round(max * (lockedPercent / 100f));
            if (target < 0) target = 0;
            if (target > max) target = max;
            int current = audio.getStreamVolume(stream);
            if (current != target) {
                audio.setStreamVolume(stream, target, 0);
            }
        } catch (Exception ignored) {}
    }

    private void registerObserver() {
        if (observer != null) return;
        observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                if (!active) return;
                applyAllStreams();
            }
        };
        try {
            // Watch the system audio settings table
            getContentResolver().registerContentObserver(
                    Settings.System.CONTENT_URI, true, observer);
        } catch (Exception ignored) {}
    }

    private void stopLock() {
        active = false;
        handler.removeCallbacks(enforceLoop);
        if (observer != null) {
            try { getContentResolver().unregisterContentObserver(observer); } catch (Exception ignored) {}
            observer = null;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        stopLock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "Volume Lock", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Foreground service notification");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("Volume locked at " + lockedPercent + "%")
         .setContentText("Tap to adjust")
         .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
         .setContentIntent(pi)
         .setOngoing(true);
        return b.build();
    }

    private void startForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, buildNotification());
            }
        } catch (Exception ignored) {}
    }
}
