# Big-DicTaphone 🍆🎤

**Big-DicTaphone** is a cross-platform voice recording app (Android & iOS). Android provides local Whisper transcription; iOS uses Apple's installed on-device Speech language in local-only mode. Gemini is an optional cloud summarizer.

## Features

-   **Cross-Platform**: Built for both Android (Kotlin/Compose) and iOS (Swift/SwiftUI).
-   **AI-Powered Summaries**:
    -   **Detailed Transcription**: High-accuracy speech-to-text from the platform's local engine when enabled.
    -   **Key Points**: Bulleted summary of the most important topics.
    -   **Action Items**: auto-extracted tasks with assignees and deadlines.
    -   **Management Updates**: Generates a professional draft for your boss automatically.
    -   **Funny Quotes**: Extracts a quote only when it is present in the transcript.
-   **True Automation**:
    -   **Auto-Send via SMTP**: Configurable SMTP client (Gmail compatible) allows the app to email summaries silently in the background immediately after processing.
    -   **Local transcription**: Android Tiny/Base Whisper models support Dutch and English without uploading audio. iOS local-only mode skips Gemini and auto-email.
-   **Privacy Focused**:
    -   Your recordings stay on your device until YOU process them.
    -   Customizable API keys (bring your own Gemini Key).

## Tech Stack

-   **Android**: Kotlin, Jetpack Compose, Material 3, Coroutines, OkHttp, service-owned foreground capture/processing.
-   **iOS**: Swift, SwiftUI, AVFoundation, Swift Concurrency.
-   **AI**: Google Gemini 2.5 Flash for optional transcript summarization.

## Setup

1.  **Clone the repo**.
2.  **Optional API Key**: Add a Google Gemini API key only when cloud summaries are wanted. Local Android and iOS transcription do not require it.
3.  **Configure Email (Optional)**: Add your SMTP credentials (e.g., Gmail App Password) for auto-email features.

Android local transcription supports Tiny/Base Dutch and English models. Model downloads are opt-in and audio remains local; the current product limit is two hours per transcription. Legacy recordings remain readable where their audio file is present. Background recording requires the Android foreground service/notification; keep the app open for transcription processing. Physical Pixel 11 Pro performance and Android 17 launch-device validation are still pending. See `docs/PIXEL-11-PRO-TEST-PLAN.md` and `docs/THIRD-PARTY.md`.

## License

Personal Project.
