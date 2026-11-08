package com.example.ui

import android.app.Activity
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.automation.AiAutomationRepository
import com.example.automation.DeviceControlEngine
import com.example.automation.GeneratedWorkflowPlan
import com.example.automation.ScreenOcrAnalysisResult
import com.example.data.model.AutomationStep
import com.example.data.model.AutomationWorkflow
import com.example.data.model.DeviceTelemetry
import com.example.data.model.ExecutionLog
import com.example.data.model.InstalledAppItem
import com.example.data.model.StepActionType
import com.example.data.model.SyncedTelemetryLog
import com.example.data.model.TelemetrySample
import com.example.data.repository.AutomationRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "AutomationViewModel"

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface AiScriptState {
    data object Idle : AiScriptState
    data object Loading : AiScriptState
    data class Success(val plan: GeneratedWorkflowPlan) : AiScriptState
    data class Error(val message: String) : AiScriptState
}

sealed interface AiChatState {
    data object Idle : AiChatState
    data object Loading : AiChatState
    data class Streaming(val text: String) : AiChatState
    data class Success(val text: String) : AiChatState
    data class Error(val message: String) : AiChatState
}

sealed interface OcrState {
    data object Idle : OcrState
    data object Analyzing : OcrState
    data class Success(val result: ScreenOcrAnalysisResult) : OcrState
    data class Error(val message: String) : OcrState
}

data class ActiveExecutionState(
    val isRunning: Boolean = false,
    val workflowTitle: String = "",
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val stepProgressText: String = "",
    val traceLines: List<String> = emptyList(),
    val statusBanner: String? = null
)

class AutomationViewModel(
    private val repository: AutomationRepository,
    private val aiRepository: AiAutomationRepository,
    private val deviceEngine: DeviceControlEngine,
    private val currentUserId: String
) : ViewModel() {

    val workflowsState: StateFlow<DataUiState<List<AutomationWorkflow>>> =
        repository.observeWorkflows(currentUserId)
            .map<List<AutomationWorkflow>, DataUiState<List<AutomationWorkflow>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing workflows", rootError)
                emit(DataUiState.Error(rootError.message ?: "Failed to load automation workflows"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )

    val logsState: StateFlow<DataUiState<List<ExecutionLog>>> =
        repository.observeLogs(currentUserId)
            .map<List<ExecutionLog>, DataUiState<List<ExecutionLog>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing execution logs", rootError)
                emit(DataUiState.Error(rootError.message ?: "Failed to load execution logs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )

    val telemetryLogsState: StateFlow<DataUiState<List<SyncedTelemetryLog>>> =
        repository.observeTelemetryLogs(currentUserId)
            .map<List<SyncedTelemetryLog>, DataUiState<List<SyncedTelemetryLog>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing synced telemetry logs", rootError)
                emit(DataUiState.Error(rootError.message ?: "Failed to load synced telemetry logs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )

    private val _telemetry = MutableStateFlow(deviceEngine.collectDeviceTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _telemetryHistory = MutableStateFlow<List<TelemetrySample>>(emptyList())
    val telemetryHistory: StateFlow<List<TelemetrySample>> = _telemetryHistory.asStateFlow()

    private val _isLiveTelemetryStreaming = MutableStateFlow(true)
    val isLiveTelemetryStreaming: StateFlow<Boolean> = _isLiveTelemetryStreaming.asStateFlow()

    private val _samplingIntervalMs = MutableStateFlow(2000L)
    val samplingIntervalMs: StateFlow<Long> = _samplingIntervalMs.asStateFlow()

    private val _isAutoCloudSyncEnabled = MutableStateFlow(false)
    val isAutoCloudSyncEnabled: StateFlow<Boolean> = _isAutoCloudSyncEnabled.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _aiScriptState = MutableStateFlow<AiScriptState>(AiScriptState.Idle)
    val aiScriptState: StateFlow<AiScriptState> = _aiScriptState.asStateFlow()

    private val _aiChatState = MutableStateFlow<AiChatState>(AiChatState.Idle)
    val aiChatState: StateFlow<AiChatState> = _aiChatState.asStateFlow()

    private val _ocrState = MutableStateFlow<OcrState>(OcrState.Idle)
    val ocrState: StateFlow<OcrState> = _ocrState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _isRecordingMacro = MutableStateFlow(false)
    val isRecordingMacro: StateFlow<Boolean> = _isRecordingMacro.asStateFlow()

    private val _recordedSteps = MutableStateFlow(
        listOf(
            AutomationStep(
                id = "init_tap_1",
                actionType = StepActionType.TAP,
                target = "Search Bar",
                x = 540,
                y = 420,
                note = "Focus top search input"
            ),
            AutomationStep(
                id = "init_swipe_2",
                actionType = StepActionType.SWIPE,
                target = "Feed Scroll",
                x = 540,
                y = 1780,
                endX = 540,
                endY = 580,
                delayMs = 550L,
                note = "Swipe up to reveal action cards"
            ),
            AutomationStep(
                id = "init_click_3",
                actionType = StepActionType.CLICK_TEXT,
                target = "Continue",
                x = 540,
                y = 1960,
                note = "Click primary confirmation button"
            )
        )
    )
    val recordedSteps: StateFlow<List<AutomationStep>> = _recordedSteps.asStateFlow()

    private val _executionState = MutableStateFlow(ActiveExecutionState())
    val executionState: StateFlow<ActiveExecutionState> = _executionState.asStateFlow()

    private val _shellHistory = MutableStateFlow<List<Pair<String, String>>>(
        listOf(
            "getprop ro.product.model && getprop ro.build.version.release" to
                "${_telemetry.value.deviceModel} • ${_telemetry.value.androidVersion} (SDK ${_telemetry.value.sdkInt})"
        )
    )
    val shellHistory: StateFlow<List<Pair<String, String>>> = _shellHistory.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _voiceTriggerStatus = MutableStateFlow(VoiceTriggerStatus())
    val voiceTriggerStatus: StateFlow<VoiceTriggerStatus> = _voiceTriggerStatus.asStateFlow()

    init {
        val initial = _telemetry.value
        val nowMs = System.currentTimeMillis()
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        _telemetryHistory.value = listOf(
            TelemetrySample(
                timestampMs = nowMs,
                labelTime = timeFmt.format(Date(nowMs)),
                cpuUsagePercent = initial.cpuUsagePercent,
                batteryPercent = initial.batteryPercent,
                batteryTempC = initial.batteryTempC,
                batteryVoltageMv = initial.batteryVoltageMv,
                ramUsagePercent = initial.ramUsagePercent,
                networkLatencyMs = initial.networkLatencyMs,
                rxKbps = initial.rxKbps,
                txKbps = initial.txKbps
            )
        )
        loadInstalledApps()
        startRealtimeTelemetryLoop()
    }

    private fun startRealtimeTelemetryLoop() {
        viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                if (_isLiveTelemetryStreaming.value) {
                    val (updatedTelemetry, sample) = deviceEngine.sampleRealtimeTelemetry()
                    _telemetry.value = updatedTelemetry
                    _telemetryHistory.value = (_telemetryHistory.value + sample).takeLast(30)
                    tickCount++
                    if (_isAutoCloudSyncEnabled.value && tickCount % 15 == 0) {
                        repository.syncTelemetrySnapshotToCloud(updatedTelemetry)
                    }
                }
                delay(_samplingIntervalMs.value.coerceAtLeast(1000L))
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun refreshDeviceTelemetry() {
        viewModelScope.launch {
            val (updatedTelemetry, sample) = deviceEngine.sampleRealtimeTelemetry()
            _telemetry.value = updatedTelemetry
            _telemetryHistory.value = (_telemetryHistory.value + sample).takeLast(30)
        }
    }

    fun syncCurrentTelemetryToCloud() {
        viewModelScope.launch {
            val (updatedTelemetry, sample) = deviceEngine.sampleRealtimeTelemetry()
            _telemetry.value = updatedTelemetry
            _telemetryHistory.value = (_telemetryHistory.value + sample).takeLast(30)
            val res = repository.syncTelemetrySnapshotToCloud(updatedTelemetry)
            res.fold(
                onSuccess = {
                    _toastMessage.value = "Synced telemetry snapshot from ${updatedTelemetry.deviceModel} to Cloud Firestore!"
                },
                onFailure = { err ->
                    _toastMessage.value = "Telemetry cloud sync failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun toggleAutoCloudSync(enabled: Boolean) {
        _isAutoCloudSyncEnabled.value = enabled
        if (enabled) {
            syncCurrentTelemetryToCloud()
        } else {
            _toastMessage.value = "Auto Cloud Telemetry Sync paused"
        }
    }

    fun deleteSyncedTelemetryLog(telemetryId: String) {
        viewModelScope.launch {
            repository.deleteTelemetryLog(telemetryId)
        }
    }

    fun toggleLiveTelemetryStreaming() {
        _isLiveTelemetryStreaming.value = !_isLiveTelemetryStreaming.value
        _toastMessage.value = if (_isLiveTelemetryStreaming.value) {
            "Real-time telemetry stream resumed"
        } else {
            "Real-time telemetry stream paused"
        }
    }

    fun setTelemetrySamplingInterval(intervalMs: Long) {
        _samplingIntervalMs.value = intervalMs.coerceIn(1000L, 10000L)
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = deviceEngine.queryInstalledApps()
        }
    }

    fun generateWorkflowFromPrompt(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _aiScriptState.value = AiScriptState.Loading
            val currentTelemetry = deviceEngine.collectDeviceTelemetry()
            _telemetry.value = currentTelemetry
            val result = aiRepository.generateWorkflowFromNaturalLanguage(prompt, currentTelemetry)
            result.fold(
                onSuccess = { plan ->
                    _aiScriptState.value = AiScriptState.Success(plan)
                },
                onFailure = { error ->
                    _aiScriptState.value = AiScriptState.Error(
                        error.localizedMessage ?: "Failed to generate automation script."
                    )
                }
            )
        }
    }

    fun askAiCoPilotStream(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _aiChatState.value = AiChatState.Loading
            val t = _telemetry.value
            val summary = "${t.deviceModel}, ${t.androidVersion}, Screen ${t.screenWidthPx}x${t.screenHeightPx}, Battery ${t.batteryPercent}%, RAM ${t.ramUsedMb}/${t.ramTotalMb}MB"
            var latestText = ""
            try {
                aiRepository.streamAutomationAssistant(question, summary).collect { partial ->
                    latestText = partial
                    _aiChatState.value = AiChatState.Streaming(partial)
                }
                if (latestText.isNotBlank()) {
                    _aiChatState.value = AiChatState.Success(latestText)
                } else {
                    _aiChatState.value = AiChatState.Error("Model returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI Assistant streaming timed out", e)
                _aiChatState.value = AiChatState.Error("Streaming timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Assistant streaming failed", e)
                _aiChatState.value = AiChatState.Error(e.localizedMessage ?: "AI Assistant failed.")
            }
        }
    }

    fun saveGeneratedPlanToCloud(plan: GeneratedWorkflowPlan) {
        viewModelScope.launch {
            val result = repository.createWorkflow(
                title = plan.title,
                description = plan.description,
                triggerType = plan.triggerType,
                scheduleCron = plan.scheduleCron,
                isEnabled = true,
                steps = plan.steps
            )
            result.fold(
                onSuccess = {
                    _toastMessage.value = "Saved '${plan.title}' to Cloud Workflows!"
                },
                onFailure = { err ->
                    _toastMessage.value = "Failed to save workflow: ${err.localizedMessage}"
                }
            )
        }
    }

    fun createCustomWorkflow(
        title: String,
        description: String,
        triggerType: String,
        scheduleCron: String,
        steps: List<AutomationStep>
    ) {
        viewModelScope.launch {
            val res = repository.createWorkflow(
                title = title,
                description = description,
                triggerType = triggerType,
                scheduleCron = scheduleCron,
                isEnabled = true,
                steps = steps
            )
            res.fold(
                onSuccess = { _toastMessage.value = "Created workflow '$title'" },
                onFailure = { _toastMessage.value = "Error: ${it.localizedMessage}" }
            )
        }
    }

    fun seedStarterWorkflowsIfNeeded() {
        viewModelScope.launch {
            val w = _telemetry.value.screenWidthPx
            val h = _telemetry.value.screenHeightPx

            repository.createWorkflow(
                title = "Morning System & Battery Audit",
                description = "Checks battery health via shell, opens System Settings, scrolls down, and captures OCR snapshot.",
                triggerType = "SCHEDULED",
                scheduleCron = "08:00 Daily",
                isEnabled = true,
                steps = listOf(
                    AutomationStep(
                        id = "s1",
                        actionType = StepActionType.SHELL_CMD,
                        target = "getprop ro.build.version.release && uptime",
                        note = "Verify OS version & system uptime"
                    ),
                    AutomationStep(
                        id = "s2",
                        actionType = StepActionType.OPEN_APP,
                        target = "com.android.settings",
                        note = "Open Android System Settings"
                    ),
                    AutomationStep(
                        id = "s3",
                        actionType = StepActionType.SWIPE,
                        x = w / 2,
                        y = (h * 0.72).toInt(),
                        endX = w / 2,
                        endY = (h * 0.28).toInt(),
                        delayMs = 550L,
                        note = "Scroll down to Battery & Device Care"
                    ),
                    AutomationStep(
                        id = "s4",
                        actionType = StepActionType.CLICK_TEXT,
                        target = "Battery",
                        x = w / 2,
                        y = (h * 0.45).toInt(),
                        note = "Click Battery menu item"
                    ),
                    AutomationStep(
                        id = "s5",
                        actionType = StepActionType.SCREENSHOT_OCR,
                        target = "Battery Usage Screen",
                        note = "Extract battery stats via AI OCR"
                    )
                )
            )

            repository.createWorkflow(
                title = "Quick Form Auto-Fill & Submit Macro",
                description = "Fills user profile fields automatically, types verification text, and taps Submit button.",
                triggerType = "MANUAL",
                scheduleCron = "",
                isEnabled = true,
                steps = listOf(
                    AutomationStep(
                        id = "f1",
                        actionType = StepActionType.FILL_FORM,
                        target = "email_input, full_name",
                        value = "operator@autopilot.ai",
                        note = "Auto-fill login/contact form"
                    ),
                    AutomationStep(
                        id = "f2",
                        actionType = StepActionType.TAP,
                        target = "Continue / Submit Button",
                        x = w / 2,
                        y = (h * 0.82).toInt(),
                        note = "Tap primary CTA button"
                    )
                )
            )
            _toastMessage.value = "Loaded 2 starter automation workflows!"
        }
    }

    fun toggleWorkflowEnabled(workflow: AutomationWorkflow) {
        viewModelScope.launch {
            repository.updateWorkflow(workflow.copy(isEnabled = !workflow.isEnabled))
        }
    }

    fun deleteWorkflow(workflowId: String) {
        viewModelScope.launch {
            repository.deleteWorkflow(workflowId)
        }
    }

    fun deleteExecutionLog(logId: String) {
        viewModelScope.launch {
            repository.deleteLog(logId)
        }
    }

    fun executeWorkflow(workflow: AutomationWorkflow) {
        val steps = workflow.parsedSteps()
        runStepsSequence(
            title = workflow.title,
            workflowId = workflow.id.ifBlank { "adhoc" },
            steps = steps,
            existingWorkflow = workflow
        )
    }

    fun runStepsSequence(
        title: String,
        workflowId: String,
        steps: List<AutomationStep>,
        existingWorkflow: AutomationWorkflow? = null
    ) {
        if (steps.isEmpty()) {
            _toastMessage.value = "No steps to execute."
            return
        }
        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            val trace = mutableListOf<String>()
            _executionState.value = ActiveExecutionState(
                isRunning = true,
                workflowTitle = title,
                currentStepIndex = 0,
                totalSteps = steps.size,
                stepProgressText = "Initializing automation engine...",
                traceLines = emptyList(),
                statusBanner = null
            )

            var allSucceeded = true
            steps.forEachIndexed { index, step ->
                _executionState.value = _executionState.value.copy(
                    currentStepIndex = index + 1,
                    stepProgressText = "Step ${index + 1}/${steps.size}: ${step.summaryText()}"
                )
                val (ok, stepOut) = deviceEngine.executeStep(step) { msg ->
                    _executionState.value = _executionState.value.copy(stepProgressText = msg)
                }
                if (!ok) allSucceeded = false
                trace.add("[Step ${index + 1} • ${step.actionType.badge}] $stepOut")
                _executionState.value = _executionState.value.copy(traceLines = trace.toList())
            }

            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(120L)
            val finalStatus = if (allSucceeded) "SUCCESS" else "WARNING"
            val summary = "Executed ${steps.size} steps in ${elapsed}ms ($finalStatus)"

            _executionState.value = _executionState.value.copy(
                isRunning = false,
                stepProgressText = summary,
                statusBanner = summary
            )

            if (existingWorkflow != null && existingWorkflow.id.isNotBlank()) {
                repository.updateWorkflow(
                    existingWorkflow.copy(
                        runCount = existingWorkflow.runCount + 1L,
                        lastStatus = if (allSucceeded) "SUCCESS" else "FAILED"
                    )
                )
            }

            repository.recordExecutionLog(
                workflowId = workflowId,
                workflowTitle = title,
                status = finalStatus,
                summary = summary,
                commandOutput = trace.joinToString("\n"),
                durationMs = elapsed
            )
            refreshDeviceTelemetry()
        }
    }

    fun dismissExecutionBanner() {
        _executionState.value = ActiveExecutionState()
    }

    // --- UI Automation & Macro Recorder ---
    fun toggleMacroRecording() {
        _isRecordingMacro.value = !_isRecordingMacro.value
        if (_isRecordingMacro.value) {
            _toastMessage.value = "Macro Recording ON — Tap, swipe, or add actions below."
        } else {
            _toastMessage.value = "Macro Recording paused (${_recordedSteps.value.size} steps)."
        }
    }

    fun addRecordedStep(step: AutomationStep) {
        _recordedSteps.value = _recordedSteps.value + step
        viewModelScope.launch {
            val (_, out) = deviceEngine.executeStep(step) {}
            _toastMessage.value = out.lines().firstOrNull() ?: "Action dispatched"
        }
    }

    fun updateRecordedSteps(steps: List<AutomationStep>) {
        _recordedSteps.value = steps
    }

    fun executeSingleStep(step: AutomationStep) {
        viewModelScope.launch {
            val (_, out) = deviceEngine.executeStep(step) {}
            _toastMessage.value = "Executed [${step.actionType.badge}]: ${out.lines().firstOrNull().orEmpty()}"
        }
    }

    fun saveEditedWorkflow(
        existingWorkflow: AutomationWorkflow?,
        title: String,
        description: String,
        triggerType: String,
        scheduleCron: String,
        steps: List<AutomationStep>
    ) {
        if (existingWorkflow != null && existingWorkflow.id.isNotBlank()) {
            viewModelScope.launch {
                val encoded = AutomationStep.encodeSteps(steps).take(14500)
                val updated = existingWorkflow.copy(
                    title = title.ifBlank { existingWorkflow.title },
                    description = description,
                    triggerType = triggerType,
                    scheduleCron = scheduleCron,
                    stepsJson = encoded,
                    stepCount = steps.size.toLong()
                )
                val res = repository.updateWorkflow(updated)
                res.fold(
                    onSuccess = { _toastMessage.value = "Updated workflow '${updated.title}' (${steps.size} steps)" },
                    onFailure = { _toastMessage.value = "Update failed: ${it.localizedMessage}" }
                )
            }
        } else {
            createCustomWorkflow(
                title = title,
                description = description,
                triggerType = triggerType,
                scheduleCron = scheduleCron,
                steps = steps
            )
        }
    }

    fun clearRecordedSteps() {
        _recordedSteps.value = emptyList()
    }

    fun replayRecordedSteps() {
        val current = _recordedSteps.value
        if (current.isEmpty()) {
            _toastMessage.value = "Record or add at least one UI action first."
            return
        }
        runStepsSequence(
            title = "Recorded UI Macro (${current.size} steps)",
            workflowId = "recorded_macro",
            steps = current
        )
    }

    fun saveRecordedMacroAsWorkflow(title: String, scheduleCron: String) {
        val steps = _recordedSteps.value
        if (steps.isEmpty()) {
            _toastMessage.value = "No recorded steps to save."
            return
        }
        createCustomWorkflow(
            title = title.ifBlank { "Recorded UI Macro (${steps.size} actions)" },
            description = "Authored via Automation Script & Block Editor.",
            triggerType = if (scheduleCron.isBlank()) "MANUAL" else "SCHEDULED",
            scheduleCron = scheduleCron,
            steps = steps
        )
    }

    // --- Screen Capture & Multimodal OCR ---
    fun captureScreenAndRunOcr(activity: Activity?, userGoal: String) {
        val bmp = deviceEngine.captureCurrentActivityBitmap(activity)
        if (bmp == null) {
            _ocrState.value = OcrState.Error("Could not capture current window frame.")
            return
        }
        runOcrOnBitmap(bmp, userGoal)
    }

    fun runOcrOnBitmap(bitmap: Bitmap, userGoal: String) {
        _capturedBitmap.value = bitmap
        viewModelScope.launch {
            _ocrState.value = OcrState.Analyzing
            val t = _telemetry.value
            val result = aiRepository.analyzeScreenOcrAndElements(
                bitmap = bitmap,
                userGoal = userGoal,
                screenWidth = t.screenWidthPx,
                screenHeight = t.screenHeightPx
            )
            result.fold(
                onSuccess = { analysis ->
                    _ocrState.value = OcrState.Success(analysis)
                },
                onFailure = { err ->
                    _ocrState.value = OcrState.Error(
                        err.localizedMessage ?: "Screen OCR analysis failed."
                    )
                }
            )
        }
    }

    // --- Device Control & ADB Shell ---
    fun runShellCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val (ok, output) = deviceEngine.executeShellCommand(command)
            val elapsed = System.currentTimeMillis() - start
            _shellHistory.value = listOf(command to output) + _shellHistory.value.take(24)
            repository.recordExecutionLog(
                workflowId = "adb_shell",
                workflowTitle = "Shell: ${command.take(40)}",
                status = if (ok) "SUCCESS" else "WARNING",
                summary = "Exit status: ${if (ok) "0 (OK)" else "non-zero"} in ${elapsed}ms",
                commandOutput = "$ $command\n$output",
                durationMs = elapsed
            )
            refreshDeviceTelemetry()
        }
    }

    fun launchInstalledApp(app: InstalledAppItem) {
        val res = deviceEngine.launchAppOrSettings(app.packageName)
        _toastMessage.value = res.getOrNull() ?: "Could not launch ${app.appName}"
    }

    fun openAppInfoForUninstall(app: InstalledAppItem) {
        val res = deviceEngine.openAppDetailsSettings(app.packageName)
        _toastMessage.value = res.getOrNull() ?: "Opened App Info for ${app.appName}"
    }

    // --- Voice-to-Text Workflow Triggering ---
    fun setVoiceAutoExecute(enabled: Boolean) {
        _voiceTriggerStatus.value = _voiceTriggerStatus.value.copy(autoExecuteOnMatch = enabled)
    }

    fun processVoiceCommandForWorkflows(spokenText: String) {
        val cleaned = spokenText.trim()
        if (cleaned.isBlank()) return

        val workflows = (workflowsState.value as? DataUiState.Success)?.data.orEmpty()
        if (workflows.isEmpty()) {
            _voiceTriggerStatus.value = _voiceTriggerStatus.value.copy(
                lastSpokenText = cleaned,
                matchedWorkflowId = null,
                matchedWorkflowTitle = null,
                isMatchSuccess = false,
                statusDetail = "No saved workflows found yet. Load Starter Scripts below or save a workflow first."
            )
            _toastMessage.value = "Voice recognized: \"$cleaned\" — Load or save a workflow first."
            return
        }

        val stopWords = setOf(
            "run", "start", "trigger", "execute", "launch", "open",
            "workflow", "script", "automation", "macro", "please", "the", "my", "now"
        )
        val normalizedSpoken = cleaned.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()
        val spokenTokens = normalizedSpoken
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }

        val strippedPhrase = spokenTokens.joinToString(" ")

        var bestWorkflow: AutomationWorkflow? = null
        var bestScore = 0

        for (wf in workflows) {
            val titleNorm = wf.title.lowercase(Locale.US).replace(Regex("[^a-z0-9\\s]"), " ").trim()
            val descNorm = wf.description.lowercase(Locale.US).replace(Regex("[^a-z0-9\\s]"), " ").trim()
            val stepsCorpus = wf.parsedSteps().joinToString(" ") {
                "${it.actionType.label} ${it.target} ${it.note}"
            }.lowercase(Locale.US)

            var score = 0
            if (strippedPhrase.isNotBlank() && (titleNorm.contains(strippedPhrase) || strippedPhrase.contains(titleNorm))) {
                score += 100
            }
            if (normalizedSpoken.contains(titleNorm)) {
                score += 90
            }

            for (token in spokenTokens) {
                if (titleNorm.contains(token)) score += 35
                if (descNorm.contains(token)) score += 15
                if (stepsCorpus.contains(token)) score += 10
            }

            if (wf.isEnabled && score > 0) {
                score += 5
            }

            if (score > bestScore) {
                bestScore = score
                bestWorkflow = wf
            }
        }

        val matched = if (bestScore >= 15) bestWorkflow else null
        if (matched != null) {
            val autoRun = _voiceTriggerStatus.value.autoExecuteOnMatch
            _voiceTriggerStatus.value = _voiceTriggerStatus.value.copy(
                lastSpokenText = cleaned,
                matchedWorkflowId = matched.id,
                matchedWorkflowTitle = matched.title,
                isMatchSuccess = true,
                statusDetail = if (autoRun) {
                    "Matched '${matched.title}' — Executing ${matched.parsedSteps().size} steps automatically!"
                } else {
                    "Matched '${matched.title}' — Tap Run below to execute."
                }
            )
            if (autoRun) {
                _toastMessage.value = "Voice Triggered: ${matched.title}"
                executeWorkflow(matched)
            } else {
                _toastMessage.value = "Voice Matched: ${matched.title}"
            }
        } else {
            _voiceTriggerStatus.value = _voiceTriggerStatus.value.copy(
                lastSpokenText = cleaned,
                matchedWorkflowId = null,
                matchedWorkflowTitle = null,
                isMatchSuccess = false,
                statusDetail = "No workflow matched \"$cleaned\". Try saying a workflow title like \"Run ${workflows.first().title}\"."
            )
            _toastMessage.value = "No saved workflow matched \"$cleaned\""
        }
    }
}
