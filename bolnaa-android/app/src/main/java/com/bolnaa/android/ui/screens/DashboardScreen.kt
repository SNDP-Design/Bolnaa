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

    // Ensure defaults are permanently enforced
    LaunchedEffect(Unit) {
        preferencesManager.setSttEngine(SttEngine.GROQ)
        preferencesManager.setFlowTone(FlowTone.NATURAL)
        preferencesManager.setAttachToKeyboardEnabled(true)
        preferencesManager.setBubbleSizeDp(72)
        preferencesManager.setFreePlacementEnabled(true)
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
            BolnaaLogoIcon(modifier = Modifier.size(46.dp))
            Spacer(modifier = Modifier.width(14.dp))
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
        // 1. GROQ WHISPER API KEY
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

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun BolnaaLogoIcon(modifier: Modifier = Modifier.size(46.dp)) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cornerRadius = h * 0.28f

        // Squircle gradient background
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF7C3AED), Color(0xFF6366F1), Color(0xFF4338CA)),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(w, h)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
        )

        // Subtle glowing border
        drawRoundRect(
            color = Color(0xFFA5B4FC).copy(alpha = 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
        )

        val cx = w / 2f
        val cy = h / 2f
        val scale = w / 108f

        // Microphone capsule
        val micW = 16f * scale
        val micH = 27f * scale
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(cx - micW / 2, cy - micH / 2 - 3 * scale),
            size = androidx.compose.ui.geometry.Size(micW, micH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(micW / 2, micW / 2)
        )

        // U-Cradle
        val strokeWidth = 3.2f * scale
        val cradlePath = androidx.compose.ui.graphics.Path().apply {
            val cradleR = 13f * scale
            addArc(
                oval = androidx.compose.ui.geometry.Rect(cx - cradleR, cy - cradleR - 2 * scale, cx + cradleR, cy + cradleR - 2 * scale),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f
            )
        }
        drawPath(
            path = cradlePath,
            color = Color.White,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )

        // Stand & base
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(cx, cy + 11f * scale),
            end = androidx.compose.ui.geometry.Offset(cx, cy + 20f * scale),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(cx - 8f * scale, cy + 20f * scale),
            end = androidx.compose.ui.geometry.Offset(cx + 8f * scale, cy + 20f * scale),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Left soundwave arc
        val leftWave = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - 21f * scale, cy - 8f * scale)
            quadraticBezierTo(
                cx - 25f * scale, cy + 1f * scale,
                cx - 21f * scale, cy + 10f * scale
            )
        }
        drawPath(
            path = leftWave,
            color = Color.White,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )

        // Right soundwave arc
        val rightWave = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx + 21f * scale, cy - 8f * scale)
            quadraticBezierTo(
                cx + 25f * scale, cy + 1f * scale,
                cx + 21f * scale, cy + 10f * scale
            )
        }
        drawPath(
            path = rightWave,
            color = Color.White,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}
