# 🎙️ Bolnaa for Android

A native, high-performance voice dictation assistant for Android inspired by [Wispr Flow](https://wisprflow.ai/). 

**Bolnaa** hovers as a sleek floating bubble above your keyboard. Tap the bubble to speak naturally—the app records your voice, removes filler words (*"um"*, *"uh"*, *"you know"*), fixes grammar/punctuation using AI, and **automatically pastes the cleaned text directly into your active input field** across WhatsApp, Telegram, Slack, Gmail, Chrome, Notes, and any other Android app!

---

## ✨ Features

- **🫧 Floating Bolnaa Bubble Overlay**: Sits neatly docked above your keyboard or on screen edges. Tap or hold to talk.
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
- **🔄 Live OTA Updates**: Auto-updates over-the-air whenever changes are pushed to GitHub.

---

## 📲 Direct APK Download

👉 **[Download Bolnaa APK (bolnaa.apk)](https://github.com/SNDP-Design/Bolnaa/releases/latest/download/bolnaa.apk)**
