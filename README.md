# Big-DicTaphone 🍆🎤

**Big-DicTaphone** is a powerful cross-platform voice recording app (Android & iOS) that uses Google Gemini AI to transcribe, summarize, and extract action items from your meetings and voice notes.

## Features

-   **Cross-Platform**: Built for both Android (Kotlin/Compose) and iOS (Swift/SwiftUI).
-   **AI-Powered Summaries**:
    -   **Detailed Transcription**: High-accuracy speech-to-text.
    -   **Key Points**: Bulleted summary of the most important topics.
    -   **Action Items**: auto-extracted tasks with assignees and deadlines.
    -   **Management Updates**: Generates a professional draft for your boss automatically.
    -   **Funny Quotes**: Finds the most memorable moment of the meeting.
-   **True Automation**:
    -   **Auto-Send via SMTP**: Configurable SMTP client (Gmail compatible) allows the app to email summaries silently in the background immediately after processing.
    -   **Speaker Identification**: Uses stereo recording and AI capabilities to differentiate distinct voices.
-   **Privacy Focused**:
    -   Your recordings stay on your device until YOU process them.
    -   Customizable API keys (bring your own Gemini Key).

## Tech Stack

-   **Android**: Kotlin, Jetpack Compose, Material 3, Coroutines, OkHttp.
-   **iOS**: Swift, SwiftUI, AVFoundation, Swift Concurrency.
-   **AI**: Google Gemini 2.5 Flash (Multimodal Audio & Text).

## Setup

1.  **Clone the repo**.
2.  **Add API Key**: Go to Settings in the app and add your Google Gemini API Key.
3.  **Configure Email (Optional)**: Add your SMTP credentials (e.g., Gmail App Password) for auto-email features.

## License

Personal Project.
