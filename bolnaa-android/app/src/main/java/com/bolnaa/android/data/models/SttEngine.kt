package com.bolnaa.android.data.models

enum class SttEngine(
    val title: String,
    val description: String,
    val requiresApiKey: Boolean
) {
    GROQ(
        title = "Groq Whisper (Ultra-Fast ~300ms)",
        description = "Blazing fast transcription with Whisper Large v3 on Groq LPU. Recommended for real-time Bolnaa experience.",
        requiresApiKey = true
    ),
    OPENAI(
        title = "OpenAI Whisper",
        description = "Standard OpenAI Whisper-1 cloud transcription with high accuracy across 50+ languages.",
        requiresApiKey = true
    ),
    LOCAL(
        title = "Google On-Device Speech",
        description = "Uses Android's built-in speech recognition. Free, offline-capable, and requires no API key.",
        requiresApiKey = false
    )
}
