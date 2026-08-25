package com.bolnaa.android.ai

import android.content.Context
import android.util.Log
import com.bolnaa.android.data.PreferencesManager
import com.bolnaa.android.data.models.FlowTone
import com.bolnaa.android.data.models.SttEngine
import kotlinx.coroutines.flow.first
import java.io.File

class FlowTranscriptionEngine(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val TAG = "FlowTranscriptionEngine"
        private const val HINGLISH_WHISPER_PROMPT =
            "Namaste, main theek hoon, aap kaise ho? Transcribe all Hindi, Hinglish, and Indian speech in English letters / Roman script (Hinglish). For example: kya haal hai, theek hai, bilkul, main aa raha hoon, kya kar rahe ho, aaj bahut kaam hai. Do NOT use Devanagari script."
    }

    private var groqApiKey = ""
    private var openAiApiKey = ""

    val groqClient = GroqWhisperClient { groqApiKey }
    val openAiClient = OpenAIWhisperClient { openAiApiKey }
    val localClient = LocalSpeechClient(context)
    val smartFormatter = FlowSmartFormatter(
        groqKeyProvider = { groqApiKey },
        openAiKeyProvider = { openAiApiKey }
    )

    suspend fun processAudioFile(
        audioFile: File,
        prompt: String = ""
    ): Result<String> {
        groqApiKey = preferencesManager.groqApiKey.first()
        openAiApiKey = preferencesManager.openAiApiKey.first()
        val preferredEngine = preferencesManager.sttEngine.first()
        val tone = preferencesManager.flowTone.first()
        val isAiCleanupEnabled = preferencesManager.isAiCleanupEnabled.first()
        val customVocab = preferencesManager.customVocabulary.first()

        Log.d(TAG, "Processing audio file (${audioFile.length()} bytes) with engine: $preferredEngine")

        val effectivePrompt = if (prompt.isNotBlank()) {
            "$prompt. Transcribe Hindi speech in English letters (Hinglish / Roman script): Namaste, main theek hoon, aap kaise ho, theek hai, kya kar rahe ho."
        } else {
            HINGLISH_WHISPER_PROMPT
        }

        // 1. Perform Speech-to-Text
        val rawTranscriptionResult: Result<String> = when (preferredEngine) {
            SttEngine.GROQ -> {
                if (groqApiKey.isNotBlank()) {
                    groqClient.transcribeAudio(audioFile, effectivePrompt)
                } else if (openAiApiKey.isNotBlank()) {
                    openAiClient.transcribeAudio(audioFile, effectivePrompt)
                } else {
                    Result.failure(IllegalStateException("No Groq or OpenAI API key configured. Please configure in Settings or switch to Google Speech."))
                }
            }
            SttEngine.OPENAI -> {
                if (openAiApiKey.isNotBlank()) {
                    openAiClient.transcribeAudio(audioFile, effectivePrompt)
                } else if (groqApiKey.isNotBlank()) {
                    groqClient.transcribeAudio(audioFile, effectivePrompt)
                } else {
                    Result.failure(IllegalStateException("No OpenAI API key configured. Please configure in Settings or switch to Google Speech."))
                }
            }
            SttEngine.LOCAL -> {
                // If local engine is selected with audio file, we try Groq/OpenAI if available, else error
                if (groqApiKey.isNotBlank()) {
                    groqClient.transcribeAudio(audioFile, effectivePrompt)
                } else {
                    Result.failure(IllegalStateException("Local SpeechRecognizer requires direct microphone stream."))
                }
            }
        }

        // Clean up temporary audio file
        try {
            audioFile.delete()
        } catch (e: Exception) {
            // Ignore
        }

        if (rawTranscriptionResult.isFailure) {
            return rawTranscriptionResult
        }

        val rawText = rawTranscriptionResult.getOrNull() ?: ""
        if (rawText.isBlank()) {
            return Result.success("")
        }

        // 2. Perform Bolnaa Smart AI Formatting
        val formattedText = if (isAiCleanupEnabled) {
            smartFormatter.formatTranscription(
                rawText = rawText,
                tone = tone,
                customVocabulary = customVocab
            )
        } else {
            DevanagariTransliterator.transliterate(rawText.trim())
        }

        return Result.success(formattedText)
    }

    suspend fun formatRawText(rawText: String): String {
        groqApiKey = preferencesManager.groqApiKey.first()
        openAiApiKey = preferencesManager.openAiApiKey.first()
        val tone = preferencesManager.flowTone.first()
        val customVocab = preferencesManager.customVocabulary.first()
        return smartFormatter.formatTranscription(rawText, tone, customVocab)
    }
}
