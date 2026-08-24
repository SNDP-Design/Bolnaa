package com.wisprflow.android.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wisprflow.android.ai.FlowTranscriptionEngine
import com.wisprflow.android.audio.FlowAudioRecorder
import com.wisprflow.android.data.PreferencesManager
import com.wisprflow.android.data.models.DictationState
import com.wisprflow.android.data.models.FlowTone
import com.wisprflow.android.ui.components.WaveformPreview
import com.wisprflow.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundScreen(
    preferencesManager: PreferencesManager,
    isMicPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val audioRecorder = remember { FlowAudioRecorder(context) }
    val transcriptionEngine = remember { FlowTranscriptionEngine(context, preferencesManager) }

    var dictationState by remember { mutableStateOf(DictationState.IDLE) }
    var inputText by remember { mutableStateOf("") }
    var rawTranscript by remember { mutableStateOf("") }
    var cleanedTranscript by remember { mutableStateOf("") }

    val selectedTone by preferencesManager.flowTone.collectAsState(initial = FlowTone.NATURAL)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flow Playground", color = FlowTextPrimary) },
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Test Dictation & Auto-Paste",
                style = MaterialTheme.typography.headlineMedium,
                color = FlowTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Speak naturally with filler words (e.g. 'um', 'uh') to watch Wispr Flow clean and paste it into this field.",
                style = MaterialTheme.typography.bodyMedium,
                color = FlowTextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Interactive Text Field
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Target Input Field") },
                placeholder = { Text("Tap the mic below and start speaking...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FlowPrimary,
                    unfocusedBorderColor = FlowBorder,
                    focusedTextColor = FlowTextPrimary,
                    unfocusedTextColor = FlowTextPrimary,
                    focusedContainerColor = FlowSurface,
                    unfocusedContainerColor = FlowSurface
                ),
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (inputText.isNotEmpty()) {
                        IconButton(onClick = { inputText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = FlowTextMuted)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Waveform visualizer
            WaveformPreview(
                isListening = dictationState == DictationState.LISTENING,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Dictation Action Button
            val isListening = dictationState == DictationState.LISTENING
            val isProcessing = dictationState == DictationState.PROCESSING

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isListening -> FlowListening
                            isProcessing -> FlowAccent
                            else -> FlowPrimary
                        }
                    )
                    .clickable(enabled = !isProcessing) {
                        if (!isMicPermissionGranted) {
                            onRequestMicPermission()
                            return@clickable
                        }

                        if (isListening) {
                            // Stop and process
                            dictationState = DictationState.PROCESSING
                            coroutineScope.launch {
                                val audioFile = withContext(Dispatchers.IO) {
                                    audioRecorder.stopRecording()
                                }
                                if (audioFile != null && audioFile.exists()) {
                                    val result = transcriptionEngine.processAudioFile(audioFile)
                                    if (result.isSuccess) {
                                        val output = result.getOrNull().orEmpty()
                                        cleanedTranscript = output
                                        inputText = if (inputText.isNotEmpty()) "$inputText $output" else output
                                        dictationState = DictationState.SUCCESS
                                        delay(1000)
                                        dictationState = DictationState.IDLE
                                    } else {
                                        Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        dictationState = DictationState.ERROR
                                        delay(1500)
                                        dictationState = DictationState.IDLE
                                    }
                                } else {
                                    dictationState = DictationState.IDLE
                                }
                            }
                        } else {
                            // Start listening
                            val started = audioRecorder.startRecording()
                            if (started) {
                                dictationState = DictationState.LISTENING
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isProcessing -> Icons.Default.HourglassTop
                        isListening -> Icons.Default.Stop
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Dictate",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (dictationState) {
                    DictationState.LISTENING -> "Listening... Tap to stop"
                    DictationState.PROCESSING -> "Transcribing & Flow Formatting..."
                    DictationState.SUCCESS -> "Pasted Successfully!"
                    DictationState.ERROR -> "Try Again"
                    else -> "Tap to Speak"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when (dictationState) {
                    DictationState.LISTENING -> FlowListening
                    DictationState.SUCCESS -> FlowSuccess
                    DictationState.PROCESSING -> FlowAccent
                    else -> FlowTextPrimary
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // AI Flow comparison card
            AnimatedVisibility(visible = cleanedTranscript.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, FlowBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = FlowSurface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FlowAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wispr Flow Result", style = MaterialTheme.typography.titleMedium, color = FlowTextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = cleanedTranscript, style = MaterialTheme.typography.bodyLarge, color = FlowTextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
