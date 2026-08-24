# 🎙️ Bolnaa Android Application

Bolnaa is a native, high-performance voice dictation assistant for Android.

## Architecture

- **UI Layer**: Jetpack Compose with Material 3 dark-mode theme
- **Accessibility Engine**: `FlowAccessibilityService` for global text injection
- **Floating Overlay**: `FlowOverlayService` & `FloatingBubbleView` (Window Manager overlay)
- **Audio Engine**: `FlowAudioRecorder` (16kHz PCM WAV recording + Real-time Amplitude Flow + VAD)
- **AI Processing**: `FlowTranscriptionEngine` with Groq, OpenAI, and Local On-Device Speech APIs
- **OTA Updater**: `AppUpdateManager` with direct GitHub Releases auto-updater

## Build

```bash
./gradlew assembleRelease
```
