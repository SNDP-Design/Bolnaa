package com.bolnaa.android.ui.screens

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolnaa.android.data.PreferencesManager
import com.bolnaa.android.data.models.FlowTone
import com.bolnaa.android.data.models.SttEngine
import com.bolnaa.android.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    preferencesManager: PreferencesManager,
    isOverlayPermissionGranted: Boolean,
    isAccessibilityPermissionGranted: Boolean,
    isMicPermissionGranted: Boolean,
    onOpenSetupWizard: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Preferences state
    val groqKey by preferencesManager.groqApiKey.collectAsState(initial = "")
    val isAiCleanupEnabled by preferencesManager.isAiCleanupEnabled.collectAsState(initial = true)
    val isAutoStopSilence by preferencesManager.isAutoStopSilence.collectAsState(initial = true)
    val silenceTimeoutMs by preferencesManager.silenceTimeoutMs.collectAsState(initial = 1600)
    val bubbleSizeDp by preferencesManager.bubbleSizeDp.collectAsState(initial = 58)
    val isFreePlacement by preferencesManager.isFreePlacementEnabled.collectAsState(initial = true)
    val isHapticsEnabled by preferencesManager.isHapticFeedbackEnabled.collectAsState(initial = true)
    val customVocab by preferencesManager.customVocabulary.collectAsState(initial = "")

    // Ensure Groq Whisper and Natural Tone are always enforced as defaults
    LaunchedEffect(Unit) {
        preferencesManager.setSttEngine(SttEngine.GROQ)
        preferencesManager.setFlowTone(FlowTone.NATURAL)
        preferencesManager.setAttachToKeyboardEnabled(true)
    }

    // Local UI state for text fields
    var groqInput by remember(groqKey) { mutableStateOf(groqKey) }
    var isGroqKeyVisible by remember { mutableStateOf(false) }
    var customVocabInput by remember(customVocab) { mutableStateOf(customVocab) }

    val allPermissionsGranted = isOverlayPermissionGranted && isAccessibilityPermissionGranted && isMicPermissionGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // --- Top Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
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

        Spacer(modifier = Modifier.height(20.dp))

        // --- Permissions Warning (if any missing) ---
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
                            text = "Permissions Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = FlowWarning
                        )
                        Text(
                            text = "Tap to grant Overlay & Accessibility permissions for auto-paste.",
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ==========================================
        // 1. FLOATING BUBBLE CUSTOMIZATION
        // ==========================================
        Text(
            text = "Floating Bubble Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = FlowTextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FlowSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, FlowBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Bubble Size Slider & Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bubble Size", style = MaterialTheme.typography.titleMedium, color = FlowTextPrimary)
                        Text("Live dimensions: ${bubbleSizeDp}dp", style = MaterialTheme.typography.bodyMedium, color = FlowTextSecondary)
                    }
                    // Visual circular preview badge
                    Box(
                        modifier = Modifier
                            .size((bubbleSizeDp * 0.6f).dp.coerceIn(26.dp, 48.dp))
                            .clip(CircleShape)
                            .background(FlowPrimary.copy(alpha = 0.25f))
                            .border(1.5.dp, FlowPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Preset chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Small" to 46,
                        "Medium" to 56,
                        "Large" to 66,
                        "X-Large" to 76
                    ).forEach { (label, size) ->
                        val isSelected = bubbleSizeDp == size
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    coroutineScope.launch { preferencesManager.setBubbleSizeDp(size) }
                                }
                                .border(
                                    1.dp,
                                    if (isSelected) FlowPrimary else FlowBorder,
                                    RoundedCornerShape(10.dp)
                                ),
                            color = if (isSelected) FlowPrimary.copy(alpha = 0.2f) else FlowSurfaceVariant
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) FlowPrimary else FlowTextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = bubbleSizeDp.toFloat(),
                    onValueChange = {
                        coroutineScope.launch { preferencesManager.setBubbleSizeDp(it.toInt()) }
                    },
                    valueRange = 42f..80f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = FlowPrimary,
                        activeTrackColor = FlowPrimary,
                        inactiveTrackColor = FlowSurfaceVariant
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = FlowBorder)

                // Free Placement anywhere toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Place Anywhere on Screen", style = MaterialTheme.typography.titleMedium, color = FlowTextPrimary)
                        Text("Drag and drop the bubble anywhere freely without forced edge-snapping", style = MaterialTheme.typography.bodyMedium, color = FlowTextSecondary)
                    }
                    Switch(
                        checked = isFreePlacement,
                        onCheckedChange = { coroutineScope.launch { preferencesManager.setFreePlacementEnabled(it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = FlowPrimary)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = FlowBorder)

                // Haptic feedback toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Feedback", style = MaterialTheme.typography.titleMedium, color = FlowTextPrimary)
                        Text("Gently vibrate on start, stop, and paste", style = MaterialTheme.typography.bodyMedium, color = FlowTextSecondary)
                    }
                    Switch(
                        checked = isHapticsEnabled,
                        onCheckedChange = { coroutineScope.launch { preferencesManager.setHapticFeedbackEnabled(it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = FlowPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 2. GROQ WHISPER API KEY
        // ==========================================
        Text(
            text = "Groq Whisper API Key",
            style = MaterialTheme.typography.titleMedium,
            color = FlowTextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FlowSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, FlowBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡ Groq Whisper Large v3 (Ultra-Fast ~300ms)",
                        style = MaterialTheme.typography.titleSmall,
                        color = FlowSuccess,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "High-accuracy, sub-second voice transcription powered by Groq LPU.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FlowTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Groq API Key Input
                OutlinedTextField(
                    value = groqInput,
                    onValueChange = {
                        groqInput = it
                        coroutineScope.launch { preferencesManager.setGroqApiKey(it) }
                    },
                    label = { Text("Groq API Key") },
                    placeholder = { Text("gsk_...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (isGroqKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isGroqKeyVisible = !isGroqKeyVisible }) {
                            Icon(
                                imageVector = if (isGroqKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = FlowTextMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FlowPrimary,
                        unfocusedBorderColor = FlowBorder,
                        focusedTextColor = FlowTextPrimary,
                        unfocusedTextColor = FlowTextPrimary,
                        focusedContainerColor = FlowSurfaceVariant,
                        unfocusedContainerColor = FlowSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Get your free API key at console.groq.com",
                    style = MaterialTheme.typography.labelSmall,
                    color = FlowTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 3. AI SMART CLEAN-UP & VOCABULARY
        // ==========================================
        Text(
            text = "AI Smart Clean-up & Audio",
            style = MaterialTheme.typography.titleMedium,
            color = FlowTextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = FlowSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, FlowBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // AI Cleanup Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bolnaa Smart AI Clean-up", style = MaterialTheme.typography.titleMedium, color = FlowTextPrimary)
                        Text("Removes 'um/uh', fixes grammar, and polishes voice flow naturally", style = MaterialTheme.typography.bodyMedium, color = FlowTextSecondary)
                    }
                    Switch(
                        checked = isAiCleanupEnabled,
                        onCheckedChange = { coroutineScope.launch { preferencesManager.setAiCleanupEnabled(it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = FlowPrimary)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = FlowBorder)

                // Custom vocabulary / dictionary
                Text("Custom Vocabulary & Acronyms:", style = MaterialTheme.typography.labelMedium, color = FlowTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = customVocabInput,
                    onValueChange = {
                        customVocabInput = it
                        coroutineScope.launch { preferencesManager.setCustomVocabulary(it) }
                    },
                    placeholder = { Text("e.g., Kubernetes, SwiftUI, SNDP, Bolnaa") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FlowPrimary,
                        unfocusedBorderColor = FlowBorder,
                        focusedTextColor = FlowTextPrimary,
                        unfocusedTextColor = FlowTextPrimary,
                        focusedContainerColor = FlowSurfaceVariant,
                        unfocusedContainerColor = FlowSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = FlowBorder)

                // Auto-stop silence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Stop on Silence", style = MaterialTheme.typography.titleMedium, color = FlowTextPrimary)
                        Text("Automatically finishes dictation when you pause talking", style = MaterialTheme.typography.bodyMedium, color = FlowTextSecondary)
                    }
                    Switch(
                        checked = isAutoStopSilence,
                        onCheckedChange = { coroutineScope.launch { preferencesManager.setAutoStopSilence(it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = FlowPrimary)
                    )
                }

                if (isAutoStopSilence) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Silence Pause Duration: ${silenceTimeoutMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = FlowTextSecondary
                    )
                    Slider(
                        value = silenceTimeoutMs.toFloat(),
                        onValueChange = {
                            coroutineScope.launch { preferencesManager.setSilenceTimeoutMs(it.toInt()) }
                        },
                        valueRange = 800f..3000f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = FlowPrimary,
                            activeTrackColor = FlowPrimary,
                            inactiveTrackColor = FlowSurfaceVariant
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
