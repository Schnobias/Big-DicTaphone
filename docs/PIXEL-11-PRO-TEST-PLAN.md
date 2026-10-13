# Pixel 11 Pro acceptance checks

Android targets API 36 and includes an ARM64 native library. It uses portable CPU
inference rather than a Pixel-specific NPU, Google speech service, or model-name
allowlist. The same path supports other ARM64 Android devices from Android 8.
Native libraries and APK alignment support 16 KB pages. Physical-device recognition
quality, speed, battery drain, thermal behavior and microphone routing need testing
on the actual handset; an emulator is not evidence for those measurements.

1. Install the debug APK (`adb install -r app-debug.apk`) on a test device. A
   differently signed existing installation requires a matching signing key to
   update. Do not uninstall an existing app containing recordings to work around it.
2. Settings → Transcription → On this phone. Download Base on Wi-Fi (141 MiB).
   Tiny (74 MiB) is a faster alternative. Both are multilingual. Check the installed
   indicator. There is no Gemini-key requirement and no automatic cloud fallback.
3. Enable airplane mode, with Wi-Fi disabled. Record 30–60 seconds in English.
   Pause for ten seconds and resume. Stop, name and save. The timer must exclude
   the pause; playback must contain the complete spoken material.
4. Open Recordings → recording. Confirm an on-phone transcript, readable/selectable
   text and no fabricated summary or email card. Repeat in Dutch and Auto-detect.
5. Record while locking the phone for two minutes. Unlock and stop. Check that the
   foreground notification and microphone indicator disappear and playback remains intact.
6. Start processing a longer recording, lock the screen and wait for completion.
   Repeat while using another app. Cancel from the notification, then retry. Audio
   must be retained, temporary decoded audio removed, and duplicate queue requests
   must not start parallel inference or produce duplicate delivery.
7. Test a 15-minute conversation, background the app briefly, and return. Record
   wall time, peak memory, device temperature, battery drop, transcript omissions
   and speaker overlap. Rotate, navigate away and recreate the screen during both
   recording and transcription. Pause/resume using the notification. Stop/save
   there too, reopen the app and immediately start another recording.
8. Deny microphone permission, then grant it. Try an immediate stop, silent recording,
   low storage, missing model, interrupted model download and corrupted audio.
   Failures must be visible and must not upload anything or overwrite valid audio.
9. Kill the app process during processing and reopen it. The recording should be
   marked interrupted and retryable; its source audio must remain. Repeat during
   capture: recovered audio must be listed with an interruption warning, even if
   Android could not finalize the M4A. Restart during model download: partial data
   must not be considered installed. A failed replacement must keep the good model.
10. If testing cloud mode, explicitly select it and use non-sensitive test audio.
    Existing LOCAL recordings must keep their mode on retry. Actual API/email calls
    are separate checks requiring your own credentials and recipients.

Run the automated suite on Android 16/API 36 and Android 17/API 37, including a
16 KB image. Google lists Android 17 for the [Pixel 11 Pro](https://store.google.com/product/pixel_11_pro_specs?hl=en-US).
Emulator signal routing is not physical microphone evidence. On the handset,
speak a numbered phrase every five seconds across lock, switching and rotation;
play back the full file and check every phrase, excluding deliberate pauses.
Record interruptions (phone call, microphone privacy toggle, Bluetooth change)
and verify that the UI reports them and retains all recoverable audio.

For service timeout testing on a disposable emulator, shorten Android's foreground
service time budget and verify that the job becomes retryable and the service stops
promptly. Restore the system setting afterwards. Never delete user recordings to
simulate low storage; use an isolated test repository or a disposable emulator.

Useful read-only diagnostics:

```sh
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getconf PAGE_SIZE
adb shell dumpsys meminfo com.bigdictaphone.app
adb logcat -b crash -d
```

Test speech fixture: the public-domain JFK sample from the pinned whisper.cpp source,
converted to stereo 44.1 kHz AAC/M4A. Instrumentation tests explicitly skip inference
if the model is absent. CI builds the tests; it does not claim device inference passed.
