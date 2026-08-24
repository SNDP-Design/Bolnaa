package com.bolnaa.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolnaa.android.data.PreferencesManager
import com.bolnaa.android.data.models.FlowTone
import com.bolnaa.android.data.models.SttEngine
import com.bolnaa.android.ui.components.WaveformPreview
import com.bolnaa.android.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    preferencesManager: PreferencesManager,
    isOverlayPermissionGranted: Boolean,
    isAccessibilityPermissionGranted: Boolean,
    isMicPermissionGranted: Boolean,
    isOverlayServiceRunning: Boolean,
    onToggleService: (Boolean) -> Unit,
    onOpenSetupWizard: () -> Unit,
    onOpenPlayground: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val selectedEngine by preferencesManager.sttEngine.collectAsState(initial = SttEngine.GROQ)
    val selectedTone by preferencesManager.flowTone.collectAsState(initial = FlowTone.NATURAL)
    val groqKey by preferencesManager.groqApiKey.collectAsState(initial = "")
    val openAiKey by preferencesManager.openAiApiKey.collectAsState(initial = "")

    val allPermissionsGranted = isOverlayPermissionGranted && isAccessibilityPermissionGranted && isMicPermissionGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(FlowPrimary, FlowAccent)),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Bolnaa",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FlowTextPrimary
                    )
                    Text(
                        text = "AI Voice Dictation & Auto-Paste",
                        style = MaterialTheme.typography.labelSmall,
                        color = FlowTextMuted
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .background(FlowSurfaceVariant, CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = FlowTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Permissions Banner (if missing any)
        if (!allPermissionsGranted) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenSetupWizard() }
                    .border(1.dp, FlowWarning.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                color = FlowSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = FlowWarning,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Setup Incomplete",
                            style = MaterialTheme.typography.titleMedium,
                            color = FlowWarning
                        )
                        Text(
                            text = "Grant Overlay & Accessibility permissions to enable auto-paste.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FlowTextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = FlowTextMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Live Auto-Update Status Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FlowAccent.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = FlowSurfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(FlowAccent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = FlowAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Live OTA Updates: Active",
                            style = MaterialTheme.typography.titleSmall,
                            color = FlowTextPrimary
                        )
                        Text(
                            text = "Auto-syncs changes without manual reinstall",
                            style = MaterialTheme.typography.labelSmall,
                            color = FlowTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Master Service Card (Bolnaa Floating Bubble ON/OFF)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FlowBorder, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = FlowSurface
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Floating Bolnaa Bubble",
                            style = MaterialTheme.typography.titleLarge,
                            color = FlowTextPrimary
                        )
                        Text(
                            text = if (isOverlayServiceRunning) "Bubble active over all apps" else "Turn on to show bubble",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOverlayServiceRunning) FlowSuccess else FlowTextMuted
                        )
                    }

                    Switch(
                        checked = isOverlayServiceRunning,
                        onCheckedChange = { isChecked ->
                            if (isChecked && !allPermissionsGranted) {
                                onOpenSetupWizard()
                            } else {
                                onToggleService(isChecked)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = FlowPrimary,
                            uncheckedThumbColor = FlowTextMuted,
                            uncheckedTrackColor = FlowSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Waveform illustration
                WaveformPreview(
                    isListening = isOverlayServiceRunning,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "💡 Tip: Tap the floating bubble anywhere to speak. It transcribes, removes 'um/uh', and pastes directly into your focused chat or doc!",
                    style = MaterialTheme.typography.labelSmall,
                    color = FlowTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Flow AI Tone Mode Selector
        Text(
            text = "AI Formatting Tone",
            style = MaterialTheme.typography.titleMedium,
            color = FlowTextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowTone.values().forEach { tone ->
                val isSelected = tone == selectedTone
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            coroutineScope.launch { preferencesManager.setFlowTone(tone) }
                        }
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) FlowPrimary else FlowBorder,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    color = if (isSelected) FlowPrimary.copy(alpha = 0.1f) else FlowSurface
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch { preferencesManager.setFlowTone(tone) }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = FlowPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = tone.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) FlowPrimaryLight else FlowTextPrimary
                            )
                            Text(
                                text = tone.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = FlowTextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Playground Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onOpenPlayground() }
                .border(1.dp, FlowAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            color = FlowSurfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(FlowAccent.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = FlowAccent
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interactive Playground",
                        style = MaterialTheme.typography.titleMedium,
                        color = FlowTextPrimary
                    )
                    Text(
                        text = "Test dictation, waveform, & AI cleaning right here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlowTextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = FlowTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}
