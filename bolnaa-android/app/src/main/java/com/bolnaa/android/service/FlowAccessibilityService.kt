package com.bolnaa.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

class FlowAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FlowAccessibility"
        private var instanceRef: WeakReference<FlowAccessibilityService>? = null

        val isServiceRunning: Boolean
            get() = instanceRef?.get() != null

        fun getInstance(): FlowAccessibilityService? = instanceRef?.get()

        private val _isKeyboardVisibleFlow = MutableStateFlow(false)
        val isKeyboardVisibleFlow: StateFlow<Boolean> = _isKeyboardVisibleFlow.asStateFlow()

        private val _keyboardTopYFlow = MutableStateFlow<Int?>(null)
        val keyboardTopYFlow: StateFlow<Int?> = _keyboardTopYFlow.asStateFlow()
    }

    private var currentFocusedNode: AccessibilityNodeInfo? = null
    private var lastFocusedPackage: CharSequence? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        Log.d(TAG, "FlowAccessibilityService connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
        }
        serviceInfo = info
        ensureOverlayServiceRunning()
        checkKeyboardAndFocusState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val source = event.source
                if (source != null && source.isEditable) {
                    currentFocusedNode = source
                    lastFocusedPackage = event.packageName
                    Log.d(TAG, "Focused editable field in package: $lastFocusedPackage")
                }
                ensureOverlayServiceRunning()
                checkKeyboardAndFocusState()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                findAndCacheFocusedNode()
                checkKeyboardAndFocusState()
            }
        }
    }

    private fun ensureOverlayServiceRunning() {
        try {
            if (!FlowOverlayService.isRunning && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
                Log.d(TAG, "Ensuring FlowOverlayService is running from AccessibilityService")
                FlowOverlayService.start(this)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure FlowOverlayService running", e)
        }
    }

    private fun checkKeyboardAndFocusState() {
        try {
            var keyboardVisible = false
            var keyboardTopY: Int? = null

            // Inspect active system windows for InputMethod / Soft Keyboard
            val currentWindows = try { windows } catch (e: Exception) { null }
            if (!currentWindows.isNullOrEmpty()) {
                val screenHeight = resources.displayMetrics.heightPixels
                for (window in currentWindows) {
                    if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        val bounds = Rect()
                        window.getBoundsInScreen(bounds)
                        // A visible soft keyboard occupies substantial height at the bottom of the screen (typically > 120px)
                        if (bounds.height() > 120 && bounds.top < screenHeight && bounds.bottom >= screenHeight - 150) {
                            keyboardVisible = true
                            keyboardTopY = bounds.top
                            break
                        }
                    }
                }
            }

            if (keyboardVisible) {
                ensureOverlayServiceRunning()
            }

            if (_isKeyboardVisibleFlow.value != keyboardVisible) {
                _isKeyboardVisibleFlow.value = keyboardVisible
                Log.d(TAG, "Keyboard visibility changed: $keyboardVisible (Top Y: $keyboardTopY)")
            }
            _keyboardTopYFlow.value = keyboardTopY
        } catch (e: Exception) {
            Log.w(TAG, "Error checking keyboard state", e)
        }
    }

    private fun findAndCacheFocusedNode() {
        try {
            val root = rootInActiveWindow ?: return
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                currentFocusedNode = focused
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding focused node", e)
        }
    }

    /**
     * Injects transcribed text into the currently active input field across any app.
     */
    fun injectText(text: String): Boolean {
        if (text.isBlank()) return false

        // 1. Refresh active focused node
        findAndCacheFocusedNode()
        val targetNode = currentFocusedNode

        Log.d(TAG, "Attempting text injection. Node: $targetNode, isEditable: ${targetNode?.isEditable}")

        if (targetNode != null && targetNode.isEditable) {
            var existingText = targetNode.text?.toString() ?: ""

            // 1. Check if node is explicitly showing hint text (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && targetNode.isShowingHintText) {
                existingText = ""
            }

            // 2. Check if existingText matches hintText
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                targetNode.hintText?.toString()?.trim()
            } else null

            if (!hint.isNullOrEmpty() && existingText.trim().equals(hint, ignoreCase = true)) {
                existingText = ""
            }

            // 3. Filter out common placeholder texts across apps (WhatsApp "Message", "Type a message", etc.)
            val cleanExisting = existingText.trim().lowercase()
            val isPlaceholder = cleanExisting in setOf(
                "message", "messages", "type a message", "type a message...",
                "send a message", "send a message...", "write a message",
                "write a message...", "start a message", "text message",
                "search", "search...", "search or type web address",
                "write a reply", "write a reply...", "add a comment",
                "add a comment...", "comment...", "type something...", "ask a question..."
            )
            if (isPlaceholder) {
                existingText = ""
            }

            val arguments = Bundle().apply {
                val newText = if (existingText.isNotEmpty() && !existingText.endsWith(" ")) {
                    "$existingText $text"
                } else if (existingText.isNotEmpty()) {
                    "$existingText$text"
                } else {
                    text
                }
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText
                )
            }

            val setSuccess = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (setSuccess) {
                Log.d(TAG, "Successfully injected text via ACTION_SET_TEXT")
                return true
            }

            // Fallback: Try ACTION_PASTE via Clipboard
            copyToClipboard(text)
            val pasteSuccess = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (pasteSuccess) {
                Log.d(TAG, "Successfully injected text via ACTION_PASTE")
                return true
            }
        }

        // Fallback: Copy to system clipboard for easy manual paste
        copyToClipboard(text)
        Log.d(TAG, "Direct injection fallback, copied to clipboard")
        return true
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Bolnaa Voice Typing", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onInterrupt() {
        Log.w(TAG, "FlowAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceRef = null
        currentFocusedNode = null
        _isKeyboardVisibleFlow.value = false
        _keyboardTopYFlow.value = null
        Log.d(TAG, "FlowAccessibilityService destroyed")
    }
}
