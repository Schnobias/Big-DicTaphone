# Big-DicTaphone Android

Native Android version of Big-DicTaphone - a voice recording app with AI-powered transcription and summarization.

## Features

- 🎤 **Voice Recording** with audio visualization
- 🌍 **Multi-language** support (Dutch & English)
- 🗣️ **Local Transcription** using downloadable Whisper Tiny/Base models (Dutch and English)
- 🤖 **Optional AI Summarization** using Gemini Flash
- 📧 **Email Summaries** directly from the app
- 📱 **Material You** design with dynamic colors

## Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 36 / API 36
- A Pixel 11 Pro or another ARM64 Android device; the portable inference path supports Android 8+

### Building

1. **Open in Android Studio**
   ```
   Open the `android` folder as a project
   ```

2. **Sync Gradle**
   - Android Studio will automatically sync. If not, click "Sync Project with Gradle Files"

3. **Get a Gemini API Key** (Free)
   - Go to [Google AI Studio](https://makersuite.google.com/app/apikey)
   - Sign in with your Google account
   - Create a new API key
   - Copy the key

4. **Run the App**
   - Connect a test device via USB (enable USB debugging)
   - Select your device in the device dropdown
   - Click the Run button (or press Shift+F10)

5. **Configure the App**
   - Open the Settings tab
   - Enter your email address
   - Paste your Gemini API key
   - Tap "Save"

## Usage

### Recording

1. Tap the **Record** tab
2. Select your language (🇳🇱 Dutch or 🇺🇸 English)
3. Grant microphone permission when prompted
4. Tap the **red microphone button** to start
5. Tap again to **stop** and save

### Viewing Summaries

1. Go to the **Recordings** tab
2. Tap on a recording to view:
   - Key points
   - Action items
   - Future discussion topics

### Sending Emails

1. Open a processed recording
2. Tap **Email Summary to Me**
3. Or tap **Send Management Update** for executives

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Design | Material 3 / Material You |
| Audio | MediaRecorder |
| AI | Local Whisper Tiny/Base; optional Google Gemini 2.5 Flash |
| Storage | DataStore + JSON files |
| Networking | OkHttp |

## Local transcription and privacy

Whisper Tiny (74 MiB) and Base (141 MiB) are downloaded explicitly and verified before use. Both support Dutch and English. Audio is decoded and transcribed locally; there is no automatic cloud fallback. A single transcription is limited to two hours. Foreground capture and processing use the app service and notification, including while the screen is locked. Legacy recordings remain available when their audio file is present. Android 17 on the Pixel 11 Pro is the planned launch-device validation target; physical performance, battery and thermal results are pending.

## Project Structure

```
android/
├── build.gradle.kts              # Project build config
├── settings.gradle.kts           # Project settings
└── app/
    ├── build.gradle.kts          # App build config
    └── src/main/
        ├── AndroidManifest.xml   # App manifest
        ├── java/com/bigdictaphone/app/
        │   ├── BigDicTaphoneApp.kt
        │   ├── MainActivity.kt
        │   ├── BigDicTaphoneNavigation.kt
        │   ├── data/             # Data models
        │   ├── services/         # Business logic
        │   ├── viewmodel/        # ViewModels
        │   └── ui/
        │       ├── screens/      # UI screens
        │       └── theme/        # Material theme
        └── res/
            └── values/           # Resources
```

## Troubleshooting

**App crashes on startup?**
- Make sure you have Android 13 or later
- Check that all permissions are granted

**Recording doesn't work?**
- Go to Settings > Apps > Big-DicTaphone > Permissions
- Enable Microphone permission

**Summarization fails?**
- Check your Gemini API key in Settings
- Ensure you have internet connection
- Try processing the recording again

## License

MIT License
