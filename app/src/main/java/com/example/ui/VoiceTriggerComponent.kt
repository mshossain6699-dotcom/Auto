package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.AutomationWorkflow
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import java.util.Locale

data class VoiceTriggerStatus(
    val lastSpokenText: String = "",
    val matchedWorkflowId: String? = null,
    val matchedWorkflowTitle: String? = null,
    val statusDetail: String = "Tap the microphone and speak a workflow name (e.g., \"Run Morning System Audit\").",
    val isMatchSuccess: Boolean = false,
    val autoExecuteOnMatch: Boolean = true
)

@Composable
fun VoiceWorkflowTriggerCard(
    savedWorkflows: List<AutomationWorkflow>,
    voiceStatus: VoiceTriggerStatus,
    onVoiceTranscriptReceived: (String) -> Unit,
    onToggleAutoExecute: (Boolean) -> Unit,
    onRunMatchedWorkflow: (AutomationWorkflow) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var livePartialTranscript by remember { mutableStateOf("") }
    var currentRmsDb by remember { mutableFloatStateOf(0f) }
    val rmsWaveformBars = remember {
        mutableStateListOf(0.15f, 0.2f, 0.18f, 0.25f, 0.15f, 0.22f, 0.18f, 0.2f, 0.15f, 0.24f, 0.16f, 0.2f)
    }

    // Fallback system speech recognizer activity launcher
    val intentSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val bestText = matches?.firstOrNull().orEmpty().trim()
            if (bestText.isNotBlank()) {
                livePartialTranscript = bestText
                onVoiceTranscriptReceived(bestText)
            }
        }
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun startSpeechCapture() {
        permissionDeniedMessage = null
        livePartialTranscript = ""
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak workflow name to trigger...")
        }

        if (speechRecognizer != null) {
            try {
                isListening = true
                speechRecognizer.startListening(recognizerIntent)
            } catch (e: Exception) {
                isListening = false
                try {
                    intentSpeechLauncher.launch(recognizerIntent)
                } catch (_: Exception) {
                    permissionDeniedMessage = "Speech recognition service is unavailable on this device."
                }
            }
        } else {
            try {
                isListening = true
                intentSpeechLauncher.launch(recognizerIntent)
            } catch (_: Exception) {
                isListening = false
                permissionDeniedMessage = "No SpeechRecognizer engine installed on this device."
            }
        }
    }

    fun stopSpeechCapture() {
        isListening = false
        currentRmsDb = 0f
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            permissionDeniedMessage = null
            startSpeechCapture()
        } else {
            isListening = false
            permissionDeniedMessage = "Microphone permission (RECORD_AUDIO) was denied. Grant microphone access to use voice triggers."
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                isListening = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                currentRmsDb = rmsdB
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.12f, 1.0f)
                if (rmsWaveformBars.isNotEmpty()) {
                    rmsWaveformBars.removeAt(0)
                    rmsWaveformBars.add(normalized)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isListening = false
                currentRmsDb = 0f
            }

            override fun onError(error: Int) {
                isListening = false
                currentRmsDb = 0f
                val errMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match detected. Try speaking clearly or tap a voice phrase below."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out waiting for speech."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        hasMicPermission = false
                        "Microphone permission is required."
                    }
                    else -> "Speech engine status code $error. You can retry or tap a voice command chip below."
                }
                permissionDeniedMessage = errMsg
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                currentRmsDb = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull().orEmpty().trim()
                if (best.isNotBlank()) {
                    livePartialTranscript = best
                    onVoiceTranscriptReceived(best)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (partial.isNotBlank()) {
                    livePartialTranscript = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        speechRecognizer?.setRecognitionListener(listener)

        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_workflow_trigger_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header & Permission Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (isListening) {
                            CoralAlert.copy(alpha = 0.2f)
                        } else {
                            ElectricCyan.copy(alpha = 0.16f)
                        },
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (hasMicPermission) Icons.Default.GraphicEq else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = if (isListening) CoralAlert else ElectricCyan
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Voice-to-Text Workflow Trigger",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                isListening -> "Listening via Microphone... Speak workflow name now"
                                hasMicPermission -> "Microphone ready • Say \"Run <Workflow Name>\""
                                else -> "Microphone permission required for voice trigger"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isListening -> CoralAlert
                                hasMicPermission -> NeonEmerald
                                else -> AmberPulse
                            }
                        )
                    }
                }

                // Auto-Run Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Auto-Run",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = voiceStatus.autoExecuteOnMatch,
                        onCheckedChange = onToggleAutoExecute,
                        modifier = Modifier.testTag("voice_auto_execute_switch")
                    )
                }
            }

            // 2. Microphone Action Row + Live Waveform Canvas
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (isListening) {
                            stopSpeechCapture()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            hasMicPermission = granted
                            if (granted) {
                                startSpeechCapture()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) CoralAlert else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("voice_trigger_mic_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop Listening" else "Start Voice Trigger"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isListening) "Stop Mic" else "Speak Trigger",
                        fontWeight = FontWeight.Bold
                    )
                }

                // Live Audio Waveform Visualizer Box
                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .border(
                            width = 1.dp,
                            color = if (isListening) CoralAlert.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    val animatedRms by animateFloatAsState(
                        targetValue = if (isListening) ((currentRmsDb + 2f) / 12f).coerceIn(0.18f, 1f) else 0.15f,
                        animationSpec = tween(durationMillis = 120),
                        label = "rms_level"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val count = rmsWaveformBars.size.coerceAtLeast(1)
                            val slotW = size.width / count
                            val barW = (slotW * 0.52f).coerceIn(3f, 12f)
                            rmsWaveformBars.forEachIndexed { idx, level ->
                                val effectiveLevel = if (isListening) {
                                    ((level + animatedRms) / 2f).coerceIn(0.15f, 1f)
                                } else {
                                    0.18f
                                }
                                val barH = size.height * effectiveLevel
                                val left = idx * slotW + (slotW - barW) / 2f
                                val top = (size.height - barH) / 2f
                                drawRoundRect(
                                    color = if (isListening) CoralAlert else ElectricCyan.copy(alpha = 0.45f),
                                    topLeft = Offset(left, top),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Permission / Recognition Alert Banner
            AnimatedVisibility(visible = !permissionDeniedMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = permissionDeniedMessage.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (!hasMicPermission) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Grant Mic", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // 4. Recognized Speech Transcript & Matched Workflow Status Box
            val displayedTranscript = if (isListening && livePartialTranscript.isNotBlank()) {
                livePartialTranscript
            } else {
                voiceStatus.lastSpokenText
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (displayedTranscript.isNotBlank()) {
                                "Transcript: \"$displayedTranscript\""
                            } else {
                                "Transcript: Waiting for voice command..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_transcript_text")
                        )

                        if (voiceStatus.isMatchSuccess && !voiceStatus.matchedWorkflowTitle.isNullOrBlank()) {
                            Surface(
                                color = NeonEmerald.copy(alpha = 0.18f),
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonEmerald,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "MATCHED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = voiceStatus.statusDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (voiceStatus.isMatchSuccess) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val matchedWorkflow = savedWorkflows.firstOrNull { it.id == voiceStatus.matchedWorkflowId }
                    if (matchedWorkflow != null && !voiceStatus.autoExecuteOnMatch) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { onRunMatchedWorkflow(matchedWorkflow) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("run_voice_matched_workflow_button")
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run '${matchedWorkflow.title}' Now", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // 5. Quick Voice Command Phrase Chips (for saved workflows)
            if (savedWorkflows.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Try saying (or tap to simulate voice utterance):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        savedWorkflows.take(6).forEachIndexed { idx, wf ->
                            val phrase = "Run ${wf.title}"
                            AssistChip(
                                onClick = {
                                    livePartialTranscript = phrase
                                    onVoiceTranscriptReceived(phrase)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = ElectricCyan
                                    )
                                },
                                label = {
                                    Text(
                                        text = "\"$phrase\"",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("voice_phrase_chip_$idx")
                            )
                        }
                    }
                }
            }
        }
    }
}
