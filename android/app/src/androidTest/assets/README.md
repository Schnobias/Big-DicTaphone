`jfk.m4a` is a stereo, 44.1 kHz AAC conversion of `android/whisper.cpp/samples/jfk.wav`,
the public-domain JFK inaugural address fixture shipped by whisper.cpp. It exercises
the same container, codec and original sample rate as saved app recordings.
`silence.m4a` is two seconds of generated silence at 44.1 kHz.

Inference tests require the verified multilingual Tiny model in the target app's
`no_backup/models/ggml-tiny.bin`. The model is deliberately not bundled in the test APK.
Run with airplane mode enabled to verify the offline path. Missing model tests are
reported as skipped, never counted as successful inference.
