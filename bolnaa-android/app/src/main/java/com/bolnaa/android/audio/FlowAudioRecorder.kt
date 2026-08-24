package com.bolnaa.android.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

class FlowAudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "FlowAudioRecorder"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SILENCE_AMPLITUDE_THRESHOLD = 500 // Adjust based on mic
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    private val _amplitudeFlow = MutableSharedFlow<Float>(extraBufferCapacity = 1)
    val amplitudeFlow: SharedFlow<Float> = _amplitudeFlow

    var onSilenceDetected: (() -> Unit)? = null
    var autoSilenceDetectionEnabled: Boolean = true
    var silenceTimeoutMs: Long = 1600

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording) return true

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize.coerceAtLeast(4096)
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return false
            }

            audioRecord?.startRecording()
            isRecording = true

            startCaptureLoop()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            return false
        }
    }

    private val pcmOutputStream = ByteArrayOutputStream()

    private fun startCaptureLoop() {
        pcmOutputStream.reset()
        val readBuffer = ShortArray(bufferSize / 2)
        var lastSpeechTimestamp = System.currentTimeMillis()
        var hasSpokenYet = false

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isRecording) {
                val readCount = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                if (readCount > 0) {
                    var sumSquare = 0.0
                    var maxAmp = 0

                    val byteBuffer = ByteArray(readCount * 2)
                    for (i in 0 until readCount) {
                        val sample = readBuffer[i]
                        val absSample = abs(sample.toInt())
                        if (absSample > maxAmp) maxAmp = absSample
                        sumSquare += sample * sample

                        // Convert short to little-endian bytes
                        byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                    }

                    synchronized(pcmOutputStream) {
                        pcmOutputStream.write(byteBuffer)
                    }

                    // Calculate RMS amplitude normalized (0.0 to 1.0)
                    val rms = sqrt(sumSquare / readCount)
                    val normalizedAmp = (rms / 8000f).toFloat().coerceIn(0f, 1f)
                    _amplitudeFlow.tryEmit(normalizedAmp)

                    // Auto-Silence Detection
                    val now = System.currentTimeMillis()
                    if (maxAmp > SILENCE_AMPLITUDE_THRESHOLD) {
                        hasSpokenYet = true
                        lastSpeechTimestamp = now
                    } else if (hasSpokenYet && autoSilenceDetectionEnabled) {
                        if (now - lastSpeechTimestamp > silenceTimeoutMs) {
                            Log.d(TAG, "Silence timeout reached, auto-stopping.")
                            withContext(Dispatchers.Main) {
                                onSilenceDetected?.invoke()
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        isRecording = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }

        val rawPcm: ByteArray
        synchronized(pcmOutputStream) {
            rawPcm = pcmOutputStream.toByteArray()
        }

        cleanup()

        if (rawPcm.isEmpty()) {
            return null
        }

        // Save to cache WAV file
        val wavFile = File(context.cacheDir, "flow_dictation_${System.currentTimeMillis()}.wav")
        WavAudioWriter.writeWavFile(rawPcm, wavFile, SAMPLE_RATE)
        return wavFile
    }

    fun cancelRecording() {
        isRecording = false
        recordingJob?.cancel()
        cleanup()
    }

    private fun cleanup() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
    }

    fun isCurrentlyRecording(): Boolean = isRecording
}
