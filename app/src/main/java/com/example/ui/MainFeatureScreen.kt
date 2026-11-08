package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.signOut
import com.example.automation.AiAutomationRepository
import com.example.automation.DeviceControlEngine
import com.example.data.repository.AutomationRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFeatureScreen(
    currentUserId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: AutomationViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = AutomationRepository.resolveFirestoreDatabaseId(app)
                val db = FirebaseFirestore.getInstance(databaseId)
                AutomationViewModel(
                    repository = AutomationRepository(db),
                    aiRepository = AiAutomationRepository(),
                    deviceEngine = DeviceControlEngine(app.applicationContext),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val workflowsState by viewModel.workflowsState.collectAsStateWithLifecycle()
    val logsState by viewModel.logsState.collectAsStateWithLifecycle()
    val telemetryLogsState by viewModel.telemetryLogsState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val telemetryHistory by viewModel.telemetryHistory.collectAsStateWithLifecycle()
    val isLiveTelemetryStreaming by viewModel.isLiveTelemetryStreaming.collectAsStateWithLifecycle()
    val samplingIntervalMs by viewModel.samplingIntervalMs.collectAsStateWithLifecycle()
    val isAutoCloudSyncEnabled by viewModel.isAutoCloudSyncEnabled.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val aiScriptState by viewModel.aiScriptState.collectAsStateWithLifecycle()
    val aiChatState by viewModel.aiChatState.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val capturedBitmap by viewModel.capturedBitmap.collectAsStateWithLifecycle()
    val isRecordingMacro by viewModel.isRecordingMacro.collectAsStateWithLifecycle()
    val recordedSteps by viewModel.recordedSteps.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val shellHistory by viewModel.shellHistory.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val voiceTriggerStatus by viewModel.voiceTriggerStatus.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    val userEmail = Firebase.auth.currentUser?.email ?: "Operator"

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(
                                text = "AutoPilot AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (isRecordingMacro) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = CircleShape,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "REC (${recordedSteps.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.testTag("top_voice_trigger_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Workflow Trigger",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { signOut(credentialManager, scope) },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val tabs = listOf(
                    Triple(0, "AI Studio", Icons.Default.AutoAwesome),
                    Triple(1, "UI & OCR", Icons.Default.TouchApp),
                    Triple(2, "Workflows", Icons.Default.AccountTree),
                    Triple(3, "Telemetry & ADB", Icons.Default.DeveloperBoard)
                )
                tabs.forEach { (idx, label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("nav_tab_$idx")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Execution Progress Banner
            AnimatedVisibility(
                visible = executionState.isRunning || executionState.statusBanner != null
            ) {
                ExecutionStatusBanner(
                    state = executionState,
                    onDismiss = { viewModel.dismissExecutionBanner() }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> AiStudioTab(
                        telemetry = telemetry,
                        aiScriptState = aiScriptState,
                        aiChatState = aiChatState,
                        onGenerateWorkflow = viewModel::generateWorkflowFromPrompt,
                        onSavePlanToCloud = viewModel::saveGeneratedPlanToCloud,
                        onExecuteStepsNow = { title, steps ->
                            viewModel.runStepsSequence(title, "ai_direct", steps)
                        },
                        onAskCoPilot = viewModel::askAiCoPilotStream
                    )

                    1 -> UiOcrStudioTab(
                        telemetry = telemetry,
                        isRecordingMacro = isRecordingMacro,
                        recordedSteps = recordedSteps,
                        capturedBitmap = capturedBitmap,
                        ocrState = ocrState,
                        onToggleRecording = viewModel::toggleMacroRecording,
                        onAddStep = viewModel::addRecordedStep,
                        onUpdateSteps = viewModel::updateRecordedSteps,
                        onRunSingleStep = viewModel::executeSingleStep,
                        onClearSteps = viewModel::clearRecordedSteps,
                        onReplayMacro = viewModel::replayRecordedSteps,
                        onSaveMacroToCloud = viewModel::saveRecordedMacroAsWorkflow,
                        onCaptureCurrentScreenOcr = viewModel::captureScreenAndRunOcr,
                        onRunOcrOnImportedBitmap = viewModel::runOcrOnBitmap
                    )

                    2 -> WorkflowsTab(
                        telemetry = telemetry,
                        workflowsState = workflowsState,
                        logsState = logsState,
                        voiceTriggerStatus = voiceTriggerStatus,
                        onVoiceTranscriptReceived = viewModel::processVoiceCommandForWorkflows,
                        onToggleVoiceAutoExecute = viewModel::setVoiceAutoExecute,
                        onSeedStarterWorkflows = viewModel::seedStarterWorkflowsIfNeeded,
                        onSaveEditedWorkflow = viewModel::saveEditedWorkflow,
                        onExecuteSingleStep = viewModel::executeSingleStep,
                        onExecuteStepsNow = { title, steps ->
                            viewModel.runStepsSequence(title, "ide_preview", steps)
                        },
                        onExecuteWorkflow = viewModel::executeWorkflow,
                        onToggleWorkflowEnabled = viewModel::toggleWorkflowEnabled,
                        onDeleteWorkflow = viewModel::deleteWorkflow,
                        onDeleteLog = viewModel::deleteExecutionLog
                    )

                    3 -> {
                        val syncedCount = (workflowsState as? DataUiState.Success)?.data?.size ?: 0
                        DeviceControlTab(
                            telemetry = telemetry,
                            telemetryHistory = telemetryHistory,
                            isLiveStreaming = isLiveTelemetryStreaming,
                            samplingIntervalMs = samplingIntervalMs,
                            telemetryLogsState = telemetryLogsState,
                            syncedWorkflowCount = syncedCount,
                            isAutoCloudSyncEnabled = isAutoCloudSyncEnabled,
                            installedApps = installedApps,
                            shellHistory = shellHistory,
                            onRefreshTelemetry = viewModel::refreshDeviceTelemetry,
                            onToggleLiveStreaming = viewModel::toggleLiveTelemetryStreaming,
                            onChangeSamplingInterval = viewModel::setTelemetrySamplingInterval,
                            onSyncTelemetryToCloud = viewModel::syncCurrentTelemetryToCloud,
                            onToggleAutoCloudSync = viewModel::toggleAutoCloudSync,
                            onDeleteSyncedTelemetryLog = viewModel::deleteSyncedTelemetryLog,
                            onRunShellCommand = viewModel::runShellCommand,
                            onLaunchApp = viewModel::launchInstalledApp,
                            onOpenAppInfo = viewModel::openAppInfoForUninstall,
                            onAddAppStepToMacro = viewModel::addRecordedStep
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionStatusBanner(
    state: ActiveExecutionState,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column {
                        Text(
                            text = state.workflowTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = state.stepProgressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                if (!state.isRunning) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Execution Trace",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (state.totalSteps > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (state.currentStepIndex.toFloat() / state.totalSteps.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            if (state.traceLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = state.traceLines.takeLast(3).joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
