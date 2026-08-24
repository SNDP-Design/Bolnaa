package com.bolnaa.android.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIWhisperClient(private val apiKeyProvider: () -> String) {

    companion object {
        private const val TAG = "OpenAIWhisperClient"
        private const val OPENAI_AUDIO_URL = "https://api.openai.com/v1/audio/transcriptions"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class WhisperResponse(
        val text: String = ""
    )

    suspend fun transcribeAudio(
        audioFile: File,
        prompt: String = "",
        language: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("OpenAI API Key is not configured."))
        }

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            )
            .addFormDataPart("response_format", "json")

        if (prompt.isNotBlank()) {
            requestBodyBuilder.addFormDataPart("prompt", prompt)
        }
        if (!language.isNullOrBlank()) {
            requestBodyBuilder.addFormDataPart("language", language)
        }

        val request = Request.Builder()
            .url(OPENAI_AUDIO_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBodyBuilder.build())
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "OpenAI API error: ${response.code} $responseBody")
                    return@withContext Result.failure(
                        IOException("OpenAI Whisper error (${response.code}): $responseBody")
                    )
                }

                val parsed = json.decodeFromString<WhisperResponse>(responseBody)
                Result.success(parsed.text.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network failure calling OpenAI Whisper", e)
            Result.failure(e)
        }
    }
}
