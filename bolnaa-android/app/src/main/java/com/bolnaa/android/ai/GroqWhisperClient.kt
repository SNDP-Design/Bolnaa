package com.bolnaa.android.ai

import android.util.Log
import com.bolnaa.android.BuildConfig
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

class GroqWhisperClient {

    companion object {
        private const val TAG = "GroqWhisperClient"
        private const val BACKEND_AUDIO_URL = BuildConfig.BOLNAA_BACKEND_URL
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
        if (BACKEND_AUDIO_URL.contains("YOUR_BOLNAA_WORKER")) {
            return@withContext Result.failure(IllegalStateException("Bolnaa backend is not configured."))
        }

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            )
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0.0")

        if (prompt.isNotBlank()) {
            requestBodyBuilder.addFormDataPart("prompt", prompt)
        }
        if (!language.isNullOrBlank()) {
            requestBodyBuilder.addFormDataPart("language", language)
        }

        val request = Request.Builder()
            .url(BACKEND_AUDIO_URL)
            .post(requestBodyBuilder.build())
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Groq API error: ${response.code} $responseBody")
                    return@withContext Result.failure(
                        IOException("Groq Whisper error (${response.code}): $responseBody")
                    )
                }

                val parsed = json.decodeFromString<WhisperResponse>(responseBody)
                Result.success(parsed.text.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network failure calling Groq Whisper", e)
            Result.failure(e)
        }
    }
}
