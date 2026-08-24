package com.bolnaa.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bolnaa.android.ui.components.PermissionCard
import com.bolnaa.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    isOverlayPermissionGranted: Boolean,
    isAccessibilityPermissionGranted: Boolean,
    isMicPermissionGranted: Boolean,
    isBatteryOptimizationExempt: Boolean,
    isAutostartConfigured: Boolean,
    onRequestMicPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val scrollState = rememberScrollState()
    val corePermissionsGranted = isOverlayPermissionGranted && isAccessibilityPermissionGranted && isMicPermissionGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions & Reliability", color = FlowTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = FlowTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FlowBg)
            )
        },
        containerColor = FlowBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "100% Uninterrupted Setup",
                style = MaterialTheme.typography.headlineMedium,
                color = FlowTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Configure these settings to ensure Bolnaa instantly floats above your keyboard and remains active even after killing recent apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = FlowTextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 1: CORE PERMISSIONS
            Text(
                text = "CORE PERMISSIONS",
                style = MaterialTheme.typography.labelMedium,
                color = FlowPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Microphone Permission
            PermissionCard(
                title = "1. Microphone Access",
                description = "Required to capture crisp audio for high-speed AI dictation.",
                icon = Icons.Default.Mic,
                isGranted = isMicPermissionGranted,
                onGrantClick = onRequestMicPermission,
                buttonText = "Grant"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Display Over Other Apps (Overlay)
            PermissionCard(
                title = "2. Floating Bubble Overlay",
                description = "Allows the Bolnaa bubble to hover right above your keyboard.",
                icon = Icons.Default.Layers,
                isGranted = isOverlayPermissionGranted,
                onGrantClick = onRequestOverlayPermission,
                buttonText = "Grant"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Accessibility Service
            PermissionCard(
                title = "3. Auto Text Paste & Instant Wakeup",
                description = "Detects your keyboard opening and automatically types text into active input fields.",
                icon = Icons.Default.AccessibilityNew,
                isGranted = isAccessibilityPermissionGranted,
                onGrantClick = onRequestAccessibilityPermission,
                buttonText = "Enable"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 2: BACKGROUND PERSISTENCE & TASK KILLER PROTECTION
            Text(
                text = "BACKGROUND RELIABILITY (RECOMMENDED)",
                style = MaterialTheme.typography.labelMedium,
                color = FlowPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 4. Autostart Permission
            PermissionCard(
                title = "4. Autostart in Background",
                description = "Essential for Xiaomi / HyperOS, Samsung, and OnePlus to keep Bolnaa alive in memory.",
                icon = Icons.Default.FlashOn,
                isGranted = isAutostartConfigured,
                onGrantClick = onOpenAutostartSettings,
                buttonText = "Open"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Battery Saver Exemption
            PermissionCard(
                title = "5. Battery Saver: No Restrictions",
                description = "Prevents Android from freezing voice dictation when the phone is locked or idle.",
                icon = Icons.Default.BatteryChargingFull,
                isGranted = isBatteryOptimizationExempt,
                onGrantClick = onRequestBatteryOptimization,
                buttonText = "Disable"
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 6. Pro-Tip Card: Lock in Recent Apps
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = FlowPrimary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                color = FlowSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(FlowPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Bolnaa",
                            tint = FlowPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Pro-Tip: Lock Bolnaa in Recent Apps",
                            style = MaterialTheme.typography.titleMedium,
                            color = FlowTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Open your Recent Apps (Task Manager).\n2. Pull down or hold the Bolnaa card.\n3. Tap the 🔒 Lock icon.\n\nThis completely shields Bolnaa from the system 'Clear All' button.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FlowTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Finish Button
            Button(
                onClick = onFinish,
                enabled = corePermissionsGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlowPrimary,
                    disabledContainerColor = FlowSurfaceVariant,
                    contentColor = Color.White,
                    disabledContentColor = FlowTextMuted
                )
            ) {
                Text(
                    text = if (corePermissionsGranted) "All Set! Launch Bolnaa" else "Please Grant Core Permissions Above",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
