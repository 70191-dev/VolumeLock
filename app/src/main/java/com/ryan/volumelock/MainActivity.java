package com.ryan.volumelock;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    public static final String PREFS = "volume_lock_prefs";
    public static final String KEY_LOCKED = "locked";
    public static final String KEY_PERCENT = "percent";
    public static final String KEY_STREAMS_MASK = "streams_mask";

    // Bit flags for which streams to lock
    public static final int STREAM_MEDIA = 1 << AudioManager.STREAM_MUSIC;
    public static final int STREAM_RING = 1 << AudioManager.STREAM_RING;
    public static final int STREAM_NOTIF = 1 << AudioManager.STREAM_NOTIFICATION;
    public static final int STREAM_ALARM = 1 << AudioManager.STREAM_ALARM;

    private SeekBar seekBar;
    private TextView label;
    private Button lockBtn, unlockBtn;
    private android.widget.CheckBox cbMedia, cbRing, cbNotif, cbAlarm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        seekBar = (SeekBar) findViewById(R.id.seekBar);
        label = (TextView) findViewById(R.id.label);
        lockBtn = (Button) findViewById(R.id.lockBtn);
        unlockBtn = (Button) findViewById(R.id.unlockBtn);
        cbMedia = (android.widget.CheckBox) findViewById(R.id.cbMedia);
        cbRing = (android.widget.CheckBox) findViewById(R.id.cbRing);
        cbNotif = (android.widget.CheckBox) findViewById(R.id.cbNotif);
        cbAlarm = (android.widget.CheckBox) findViewById(R.id.cbAlarm);

        final SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int saved = prefs.getInt(KEY_PERCENT, 50);
        int mask = prefs.getInt(KEY_STREAMS_MASK, STREAM_MEDIA);

        seekBar.setProgress(saved);
        updateLabel(saved);
        cbMedia.setChecked((mask & STREAM_MEDIA) != 0);
        cbRing.setChecked((mask & STREAM_RING) != 0);
        cbNotif.setChecked((mask & STREAM_NOTIF) != 0);
        cbAlarm.setChecked((mask & STREAM_ALARM) != 0);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                updateLabel(p);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                prefs.edit().putInt(KEY_PERCENT, sb.getProgress()).apply();
            }
        });

        lockBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int percent = seekBar.getProgress();
                int mask = 0;
                if (cbMedia.isChecked()) mask |= STREAM_MEDIA;
                if (cbRing.isChecked()) mask |= STREAM_RING;
                if (cbNotif.isChecked()) mask |= STREAM_NOTIF;
                if (cbAlarm.isChecked()) mask |= STREAM_ALARM;
                if (mask == 0) {
                    Toast.makeText(MainActivity.this, "Pick at least one stream", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit()
                    .putInt(KEY_PERCENT, percent)
                    .putInt(KEY_STREAMS_MASK, mask)
                    .putBoolean(KEY_LOCKED, true)
                    .apply();
                Intent i = new Intent(MainActivity.this, VolumeLockService.class);
                i.setAction("com.ryan.volumelock.SET_VOLUME");
                i.putExtra("percent", percent);
                i.putExtra("mask", mask);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i);
                } else {
                    startService(i);
                }
                Toast.makeText(MainActivity.this, "Locked at " + percent + "%", Toast.LENGTH_SHORT).show();
            }
        });

        unlockBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefs.edit().putBoolean(KEY_LOCKED, false).apply();
                Intent i = new Intent(MainActivity.this, VolumeLockService.class);
                i.setAction("com.ryan.volumelock.STOP_LOCK");
                startService(i);
                Toast.makeText(MainActivity.this, "Unlocked", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateLabel(int p) {
        label.setText("Volume locked at: " + p + "%");
    }
}
