package com.wisprflow.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

class FlowAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FlowAccessibility"
        private var instanceRef: WeakReference<FlowAccessibilityService>? = null

        val isServiceRunning: Boolean
            get() = instanceRef?.get() != null

        fun getInstance(): FlowAccessibilityService? = instanceRef?.get()
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
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
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
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // If keyboard or window changes, check active focus root
                findAndCacheFocusedNode()
            }
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
            // Check if node supports ACTION_SET_TEXT
            val existingText = targetNode.text?.toString() ?: ""
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
        Log.d(TAG, "Direct injection failed, copied to clipboard as fallback")
        return true
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Whisper Flow Dictation", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onInterrupt() {
        Log.w(TAG, "FlowAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceRef = null
        currentFocusedNode = null
        Log.d(TAG, "FlowAccessibilityService destroyed")
    }
}
