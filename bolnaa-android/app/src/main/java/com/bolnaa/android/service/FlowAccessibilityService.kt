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
import com.bolnaa.android.data.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

        // Known UPI, Payment & Banking package identifiers in India / Global
        private val FINANCIAL_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                        // PhonePe
            "net.one97.paytm",                        // Paytm
            "in.org.npci.upiapp",                     // BHIM UPI
            "com.sbi.lotusintouch",                   // YONO SBI
            "com.sbi.SBIFreedomPlus",                 // YONO Lite SBI
            "com.snapwork.hdfc",                      // HDFC MobileBanking
            "com.hdfcbank.payzapp",                   // HDFC PayZapp
            "com.csam.icici.bank.imobile",            // ICICI iMobile Pay
            "com.axis.mobile",                        // Axis Mobile
            "com.kotak.omni",                         // Kotak 811
            "com.dreamplug.androidapp",               // CRED
            "com.finopaytech.bpay",                   // BPay
            "com.msf.kbank.mobile",                   // KBank
            "com.zerodha.kite3",                      // Zerodha Kite
            "com.groww",                              // Groww
            "com.angelprime",                         // Angel One
            "com.mobikwik_new",                       // MobiKwik
            "com.freecharge.android"                  // Freecharge
        )

        fun isFinancialApp(packageName: String?): Boolean {
            if (packageName.isNullOrBlank()) return false
            val pkg = packageName.lowercase()
            if (FINANCIAL_PACKAGES.contains(pkg)) return true
            return (pkg.contains("bank") || pkg.contains("upi") || pkg.contains("paytm") ||
                    pkg.contains("phonepe") || pkg.contains("paisa") || pkg.contains("wallet") ||
                    pkg.contains(".payment") || pkg.contains("yono")) &&
                    !pkg.contains("com.bolnaa")
        }

        fun disableService() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    instanceRef?.get()?.disableSelf()
                    Log.d(TAG, "FlowAccessibilityService disableSelf called successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disableSelf", e)
            }
        }
    }

    private lateinit var preferencesManager: PreferencesManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isServiceActive = true
    private var isAutoPauseFinancialApps = true
    private var currentFocusedNode: AccessibilityNodeInfo? = null
    private var lastFocusedPackage: CharSequence? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        preferencesManager = PreferencesManager(this)
        Log.d(TAG, "FlowAccessibilityService connected")

        serviceScope.launch {
            preferencesManager.isServiceActive.collect { active ->
                isServiceActive = active
                if (active) {
                    ensureOverlayServiceRunning()
                }
                // Note: disableSelf() is called externally via disableService()
                // from stopOverlayService() in MainActivity/TileService BEFORE the
                // preference is saved, to avoid a race condition.
            }
        }
        serviceScope.launch {
            preferencesManager.isAutoPauseFinancialApps.collect { autoPause ->
                isAutoPauseFinancialApps = autoPause
            }
        }

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
        if (isServiceActive) {
            ensureOverlayServiceRunning()
        }
        checkKeyboardAndFocusState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString()
        if (pkgName != null) {
            val isFinancial = isFinancialApp(pkgName)
            if (isAutoPauseFinancialApps) {
                FlowOverlayService.setFinancialAppActive(isFinancial)
            }
        }

        if (!isServiceActive) {
            return
        }

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
        if (!isServiceActive) return
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
        serviceScope.cancel()
        instanceRef = null
        currentFocusedNode = null
        _isKeyboardVisibleFlow.value = false
        _keyboardTopYFlow.value = null
        Log.d(TAG, "FlowAccessibilityService destroyed")
    }
}
