# Local speech engine and model provenance

- Engine: [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp), MIT license,
  pinned submodule commit `2eeeba56e9edd762b4b38467bab96c2517163158` (v1.8.3).
  See `android/whisper.cpp/LICENSE`. Its bundled ggml license also applies.
- Models: multilingual Whisper Tiny and Base, converted by the whisper.cpp
  maintainers and distributed at [ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp).
  Original [OpenAI Whisper weights and code](https://github.com/openai/whisper) are MIT licensed.
- Tiny: 77,691,713 bytes; SHA-256
  `be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21`.
- Base: 147,951,465 bytes; SHA-256
  `60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe`.

Model downloads are opt-in. Files are streamed to private storage, size-checked,
hashed, then renamed into place; their hash is verified again before inference.
An incomplete or altered model never reaches the native parser. Model weights are
not committed or bundled into the APK. No GPU/NPU runtime or external FFmpeg binary
is needed by the app. Android's own MediaCodec decodes saved recordings.

The Android capture and processing path is owned by the app's foreground service.
It requires the foreground notification and is intended to remain active while the
screen is locked; transcription is limited to two hours per recording. Legacy
recordings use the existing saved-audio format when their audio file is present.
The current JVM and offline instrumentation checks pass, including ARM64/x86 and
16 KiB page alignment verification. Physical Pixel 11 Pro / Android 17 performance,
battery, thermal and microphone-routing validation remains pending.
