# Volume Lock

Locks media/ringtone/notification/alarm volume at a fixed percent. Survives reboots, prevents volume keys and apps from changing it.

## Build

Push to GitHub, the included workflow builds the APK automatically.

## Use

1. Install APK on phone
2. Open app, drag slider to desired %
3. Check the streams you want locked (media/ring/notif/alarm)
4. Tap LOCK
5. App lock the Settings app on the phone so nobody can change anything

## ADB control

```
# Lock media (mask 8) at 50%
adb shell am startservice -n com.ryan.volumelock/.VolumeLockService \
    -a com.ryan.volumelock.SET_VOLUME --ei percent 50 --ei mask 8

# Mask values: media=8, ring=4, notif=32, alarm=16
# Combine: media+ring+notif+alarm = 8+4+32+16 = 60

# Unlock
adb shell am startservice -n com.ryan.volumelock/.VolumeLockService \
    -a com.ryan.volumelock.STOP_LOCK
```
