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
    isBatteryOptimizationExempt: Boolean,
    isAutostartConfigured: Boolean,
    onOpenSetupWizard: () -> Unit,
    onStartService: () -> Unit = {},
    onStopService: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Preferences state
    val isServiceActive by preferencesManager.isServiceActive.collectAsState(initial = true)
    val isAutoPauseFinancialApps by preferencesManager.isAutoPauseFinancialApps.collectAsState(initial = true)
    val groqKey by preferencesManager.groqApiKey.collectAsState(initial = "")
    val isAiCleanupEnabled by preferencesManager.isAiCleanupEnabled.collectAsState(initial = true)
    val isAutoStopSilence by preferencesManager.isAutoStopSilence.collectAsState(initial = true)
    val silenceTimeoutMs by preferencesManager.silenceTimeoutMs.collectAsState(initial = 1600)
    val bubbleSizeDp by preferencesManager.bubbleSizeDp.collectAsState(initial = 64)
    val isFreePlacement by preferencesManager.isFreePlacementEnabled.collectAsState(initial = true)
    val isHapticsEnabled by preferencesManager.isHapticFeedbackEnabled.collectAsState(initial = true)
    val customVocab by preferencesManager.customVocabulary.collectAsState(initial = "")

    // Ensure defaults are permanently enforced
    LaunchedEffect(Unit) {
        preferencesManager.setSttEngine(SttEngine.GROQ)
        preferencesManager.setFlowTone(FlowTone.NATURAL)
        preferencesManager.setAttachToKeyboardEnabled(true)
        preferencesManager.setBubbleSizeDp(64)
        preferencesManager.setFreePlacementEnabled(true)
    }

    // Local UI state for text fields
    var groqInput by remember(groqKey) { mutableStateOf(groqKey) }
    var isGroqKeyVisible by remember { mutableStateOf(false) }
    var customVocabInput by remember(customVocab) { mutableStateOf(customVocab) }

    val corePermissionsGranted = isOverlayPermissionGranted && isAccessibilityPermissionGranted && isMicPermissionGranted
    val totalConfigured = listOf(
        isMicPermissionGranted,
        isOverlayPermissionGranted,
        isAccessibilityPermissionGranted,
        isAutostartConfigured,
        isBatteryOptimizationExempt
    ).count { it }

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

        Spacer(modifier = Modifier.height(18.dp))

        // --- Master Pause / Active Switch for Financial Apps ---
        MasterServiceSwitchCard(
            isServiceActive = isServiceActive,
            onToggle = { active ->
                coroutineScope.launch {
                    preferencesManager.setServiceActive(active)
                }
                if (active) {
                    onStartService()
                } else {
                    onStopService()
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Permanent Permissions & 100% Reliability Status Card ---
        if (totalConfigured < 5) {
            PermissionsIncompleteCard(
                totalConfigured = totalConfigured,
                corePermissionsGranted = corePermissionsGranted,
                onOpenSetupWizard = onOpenSetupWizard
            )
        } else {
            PermissionsActiveCard(
                onOpenSetupWizard = onOpenSetupWizard
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

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
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            checkedBorderColor = Color.White,
                            uncheckedThumbColor = Color(0xFFA3A3A3),
                            uncheckedTrackColor = Color(0xFF1E1E1E),
                            uncheckedBorderColor = Color(0xFF383838)
                        )
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
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            checkedBorderColor = Color.White,
                            uncheckedThumbColor = Color(0xFFA3A3A3),
                            uncheckedTrackColor = Color(0xFF1E1E1E),
                            uncheckedBorderColor = Color(0xFF383838)
                        )
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
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            checkedBorderColor = Color.White,
                            uncheckedThumbColor = Color(0xFFA3A3A3),
                            uncheckedTrackColor = Color(0xFF1E1E1E),
                            uncheckedBorderColor = Color(0xFF383838)
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = FlowBorder)

                // Auto-Pause in Banking & UPI Apps toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Pause in Banking & UPI Apps", style = MaterialTheme.typography.titleMedium, color = FlowTextPrimary)
                        Text("Hides bubble overlay when Google Pay, PhonePe, Paytm, or bank apps open", style = MaterialTheme.typography.bodyMedium, color = FlowTextSecondary)
                    }
                    Switch(
                        checked = isAutoPauseFinancialApps,
                        onCheckedChange = { coroutineScope.launch { preferencesManager.setAutoPauseFinancialApps(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            checkedBorderColor = Color.White,
                            uncheckedThumbColor = Color(0xFFA3A3A3),
                            uncheckedTrackColor = Color(0xFF1E1E1E),
                            uncheckedBorderColor = Color(0xFF383838)
                        )
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

        // Squircle gradient background (Monochrome Black & Charcoal)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF242424), Color(0xFF141414), Color(0xFF000000)),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(w, h)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
        )

        // Subtle glowing border
        drawRoundRect(
            color = Color.White.copy(alpha = 0.25f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
        )

        val scale = w / 108f
        val strokeW = 5f * scale

        // 6 Soundwave Frequency Bars with Generous Breathing Room
        // Bar 1 (Left Short)
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(34f * scale, 47f * scale),
            end = androidx.compose.ui.geometry.Offset(34f * scale, 61f * scale),
            strokeWidth = strokeW,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        // Bar 2 (Medium-Tall)
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(42f * scale, 38f * scale),
            end = androidx.compose.ui.geometry.Offset(42f * scale, 70f * scale),
            strokeWidth = strokeW,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        // Bar 3 (Tallest Peak)
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(50f * scale, 31f * scale),
            end = androidx.compose.ui.geometry.Offset(50f * scale, 77f * scale),
            strokeWidth = strokeW,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        // Bar 4 (Center-Right Medium)
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(58f * scale, 42f * scale),
            end = androidx.compose.ui.geometry.Offset(58f * scale, 66f * scale),
            strokeWidth = strokeW,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        // Bar 5 (Tall)
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(66f * scale, 37f * scale),
            end = androidx.compose.ui.geometry.Offset(66f * scale, 71f * scale),
            strokeWidth = strokeW,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        // Bar 6 (Right Short)
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(74f * scale, 46f * scale),
            end = androidx.compose.ui.geometry.Offset(74f * scale, 62f * scale),
            strokeWidth = strokeW,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
private fun PermissionsIncompleteCard(
    totalConfigured: Int,
    corePermissionsGranted: Boolean,
    onOpenSetupWizard: () -> Unit
) {
    val progress = (totalConfigured.toFloat() / 5f).coerceIn(0f, 1f)
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress,
        label = "perm_progress"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpenSetupWizard() }
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            ),
        color = Color(0xFF141414)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Top Row: Icon + Title + Pill Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Squircle Icon Container
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF222222), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SYSTEM RELIABILITY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF888888),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (!corePermissionsGranted) "Permissions Required" else "Setup Autostart & Battery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF242424),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$totalConfigured/5 Done",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Linear Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF262626))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle Description
            Text(
                text = if (!corePermissionsGranted) {
                    "Grant Microphone, Overlay & Accessibility to activate instant voice typing."
                } else {
                    "Enable Autostart & disable Battery Saver to prevent Xiaomi from killing Bolnaa overnight."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA3A3A3),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Button(
                onClick = onOpenSetupWizard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Complete Setup ($totalConfigured of 5) →",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun PermissionsActiveCard(
    onOpenSetupWizard: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpenSetupWizard() }
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            ),
        color = Color(0xFF141414)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Top Row: Verified Icon + Title + 5/5 Active Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Squircle Icon Container
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SYSTEM STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF888888),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "100% Uninterrupted Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 5/5 Active Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF222222),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "5/5 Active",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Minimalist Capability Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusFeatureChip(text = "Overlay", modifier = Modifier.weight(1f))
                StatusFeatureChip(text = "Auto-Paste", modifier = Modifier.weight(1f))
                StatusFeatureChip(text = "Autostart", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(color = Color(0xFF242424), thickness = 1.dp)

            Spacer(modifier = Modifier.height(10.dp))

            // Footer row: status text + Manage link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shielded against background sweeps & sleep",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF737373)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusFeatureChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1C1C1C),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCCCCCC),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MasterServiceSwitchCard(
    isServiceActive: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = if (isServiceActive) Color.White.copy(alpha = 0.25f) else Color(0xFFEAB308).copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            ),
        color = if (isServiceActive) Color(0xFF141414) else Color(0xFF1A1710)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (isServiceActive) Color(0xFF222222) else Color(0xFF2C2410),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (isServiceActive) Color.White.copy(alpha = 0.2f) else Color(0xFFEAB308).copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isServiceActive) Icons.Default.Mic else Icons.Default.Pause,
                        contentDescription = null,
                        tint = if (isServiceActive) Color.White else Color(0xFFFBBF24),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isServiceActive) Color(0xFF22C55E) else Color(0xFFEAB308), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isServiceActive) "ACTIVE" else "PAUSED (FINANCIAL SAFE)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceActive) Color(0xFF22C55E) else Color(0xFFEAB308),
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isServiceActive) "Voice Dictation Enabled" else "Safe for Financial Apps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Switch(
                    checked = isServiceActive,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        checkedBorderColor = Color.White,
                        uncheckedThumbColor = Color(0xFFA3A3A3),
                        uncheckedTrackColor = Color(0xFF1E1E1E),
                        uncheckedBorderColor = Color(0xFF383838)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isServiceActive) {
                    "Bolnaa is fully active. Toggle OFF before opening Google Pay, PhonePe, Paytm, or any banking app to get zero security popups."
                } else {
                    "Bolnaa is completely OFF — invisible to all apps. Toggle back ON when you want voice dictation. You'll need to re-enable the Accessibility permission once (Android system prompt)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA3A3A3),
                lineHeight = 18.sp
            )
        }
    }
}

