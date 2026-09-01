package com.bolnaa.android.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class LocalSpeechClient(private val context: Context) {

    companion object {
        private const val TAG = "LocalSpeechClient"
    }

    private var activeRecognizer: SpeechRecognizer? = null

    fun startListening(
        onPartialResult: ((String) -> Unit)? = null,
        onResult: (Result<String>) -> Unit
    ): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return false

        activeRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        activeRecognizer = recognizer
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                finish(Result.failure(Exception("Speech recognition error: $error")), onResult)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                finish(Result.success(text.trim()), onResult)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { onPartialResult?.invoke(it) }
            }
        })

        return try {
            recognizer.startListening(intent)
            true
        } catch (e: Exception) {
            finish(Result.failure(e), onResult)
            false
        }
    }

    fun stopListening() {
        activeRecognizer?.stopListening()
    }

    private fun finish(result: Result<String>, onResult: (Result<String>) -> Unit) {
        activeRecognizer?.destroy()
        activeRecognizer = null
        onResult(result)
    }

    suspend fun recognizeSpeechLive(
        onPartialResult: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return@withContext Result.failure(
                IllegalStateException("Speech recognition is not available on this device.")
            )
        }

        suspendCancellableCoroutine { continuation ->
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Local Speech ready")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Speech recognition error: $error"
                    }
                    Log.e(TAG, "SpeechRecognizer error: $errorMessage")
                    speechRecognizer.destroy()
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception(errorMessage)))
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    speechRecognizer.destroy()
                    if (continuation.isActive) {
                        continuation.resume(Result.success(text.trim()))
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { partial ->
                        onPartialResult?.invoke(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            continuation.invokeOnCancellation {
                speechRecognizer.cancel()
                speechRecognizer.destroy()
            }

            speechRecognizer.startListening(intent)
        }
    }
}
