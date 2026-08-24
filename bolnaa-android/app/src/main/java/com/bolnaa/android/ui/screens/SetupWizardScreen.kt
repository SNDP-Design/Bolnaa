package com.bolnaa.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    onRequestMicPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val scrollState = rememberScrollState()
    val allGranted = isOverlayPermissionGranted && isAccessibilityPermissionGranted && isMicPermissionGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions & Setup", color = FlowTextPrimary) },
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
                text = "Enable Bolnaa Powers",
                style = MaterialTheme.typography.headlineMedium,
                color = FlowTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Bolnaa requires these permissions to float above your keyboard, capture voice input, and automatically insert formatted text into your apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = FlowTextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Microphone Permission
            PermissionCard(
                title = "1. Microphone Access",
                description = "Required to capture your voice dictation with high audio fidelity.",
                icon = Icons.Default.Mic,
                isGranted = isMicPermissionGranted,
                onGrantClick = onRequestMicPermission
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Display Over Other Apps (Overlay)
            PermissionCard(
                title = "2. Floating Bubble Overlay",
                description = "Allows the Bolnaa bubble to hover above your keyboard and apps.",
                icon = Icons.Default.Layers,
                isGranted = isOverlayPermissionGranted,
                onGrantClick = onRequestOverlayPermission
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Accessibility Service
            PermissionCard(
                title = "3. Auto Text Insertion (Accessibility)",
                description = "Enables Bolnaa to detect active input fields and automatically paste dictated text.",
                icon = Icons.Default.AccessibilityNew,
                isGranted = isAccessibilityPermissionGranted,
                onGrantClick = onRequestAccessibilityPermission
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onFinish,
                enabled = allGranted,
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
                    text = if (allGranted) "All Set! Launch Bolnaa" else "Please Grant Permissions Above",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
