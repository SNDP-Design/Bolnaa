package com.bolnaa.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bolnaa.android.data.PreferencesManager
import com.bolnaa.android.service.FlowAccessibilityService
import com.bolnaa.android.service.FlowOverlayService
import com.bolnaa.android.ui.screens.DashboardScreen
import com.bolnaa.android.ui.screens.SetupWizardScreen
import com.bolnaa.android.ui.theme.FlowBg
import com.bolnaa.android.ui.theme.BolnaaTheme
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.first

enum class Screen {
    DASHBOARD,
    SETUP_WIZARD
}

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    private var hasMicPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var hasBatteryOptimizationExempt by mutableStateOf(false)
    private var hasAutostartVisited by mutableStateOf(false)
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
                val isSetupCompleted by preferencesManager.isSetupCompleted.collectAsState(initial = true)
                val isAutostartConfiguredPref by preferencesManager.isAutostartConfigured.collectAsState(initial = false)
                val coroutineScope = rememberCoroutineScope()

                val isAutostartEffective = hasAutostartVisited || isAutostartConfiguredPref

                LaunchedEffect(Unit) {
                    updateManager.checkForUpdates()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FlowBg
                ) {
                    val initialScreen = if (!isSetupCompleted && (!hasOverlayPermission || !hasAccessibilityPermission || !hasMicPermission)) {
                        Screen.SETUP_WIZARD
                    } else {
                        Screen.DASHBOARD
                    }

                    var currentScreen by rememberSaveable { mutableStateOf(initialScreen) }

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
                                    isBatteryOptimizationExempt = hasBatteryOptimizationExempt,
                                    isAutostartConfigured = isAutostartEffective,
                                    onOpenSetupWizard = { currentScreen = Screen.SETUP_WIZARD },
                                    onStartService = { startOverlayService() },
                                    onStopService = { stopOverlayService() }
                                )
                            }
                            Screen.SETUP_WIZARD -> {
                                SetupWizardScreen(
                                    isOverlayPermissionGranted = hasOverlayPermission,
                                    isAccessibilityPermissionGranted = hasAccessibilityPermission,
                                    isMicPermissionGranted = hasMicPermission,
                                    isBatteryOptimizationExempt = hasBatteryOptimizationExempt,
                                    isAutostartConfigured = isAutostartEffective,
                                    onRequestMicPermission = { requestMicPermission() },
                                    onRequestOverlayPermission = { requestOverlayPermission() },
                                    onRequestAccessibilityPermission = { requestAccessibilityPermission() },
                                    onRequestBatteryOptimization = { requestBatteryOptimization() },
                                    onOpenAutostartSettings = { openAutostartSettings() },
                                    onBack = { currentScreen = Screen.DASHBOARD },
                                    onFinish = {
                                        coroutineScope.launch {
                                            preferencesManager.setSetupCompleted(true)
                                            preferencesManager.setServiceActive(true)
                                        }
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
        lifecycleScope.launch {
            val isServiceActive = preferencesManager.isServiceActive.first()
            if (isServiceActive && hasOverlayPermission && hasMicPermission && hasAccessibilityPermission && !isOverlayRunning) {
                startOverlayService()
            }
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            hasBatteryOptimizationExempt = powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
        } else {
            hasBatteryOptimizationExempt = true
        }

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

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "Open App Info -> Battery -> Set to 'No restrictions'", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openAutostartSettings() {
        hasAutostartVisited = true
        lifecycleScope.launch {
            preferencesManager.setAutostartConfigured(true)
        }
        val intentList = listOf(
            // Xiaomi / MIUI / HyperOS
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            // Huawei / Honor
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")),
            // Oppo / Realme
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            // Vivo
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            // Samsung
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
        )

        var started = false
        for (intent in intentList) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                started = true
                Toast.makeText(this, "Enable 'Bolnaa' in Autostart to stay active in background", Toast.LENGTH_LONG).show()
                break
            } catch (e: Exception) {
                // Try next candidate
            }
        }

        if (!started) {
            try {
                val appSettingsIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                startActivity(appSettingsIntent)
                Toast.makeText(this, "Enable Autostart & Background Activity for Bolnaa", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open system autostart settings", Toast.LENGTH_SHORT).show()
            }
        }
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
        if (!hasAccessibilityPermission) {
            requestAccessibilityPermission()
            return
        }

        FlowOverlayService.start(this)
        isOverlayRunning = true
    }

    private fun stopOverlayService() {
        FlowOverlayService.stop(this)
        isOverlayRunning = false
        FlowAccessibilityService.disableService()
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
