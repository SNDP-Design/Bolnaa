package com.bolnaa.android.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.bolnaa.android.MainActivity
import com.bolnaa.android.R
import com.bolnaa.android.ai.FlowTranscriptionEngine
import com.bolnaa.android.audio.FlowAudioRecorder
import com.bolnaa.android.data.PreferencesManager
import com.bolnaa.android.data.models.DictationState
import com.bolnaa.android.service.overlay.FloatingBubbleView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class FlowOverlayService : Service() {

    companion object {
        private const val TAG = "FlowOverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "flow_overlay_service_channel"

        var isRunning = false
            private set

        fun start(context: Context) {
            try {
                val intent = Intent(context, FlowOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FlowOverlayService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FlowOverlayService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop FlowOverlayService", e)
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var audioRecorder: FlowAudioRecorder
    private lateinit var transcriptionEngine: FlowTranscriptionEngine

    private var bubbleView: FloatingBubbleView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var amplitudeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "FlowOverlayService onCreate")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferencesManager = PreferencesManager(this)
        audioRecorder = FlowAudioRecorder(this)
        transcriptionEngine = FlowTranscriptionEngine(this, preferencesManager)

        startForegroundNotification()
        createFloatingBubble()
        observePreferences()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FlowOverlayService onStartCommand (START_STICKY)")
        isRunning = true
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "FlowOverlayService onTaskRemoved, maintaining foreground execution")
        // Schedule auto-respawn if killed by aggressive OS task killers
        try {
            val restartIntent = Intent(applicationContext, FlowOverlayService::class.java).also {
                it.setPackage(packageName)
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext, 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 800,
                restartPendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm respawn", e)
        }
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running_title))
            .setContentText(getString(R.string.service_running_desc))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForeground with microphone type", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "Fatal fallback startForeground error", e2)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createFloatingBubble() {
        val density = resources.displayMetrics.density
        val baseSize = (72 * density).toInt()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        val initialX = displayMetrics.widthPixels - baseSize - (16 * density).toInt()
        val initialY = displayMetrics.heightPixels / 2 - (baseSize / 2)

        val params = WindowManager.LayoutParams(
            baseSize,
            baseSize,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        bubbleView = FloatingBubbleView(this).apply {
            layoutParamsWindowManager = params
            onBubbleClick = { handleBubbleClick() }
            onPositionChanged = { x, y ->
                serviceScope.launch {
                    preferencesManager.setBubblePosition(x, y)
                }
            }
            // Start hidden if attach to keyboard is enabled
            visibility = android.view.View.GONE
            alpha = 0f
        }

        try {
            windowManager.addView(bubbleView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating bubble view", e)
        }
    }

    private var isKeyboardOnlyMode = true

    private fun observePreferences() {
        serviceScope.launch {
            preferencesManager.silenceTimeoutMs.collect { timeout ->
                audioRecorder.silenceTimeoutMs = timeout.toLong()
            }
        }
        serviceScope.launch {
            preferencesManager.isAutoStopSilence.collect { enabled ->
                audioRecorder.autoSilenceDetectionEnabled = enabled
            }
        }
        serviceScope.launch {
            preferencesManager.isAttachToKeyboardEnabled.collect { enabled ->
                isKeyboardOnlyMode = enabled
                updateBubbleVisibility(FlowAccessibilityService.isKeyboardVisibleFlow.value)
            }
        }
        serviceScope.launch {
            preferencesManager.isFreePlacementEnabled.collect { enabled ->
                bubbleView?.isFreePlacementEnabled = enabled
            }
        }
        serviceScope.launch {
            FlowAccessibilityService.isKeyboardVisibleFlow.collect { isKeyboardOpen ->
                updateBubbleVisibility(isKeyboardOpen)
            }
        }
        serviceScope.launch {
            FlowAccessibilityService.keyboardTopYFlow.collect { keyboardTopY ->
                if (keyboardTopY != null && isKeyboardOnlyMode) {
                    adjustBubblePositionAboveKeyboard(keyboardTopY)
                }
            }
        }
        serviceScope.launch {
            preferencesManager.bubbleSizeDp.collect { sizeDp ->
                bubbleView?.updateBubbleSize(sizeDp)
            }
        }
        audioRecorder.onSilenceDetected = {
            if (bubbleView?.state == DictationState.LISTENING) {
                stopListeningAndProcess()
            }
        }
    }

    private fun updateBubbleVisibility(isKeyboardOpen: Boolean) {
        val bubble = bubbleView ?: return
        if (!isKeyboardOnlyMode) {
            bubble.showAnimated()
            return
        }

        if (isKeyboardOpen) {
            bubble.showAnimated()
        } else {
            if (bubble.state == DictationState.IDLE || bubble.state == DictationState.SUCCESS) {
                bubble.hideAnimated()
            }
        }
    }

    private fun adjustBubblePositionAboveKeyboard(keyboardTopY: Int) {
        val bubble = bubbleView ?: return
        val params = bubble.layoutParamsWindowManager ?: return
        val density = resources.displayMetrics.density
        val bubbleHeight = bubble.height.takeIf { it > 0 } ?: (72 * density).toInt()
        val targetY = (keyboardTopY - bubbleHeight - (12 * density).toInt()).coerceAtLeast(60)

        if (kotlin.math.abs(params.y - targetY) > 8) {
            params.y = targetY
            try {
                windowManager.updateViewLayout(bubble, params)
            } catch (e: Exception) {
                // Ignore layout update race
            }
        }
    }

    private fun handleBubbleClick() {
        val current = bubbleView?.state ?: DictationState.IDLE
        when (current) {
            DictationState.IDLE, DictationState.ERROR -> {
                startListening()
            }
            DictationState.LISTENING -> {
                stopListeningAndProcess()
            }
            DictationState.PROCESSING -> {
                // Ignore click during processing
            }
            DictationState.SUCCESS -> {
                bubbleView?.state = DictationState.IDLE
                if (isKeyboardOnlyMode && !FlowAccessibilityService.isKeyboardVisibleFlow.value) {
                    bubbleView?.hideAnimated()
                }
            }
        }
    }

    private fun startListening() {
        val started = audioRecorder.startRecording()
        if (!started) {
            bubbleView?.state = DictationState.ERROR
            serviceScope.launch {
                delay(1500)
                bubbleView?.state = DictationState.IDLE
                if (isKeyboardOnlyMode && !FlowAccessibilityService.isKeyboardVisibleFlow.value) {
                    bubbleView?.hideAnimated()
                }
            }
            return
        }

        bubbleView?.state = DictationState.LISTENING

        // Stream live amplitudes to visualizer
        amplitudeJob?.cancel()
        amplitudeJob = serviceScope.launch {
            audioRecorder.amplitudeFlow.collect { amplitude ->
                bubbleView?.setLiveAmplitude(amplitude)
            }
        }
    }

    private fun stopListeningAndProcess() {
        amplitudeJob?.cancel()
        bubbleView?.state = DictationState.PROCESSING

        serviceScope.launch {
            val audioFile = withContext(Dispatchers.IO) {
                audioRecorder.stopRecording()
            }

            if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
                bubbleView?.state = DictationState.ERROR
                delay(1200)
                bubbleView?.state = DictationState.IDLE
                if (isKeyboardOnlyMode && !FlowAccessibilityService.isKeyboardVisibleFlow.value) {
                    bubbleView?.hideAnimated()
                }
                return@launch
            }

            // Transcribe and format
            val result = transcriptionEngine.processAudioFile(audioFile)

            if (result.isSuccess) {
                val formattedText = result.getOrNull().orEmpty()
                if (formattedText.isNotBlank()) {
                    // Inject into focused app field via AccessibilityService
                    val a11yService = FlowAccessibilityService.getInstance()
                    val injected = a11yService?.injectText(formattedText) ?: false
                    Log.d(TAG, "Text injection result: $injected for: $formattedText")

                    bubbleView?.state = DictationState.SUCCESS
                    delay(1200)
                    bubbleView?.state = DictationState.IDLE
                    if (isKeyboardOnlyMode && !FlowAccessibilityService.isKeyboardVisibleFlow.value) {
                        bubbleView?.hideAnimated()
                    }
                } else {
                    bubbleView?.state = DictationState.IDLE
                    if (isKeyboardOnlyMode && !FlowAccessibilityService.isKeyboardVisibleFlow.value) {
                        bubbleView?.hideAnimated()
                    }
                }
            } else {
                Log.e(TAG, "Transcription failed", result.exceptionOrNull())
                bubbleView?.state = DictationState.ERROR
                delay(1500)
                bubbleView?.state = DictationState.IDLE
                if (isKeyboardOnlyMode && !FlowAccessibilityService.isKeyboardVisibleFlow.value) {
                    bubbleView?.hideAnimated()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        amplitudeJob?.cancel()
        serviceScope.cancel()
        audioRecorder.cancelRecording()

        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing bubble view", e)
            }
        }
        bubbleView = null
        Log.d(TAG, "FlowOverlayService destroyed")
    }
}
