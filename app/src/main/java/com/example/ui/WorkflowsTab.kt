package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AutomationStep
import com.example.data.model.AutomationWorkflow
import com.example.data.model.DeviceTelemetry
import com.example.data.model.ExecutionLog
import com.example.data.model.StepActionType

@Composable
fun WorkflowsTab(
    telemetry: DeviceTelemetry,
    workflowsState: DataUiState<List<AutomationWorkflow>>,
    logsState: DataUiState<List<ExecutionLog>>,
    voiceTriggerStatus: VoiceTriggerStatus,
    onVoiceTranscriptReceived: (String) -> Unit,
    onToggleVoiceAutoExecute: (Boolean) -> Unit,
    onSeedStarterWorkflows: () -> Unit,
    onSaveEditedWorkflow: (AutomationWorkflow?, String, String, String, String, List<AutomationStep>) -> Unit,
    onExecuteSingleStep: (AutomationStep) -> Unit,
    onExecuteStepsNow: (String, List<AutomationStep>) -> Unit,
    onExecuteWorkflow: (AutomationWorkflow) -> Unit,
    onToggleWorkflowEnabled: (AutomationWorkflow) -> Unit,
    onDeleteWorkflow: (String) -> Unit,
    onDeleteLog: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showScriptIdeModal by remember { mutableStateOf(false) }
    var editingWorkflow by remember { mutableStateOf<AutomationWorkflow?>(null) }

    val savedWorkflows = (workflowsState as? DataUiState.Success)?.data.orEmpty()

    if (showScriptIdeModal) {
        val currentWf = editingWorkflow
        val initialSteps = remember(currentWf) {
            currentWf?.parsedSteps() ?: listOf(
                AutomationStep(
                    id = "ide_1",
                    actionType = StepActionType.OPEN_APP,
                    target = "com.android.settings",
                    note = "Open system settings"
                ),
                AutomationStep(
                    id = "ide_2",
                    actionType = StepActionType.SWIPE,
                    target = "Scroll down",
                    x = telemetry.screenWidthPx / 2,
                    y = (telemetry.screenHeightPx * 0.75).toInt(),
                    endX = telemetry.screenWidthPx / 2,
                    endY = (telemetry.screenHeightPx * 0.25).toInt(),
                    delayMs = 500L,
                    note = "Swipe down to Battery"
                ),
                AutomationStep(
                    id = "ide_3",
                    actionType = StepActionType.TAP,
                    target = "Battery",
                    x = telemetry.screenWidthPx / 2,
                    y = (telemetry.screenHeightPx * 0.45).toInt(),
                    note = "Tap Battery item"
                )
            )
        }

        ScriptEditorModalDialog(
            initialTitle = currentWf?.title ?: "",
            initialDescription = currentWf?.description ?: "",
            initialTriggerType = currentWf?.triggerType ?: "MANUAL",
            initialScheduleCron = currentWf?.scheduleCron ?: "",
            initialSteps = initialSteps,
            screenWidthPx = telemetry.screenWidthPx,
            screenHeightPx = telemetry.screenHeightPx,
            onDismiss = {
                showScriptIdeModal = false
                editingWorkflow = null
            },
            onRunStep = onExecuteSingleStep,
            onRunAllSteps = onExecuteStepsNow,
            onSaveWorkflow = { title, desc, trigger, cron, steps ->
                onSaveEditedWorkflow(currentWf, title, desc, trigger, cron, steps)
                showScriptIdeModal = false
                editingWorkflow = null
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("workflows_tab_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Voice-to-Text Workflow Trigger Hub
        item {
            VoiceWorkflowTriggerCard(
                savedWorkflows = savedWorkflows,
                voiceStatus = voiceTriggerStatus,
                onVoiceTranscriptReceived = onVoiceTranscriptReceived,
                onToggleAutoExecute = onToggleVoiceAutoExecute,
                onRunMatchedWorkflow = onExecuteWorkflow
            )
        }

        // 2. Workflows & Script IDE Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Workflows & Script IDE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Author visual block chains or syntax-highlighted gesture scripts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        editingWorkflow = null
                        showScriptIdeModal = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("new_workflow_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Script IDE")
                }
            }
        }

        when (workflowsState) {
            is DataUiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is DataUiState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = workflowsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            is DataUiState.Success -> {
                val workflows = workflowsState.data
                if (workflows.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No Cloud Workflows Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Open the Script IDE to build custom gesture blocks/code, or load pre-built starter scripts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                FilledTonalButton(
                                    onClick = onSeedStarterWorkflows,
                                    modifier = Modifier.testTag("seed_starter_workflows_button")
                                ) {
                                    Text("Load Starter Automation Scripts")
                                }
                            }
                        }
                    }
                } else {
                    items(workflows, key = { it.id.ifBlank { it.title } }) { wf ->
                        WorkflowItemCard(
                            workflow = wf,
                            onEditScript = {
                                editingWorkflow = wf
                                showScriptIdeModal = true
                            },
                            onRun = { onExecuteWorkflow(wf) },
                            onToggleEnabled = { onToggleWorkflowEnabled(wf) },
                            onDelete = { onDeleteWorkflow(wf.id) }
                        )
                    }
                }
            }
        }

        // Execution Logs section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Execution History & Shell Traces",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        when (logsState) {
            is DataUiState.Loading -> {
                item {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            is DataUiState.Error -> {
                item {
                    Text(
                        text = logsState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is DataUiState.Success -> {
                val logs = logsState.data
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "No execution logs recorded yet. Run any workflow or ADB shell command to inspect output traces.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(logs.take(15), key = { it.id.ifBlank { it.summary + it.durationMs } }) { log ->
                        ExecutionLogCard(
                            log = log,
                            onDelete = { onDeleteLog(log.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowItemCard(
    workflow: AutomationWorkflow,
    onEditScript: () -> Unit,
    onRun: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    val steps = remember(workflow.stepsJson) { workflow.parsedSteps() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workflow_card_${workflow.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = workflow.triggerType,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        if (workflow.scheduleCron.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = workflow.scheduleCron,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = "• Runs: ${workflow.runCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = workflow.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            if (workflow.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = workflow.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            steps.take(4).forEachIndexed { idx, step ->
                Text(
                    text = "${idx + 1}. [${step.actionType.badge}] ${step.summaryText()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (steps.size > 4) {
                Text(
                    text = "+ ${steps.size - 4} more chained actions...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_workflow_${workflow.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Workflow",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onEditScript,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("edit_workflow_script_${workflow.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Script")
                    }

                    Button(
                        onClick = onRun,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.testTag("run_workflow_${workflow.id}")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run (${steps.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionLogCard(
    log: ExecutionLog,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.workflowTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${log.status} • ${log.durationMs}ms • ${log.summary}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (log.status == "SUCCESS") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Log",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (log.commandOutput.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(10.dp)
                ) {
                    Text(
                        text = log.commandOutput,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
