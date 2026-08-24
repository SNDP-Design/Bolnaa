package com.bolnaa.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.bolnaa.android.data.PreferencesManager
import com.bolnaa.android.service.FlowAccessibilityService
import com.bolnaa.android.ui.screens.DashboardScreen
import com.bolnaa.android.ui.screens.SetupWizardScreen
import com.bolnaa.android.ui.theme.FlowBg
import com.bolnaa.android.ui.theme.BolnaaTheme
import kotlinx.coroutines.launch

enum class Screen {
    DASHBOARD,
    SETUP_WIZARD
}

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    private var hasMicPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var isOverlayRunning by mutableStateOf(false)

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (!isGranted) {
            Toast.makeText(this, "Microphone permission is required for voice dictation", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)

        updatePermissionStates()

        setContent {
            BolnaaTheme {
                val updateManager = remember { com.bolnaa.android.updater.AppUpdateManager(this@MainActivity) }
                val updateStatus by updateManager.updateStatus.collectAsState()

                LaunchedEffect(Unit) {
                    updateManager.checkForUpdates()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FlowBg
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
                    val coroutineScope = rememberCoroutineScope()

                    AnimatedContent(
                        targetState = currentScreen,
                        label = "screen_navigation",
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        }
                    ) { screen ->
                        when (screen) {
                            Screen.DASHBOARD -> {
                                DashboardScreen(
                                    preferencesManager = preferencesManager,
                                    isOverlayPermissionGranted = hasOverlayPermission,
                                    isAccessibilityPermissionGranted = hasAccessibilityPermission,
                                    isMicPermissionGranted = hasMicPermission,
                                    onOpenSetupWizard = { currentScreen = Screen.SETUP_WIZARD }
                                )
                            }
                            Screen.SETUP_WIZARD -> {
                                SetupWizardScreen(
                                    isOverlayPermissionGranted = hasOverlayPermission,
                                    isAccessibilityPermissionGranted = hasAccessibilityPermission,
                                    isMicPermissionGranted = hasMicPermission,
                                    onRequestMicPermission = { requestMicPermission() },
                                    onRequestOverlayPermission = { requestOverlayPermission() },
                                    onRequestAccessibilityPermission = { requestAccessibilityPermission() },
                                    onBack = { currentScreen = Screen.DASHBOARD },
                                    onFinish = {
                                        startOverlayService()
                                        currentScreen = Screen.DASHBOARD
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
        if (hasOverlayPermission && hasMicPermission && hasAccessibilityPermission && !isOverlayRunning) {
            startOverlayService()
        }
    }

    private fun updatePermissionStates() {
        hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        hasAccessibilityPermission = isAccessibilityServiceEnabled(this, FlowAccessibilityService::class.java)
        isOverlayRunning = FlowOverlayService.isRunning
    }

    private fun requestMicPermission() {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Enable 'Bolnaa' in Downloaded / Installed apps", Toast.LENGTH_LONG).show()
    }

    private fun startOverlayService() {
        if (!hasOverlayPermission) {
            requestOverlayPermission()
            return
        }
        if (!hasMicPermission) {
            requestMicPermission()
            return
        }

        FlowOverlayService.start(this)
        isOverlayRunning = true
    }

    private fun stopOverlayService() {
        FlowOverlayService.stop(this)
        isOverlayRunning = false
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${serviceClass.name}"
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            if (componentNameString.equals(expectedComponentName, ignoreCase = true) ||
                componentNameString.contains(serviceClass.simpleName)
            ) {
                return true
            }
        }
        return false
    }
}
