package com.wisprflow.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import com.wisprflow.android.data.PreferencesManager
import com.wisprflow.android.data.models.SttEngine
import com.wisprflow.android.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val groqKey by preferencesManager.groqApiKey.collectAsState(initial = "")
    val openAiKey by preferencesManager.openAiApiKey.collectAsState(initial = "")
    val selectedEngine by preferencesManager.sttEngine.collectAsState(initial = SttEngine.GROQ)
    val isAiCleanupEnabled by preferencesManager.isAiCleanupEnabled.collectAsState(initial = true)
    val isAutoStopSilence by preferencesManager.isAutoStopSilence.collectAsState(initial = true)
    val silenceTimeoutMs by preferencesManager.silenceTimeoutMs.collectAsState(initial = 1600)
    val customVocab by preferencesManager.customVocabulary.collectAsState(initial = "")

    var groqInput by remember(groqKey) { mutableStateOf(groqKey) }
    var openAiInput by remember(openAiKey) { mutableStateOf(openAiKey) }
    var vocabInput by remember(customVocab) { mutableStateOf(customVocab) }
    var isGroqKeyVisible by remember { mutableStateOf(false) }
    var isOpenAiKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & API Keys", color = FlowTextPrimary) },
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
            // Speech Recognition Engine
            Text(
                text = "Speech-to-Text Engine",
                style = MaterialTheme.typography.titleMedium,
                color = FlowTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SttEngine.values().forEach { engine ->
                    val isSelected = engine == selectedEngine
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                coroutineScope.launch { preferencesManager.setSttEngine(engine) }
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
                                    coroutineScope.launch { preferencesManager.setSttEngine(engine) }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = FlowPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = engine.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) FlowPrimaryLight else FlowTextPrimary
                                )
                                Text(
                                    text = engine.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FlowTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // API Keys Section
            Text(
                text = "API Keys",
                style = MaterialTheme.typography.titleMedium,
                color = FlowTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "API keys are stored securely on your device and used only for fast transcription & AI formatting.",
                style = MaterialTheme.typography.bodyMedium,
                color = FlowTextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Groq API Key (Recommended)
            OutlinedTextField(
                value = groqInput,
                onValueChange = {
                    groqInput = it
                    coroutineScope.launch { preferencesManager.setGroqApiKey(it) }
                },
                label = { Text("Groq API Key (Recommended for <300ms speed)") },
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
                    focusedContainerColor = FlowSurface,
                    unfocusedContainerColor = FlowSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // OpenAI API Key
            OutlinedTextField(
                value = openAiInput,
                onValueChange = {
                    openAiInput = it
                    coroutineScope.launch { preferencesManager.setOpenAiApiKey(it) }
                },
                label = { Text("OpenAI API Key (Optional)") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isOpenAiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isOpenAiKeyVisible = !isOpenAiKeyVisible }) {
                        Icon(
                            imageVector = if (isOpenAiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
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
                    focusedContainerColor = FlowSurface,
                    unfocusedContainerColor = FlowSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Dictation Behavior & Auto-Stop
            Text(
                text = "Dictation Behavior",
                style = MaterialTheme.typography.titleMedium,
                color = FlowTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                            Text("Wispr Smart AI Clean-up", style = MaterialTheme.typography.titleMedium, color = FlowTextPrimary)
                            Text("Strips 'um/uh', stutters, and fixes grammar", style = MaterialTheme.typography.bodyMedium, color = FlowTextSecondary)
                        }
                        Switch(
                            checked = isAiCleanupEnabled,
                            onCheckedChange = { coroutineScope.launch { preferencesManager.setAiCleanupEnabled(it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = FlowPrimary)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 14.dp), color = FlowBorder)

                    // Auto Stop Silence Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Stop on Silence", style = MaterialTheme.typography.titleMedium, color = FlowTextPrimary)
                            Text("Automatically finishes dictation when you stop talking", style = MaterialTheme.typography.bodyMedium, color = FlowTextSecondary)
                        }
                        Switch(
                            checked = isAutoStopSilence,
                            onCheckedChange = { coroutineScope.launch { preferencesManager.setAutoStopSilence(it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = FlowPrimary)
                        )
                    }

                    if (isAutoStopSilence) {
                        Spacer(modifier = Modifier.height(12.dp))
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

            Spacer(modifier = Modifier.height(28.dp))

            // Live Updates (OTA) Configuration
            Text(
                text = "Live Over-The-Air (OTA) Updates",
                style = MaterialTheme.typography.titleMedium,
                color = FlowTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "When enabled, any new code changes pushed to GitHub automatically update this app without manual downloads.",
                style = MaterialTheme.typography.bodyMedium,
                color = FlowTextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = FlowSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, FlowBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GitHub Repository (owner/repo):",
                        style = MaterialTheme.typography.labelMedium,
                        color = FlowTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = com.wisprflow.android.updater.AppUpdateManager.GITHUB_REPO,
                        style = MaterialTheme.typography.bodyLarge,
                        color = FlowPrimaryLight,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val updater = com.wisprflow.android.updater.AppUpdateManager(context)
                                updater.checkForUpdates()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FlowPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for Live Updates Now")
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Custom Vocabulary / Dictionary
            Text(
                text = "Custom Vocabulary & Acronyms",
                style = MaterialTheme.typography.titleMedium,
                color = FlowTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add names, company terms, or technical keywords to prioritize during transcription (comma separated).",
                style = MaterialTheme.typography.bodyMedium,
                color = FlowTextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = vocabInput,
                onValueChange = {
                    vocabInput = it
                    coroutineScope.launch { preferencesManager.setCustomVocabulary(it) }
                },
                placeholder = { Text("e.g. Kubernetes, Wispr Flow, GraphQL, PyTorch") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FlowPrimary,
                    unfocusedBorderColor = FlowBorder,
                    focusedTextColor = FlowTextPrimary,
                    unfocusedTextColor = FlowTextPrimary,
                    focusedContainerColor = FlowSurface,
                    unfocusedContainerColor = FlowSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
