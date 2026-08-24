# 🎙️ Whisper Flow for Android (Wispr Flow Clone)

A native, high-performance voice dictation assistant for Android inspired by [Wispr Flow](https://wisprflow.ai/). 

Whisper Flow hovers as a sleek floating bubble above your keyboard. Tap the bubble to speak naturally—the app records your voice, removes filler words (*"um"*, *"uh"*, *"you know"*), fixes grammar/punctuation using AI, and **automatically pastes the cleaned text directly into your active input field** across WhatsApp, Telegram, Slack, Gmail, Chrome, Notes, and any other Android app!

---

## ✨ Features

- **🫧 Floating Wispr Bubble Overlay**: Sits neatly docked above your keyboard or on screen edges. Tap or hold to talk.
- **⚡ Auto Text Injection**: Uses Android's `AccessibilityService` to directly inject transcribed & formatted text into the active cursor position without manual copying.
- **🚀 Ultra-Fast Whisper STT**:
  - **Groq Whisper Large v3**: Blazing fast sub-second (~300ms) transcription for the true instant Wispr Flow experience.
  - **OpenAI Whisper-1**: High-precision cloud transcription across 50+ languages.
  - **Google On-Device Speech**: Free built-in fallback with zero API keys required.
- **🧠 Flow Smart AI Clean-up**:
  - Cleans filler words (*"um"*, *"uh"*, *"like"*, *"so basically"*), eliminates stutters, and adds natural punctuation.
  - **Tone Presets**:
    - 🌸 **Natural Smart Flow**: Human-like polished prose.
    - 🎯 **Direct Dictation (Verbatim)**: Exact word-for-word transcript.
    - 💼 **Professional & Polished**: Executive tone tailored for emails and Slack.
    - 📝 **Bullet Points & Notes**: Converts spoken thoughts into organized bullet lists.
- **🌊 Real-time Waveform Visualizer**: Dynamic reactive audio bars while speaking.
- **🤫 Auto-Stop on Silence**: Voice Activity Detection (VAD) automatically finishes dictation when you stop talking.
- **🎨 Glassmorphic Jetpack Compose UI**: Modern Material 3 dark aesthetic.

---

## 🏗️ Architecture Overview

```
whisper-flow-android/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/wisprflow/android/
│   │   │   ├── FlowApplication.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   │   ├── PreferencesManager.kt       # DataStore preference storage
│   │   │   │   └── models/
│   │   │   │       ├── DictationState.kt       # IDLE, LISTENING, PROCESSING, SUCCESS
│   │   │   │       ├── FlowTone.kt             # NATURAL, VERBATIM, PROFESSIONAL, BULLETS
│   │   │   │       └── SttEngine.kt            # GROQ, OPENAI, LOCAL
│   │   │   ├── audio/
│   │   │   │   ├── FlowAudioRecorder.kt        # 16kHz PCM capture + live amplitude + VAD
│   │   │   │   └── WavAudioWriter.kt           # RIFF/WAV header encoder
│   │   │   ├── ai/
│   │   │   │   ├── GroqWhisperClient.kt        # Fast Groq Whisper API client
│   │   │   │   ├── OpenAIWhisperClient.kt      # OpenAI Whisper API client
│   │   │   │   ├── LocalSpeechClient.kt        # Android SpeechRecognizer client
│   │   │   │   ├── FlowSmartFormatter.kt       # LLM filler word stripper & formatter
│   │   │   │   └── FlowTranscriptionEngine.kt  # Unified STT + AI orchestration
│   │   │   ├── service/
│   │   │   │   ├── FlowAccessibilityService.kt # Detects focused input & injects text
│   │   │   │   ├── FlowOverlayService.kt       # Foreground service managing bubble
│   │   │   │   └── overlay/
│   │   │   │       └── FloatingBubbleView.kt   # Smooth draggable bubble + waveform canvas
│   │   │   └── ui/
│   │   │       ├── theme/                      # Color, Theme, Typography
│   │   │       ├── components/                 # PermissionCard, WaveformPreview
│   │   │       └── screens/
│   │   │           ├── DashboardScreen.kt      # Master toggle & tone selector
│   │   │           ├── SetupWizardScreen.kt    # Step-by-step permissions checklist
│   │   │           ├── PlaygroundScreen.kt     # Live in-app dictation playground
│   │   │           └── SettingsScreen.kt       # API keys, engine, silence timeout
│   │   └── res/
│   │       ├── xml/accessibility_service_config.xml
│   │       └── values/{colors.xml, strings.xml, themes.xml}
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

---

## 🚀 How to Run and Install

### Step 1: Open in Android Studio
1. Launch **Android Studio** (Hedgehog, Iguana, Jellyfish, or newer).
2. Click **Open** and select the `whisper-flow-android` folder:
   ```
   /Users/sndp/Documents/CopyMe/whisper-flow-android
   ```
3. Allow Gradle to sync dependencies.

### Step 2: Build & Deploy
- Connect an Android device with USB Debugging enabled (or start an Android Emulator with API 26+).
- Click the **Run** button ▶️ in Android Studio or run from terminal:
  ```bash
  ./gradlew assembleDebug
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

### Step 3: Grant Required Permissions
When launching the app for the first time, tap **Permissions & Setup**:
1. **Microphone Access**: Required to record voice dictation.
2. **Display Over Other Apps (Overlay)**: Required for the floating bubble.
3. **Accessibility Service**: Go to *Settings > Accessibility > Installed apps / Downloaded services* and enable **Whisper Flow Direct Insertion**.

### Step 4: Configure Groq API Key (Recommended for <300ms Speed)
1. Open **Settings** (⚙️ top-right icon).
2. Get a free API key at [console.groq.com](https://console.groq.com/keys).
3. Paste it into the **Groq API Key** field.
4. *(Optional)* If you don't have an API key, select **Google On-Device Speech** to use the free offline recognizer!

---

## 🎯 How to Use

1. Switch on the **Floating Wispr Bubble** toggle on the Dashboard.
2. Open any app (e.g. WhatsApp, Slack, Gmail, or Notes) and focus any text input field so your keyboard opens.
3. Tap the floating **Wispr Bubble**.
4. Speak naturally:
   > *"Um, hey team, uh, let's schedule our project kickoff meeting for tomorrow at 3 PM."*
5. Tap the bubble to stop (or simply stop speaking to let auto-silence trigger).
6. **Watch the magic happen!** Whisper Flow cleans up the sentence and automatically types:
   > *"Hey team, let's schedule our project kickoff meeting for tomorrow at 3 PM."*

---

## 🔒 Privacy & Security
- Audio files are stored temporarily in volatile app cache only during transcription and deleted immediately.
- Zero analytics or data collection.
- API keys are stored locally on your device in encrypted Android DataStore preferences.
