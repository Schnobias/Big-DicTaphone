# Big DicTaphone review and validation

## Result

The application now treats recording and transcription as durable operating-system work instead of screen-owned tasks. On Android, microphone capture lives in a foreground service and local transcription runs through one persisted foreground queue. The selected processing mode is stored on each recording. Local multilingual Whisper Base is the default, Tiny is the faster option, and Dutch, English, and automatic language detection are available without a network connection.

The review also repaired the iOS project graph and package dependency, made speech cancellation and timeouts deterministic, surfaced Keychain failures, and added reproducible simulator CI.

## Main findings addressed

- Activity recreation previously owned or could discard capture state. `RecordingForegroundService` now owns the recorder and exposes pause, resume, and stop/save notification actions. A partial wake lock keeps capture active with the display asleep, and interrupted audio remains recoverable.
- Processing was not a durable background operation. `ProcessingForegroundService` now consumes one persisted queue, reports progress, accepts notification cancellation, prevents duplicate work, and preserves source audio on cancellation, failure, or service timeout.
- State updates could race or appear saved before reaching storage. The application-scoped `RecordingRepository` serializes mutations and uses awaitable `AtomicFile` writes followed by `fd.sync()`. Capture, processing, delivery, and recovery states persist independently.
- Privacy mode could drift with later settings changes. Each recording now keeps the mode selected when it was created. Local mode never falls back to cloud. Manual email composition and automatic SMTP delivery are separate paths, and uncertain delivery is not retried automatically.
- Model installation did not expose enough integrity state. The model store verifies size and SHA-256, reports download progress and damaged installations, uses atomic replacement, removes incomplete downloads, and preserves a valid installed model when replacement fails.
- Cloud upload used a single-request path. Gemini processing now uses authenticated resumable upload for larger files, rejects unexpected upload origins, polls cancellably, and deletes remote files after processing.
- The iOS project contained missing source references and an unresolved mail dependency. Source membership, the SwiftSMTP 5.1.0 pin and transitive lock, the shared scheme, simulator tests, cancellation, timeout, and Keychain error paths are repaired.

## Automated evidence

Validated on the final working tree:

- Android debug APK and instrumentation APK build successfully.
- 16 JVM tests pass.
- Android lint passes.
- 12 instrumentation tests pass on the API 36 Android 16 x86_64 emulator.
- The lifecycle suite records through pause/resume, Home, screen sleep, wake, activity recreation, stop/review, and an immediate second recording. It verifies non-empty saved audio and durable duration.
- Real offline Whisper Tiny inference completes while the emulator display remains asleep and airplane mode is enabled. The test verifies automatic language detection and expected speech, cancellation from notification, source retention, temporary-file cleanup, silence handling, corrupted audio, and duplicate queue suppression.
- Missing, damaged, interrupted, cancelled, and digest-mismatched model download paths pass, including replacement without overwriting a known-good model.
- GitHub Actions passes both Android and iOS jobs. The iOS job resolves pinned packages, builds for testing, and runs simulator tests on macOS 15 with Xcode 16.4.
- `zipalign -c -P 16 -v 4` passes for the APK. Every `PT_LOAD` segment in the packaged ARM64 and x86_64 native libraries has `p_align=0x4000` (16 KB).

The public-domain JFK fixture from the pinned whisper.cpp source is used for offline inference. No model, API key, or mail credential is bundled in the APK.

## Android 17 and Pixel 11 Pro status

The app and instrumentation APK install on the official API 37 `google_apis_ps16k` x86_64 image, and the guest reports Android 17 with a 16,384-byte page size. Functional execution is blocked by a system-image graphics crash in SurfaceFlinger before the test runner can complete. The failing build is `google/sdk_gphone16k_x86_64/emu64xa16k:17/CE2A.260420.019/15611780`; the native assertion is `!rcEnc->featureInfo()->hasReadColorBufferDma` in `mapper.ranchu.so`. The crash reproduces with ANGLE and software graphics, so API 37 installation and page-size compatibility are verified, while API 37 UI behavior is not claimed.

Pixel 11 Pro compatibility is covered statically by successful Android 17 installation, ARM64 output, portable CPU inference, and 16 KB native/APK alignment. A physical Pixel 11 Pro is still required to verify microphone routing, lock-screen capture under the handset firmware, recognition speed, thermals, memory use, and battery drain. Follow [PIXEL-11-PRO-TEST-PLAN.md](PIXEL-11-PRO-TEST-PLAN.md) on the handset before production release.

## Remaining release checks

- Run the numbered phrase playback check, Bluetooth/call/privacy-toggle interruptions, long-recording thermal test, and low-storage scenario on a physical Pixel 11 Pro.
- Exercise real Gemini and SMTP accounts only with test credentials and recipients. Automated coverage verifies request construction and state transitions without sending mail.
- Produce a release-signed bundle with the owner's production signing key. The delivered APK is debug-signed for testing.
