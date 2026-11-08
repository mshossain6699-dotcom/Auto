package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AutomationStep
import com.example.data.model.AutomationWorkflow
import com.example.data.model.DeviceTelemetry
import com.example.data.model.ExecutionLog
import com.example.data.model.SyncedTelemetryLog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AutomationRepository(
    private val db: FirebaseFirestore,
    private val fallbackUserId: String = ""
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(resolveFirestoreDatabaseId(context.applicationContext))
    )

    companion object {
        private const val FALLBACK_DATABASE_ID =
            "ai-studio-android-autopilo-84d0ea9e-e4ac-45c0-af7f-3a99963a3ef6"

        fun resolveFirestoreDatabaseId(context: Context): String {
            val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
            return if (resId != 0) {
                runCatching { context.getString(resId) }.getOrDefault(FALLBACK_DATABASE_ID)
            } else {
                FALLBACK_DATABASE_ID
            }
        }

        private val localWorkflowsState = kotlinx.coroutines.flow.MutableStateFlow<List<AutomationWorkflow>>(
            listOf(
                AutomationWorkflow(
                    id = "local_wf_1",
                    userId = "local_email_default",
                    title = "Battery & RAM Optimizer",
                    description = "Inspects battery health, clears background cache, and logs kernel telemetry.",
                    triggerType = "MANUAL",
                    scheduleCron = "",
                    isEnabled = true,
                    stepsJson = AutomationStep.encodeSteps(
                        listOf(
                            AutomationStep(
                                id = "s1",
                                actionType = com.example.data.model.StepActionType.SHELL_CMD,
                                target = "dumpsys battery",
                                note = "Check battery status"
                            ),
                            AutomationStep(
                                id = "s2",
                                actionType = com.example.data.model.StepActionType.WAIT,
                                value = "800",
                                note = "Wait 800ms"
                            ),
                            AutomationStep(
                                id = "s3",
                                actionType = com.example.data.model.StepActionType.OPEN_APP,
                                target = "com.android.settings",
                                note = "Open system settings"
                            )
                        )
                    ),
                    stepCount = 3L,
                    runCount = 1L,
                    lastStatus = "SUCCESS"
                )
            )
        )
        private val localLogsState = kotlinx.coroutines.flow.MutableStateFlow<List<ExecutionLog>>(emptyList())
        private val localTelemetryState = kotlinx.coroutines.flow.MutableStateFlow<List<SyncedTelemetryLog>>(emptyList())
    }

    private val auth = Firebase.auth

    private fun isLocalEmailId(id: String): Boolean = id.startsWith("local_email_")

    private fun requireUserId(): String {
        auth.currentUser?.uid?.let { return it }
        if (isLocalEmailId(fallbackUserId)) return fallbackUserId
        throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeWorkflows(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<AutomationWorkflow>> = flow {
        val targetUid = userId.ifBlank { auth.currentUser?.uid ?: fallbackUserId.ifBlank { "unauthenticated" } }
        if (isLocalEmailId(targetUid)) {
            emitAll(localWorkflowsState)
            return@flow
        }
        val path = "users/$targetUid/workflows"
        emitAll(
            db.collection("users").document(targetUid).collection("workflows")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AutomationWorkflow::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == targetUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeLogs(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<ExecutionLog>> = flow {
        val targetUid = userId.ifBlank { auth.currentUser?.uid ?: fallbackUserId.ifBlank { "unauthenticated" } }
        if (isLocalEmailId(targetUid)) {
            emitAll(localLogsState)
            return@flow
        }
        val path = "users/$targetUid/logs"
        emitAll(
            db.collection("users").document(targetUid).collection("logs")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ExecutionLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == targetUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeTelemetryLogs(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<SyncedTelemetryLog>> = flow {
        val targetUid = userId.ifBlank { auth.currentUser?.uid ?: fallbackUserId.ifBlank { "unauthenticated" } }
        if (isLocalEmailId(targetUid)) {
            emitAll(localTelemetryState)
            return@flow
        }
        val path = "users/$targetUid/telemetry_logs"
        emitAll(
            db.collection("users").document(targetUid).collection("telemetry_logs")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SyncedTelemetryLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == targetUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createWorkflow(
        title: String,
        description: String,
        triggerType: String,
        scheduleCron: String,
        isEnabled: Boolean,
        steps: List<AutomationStep>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val stepsJson = AutomationStep.encodeSteps(steps).take(14500)
        if (isLocalEmailId(uid)) {
            val newId = "local_wf_${System.currentTimeMillis()}"
            val wf = AutomationWorkflow(
                id = newId,
                userId = uid,
                title = title.trim().take(120).ifBlank { "Untitled Workflow" },
                description = description.trim().take(500),
                triggerType = triggerType.takeIf { it in listOf("MANUAL", "SCHEDULED", "APP_LAUNCH", "AI_PROMPT") }.orEmpty().ifBlank { "MANUAL" },
                scheduleCron = scheduleCron.trim().take(100),
                isEnabled = isEnabled,
                stepsJson = stepsJson,
                stepCount = steps.size.coerceIn(0, 100).toLong(),
                runCount = 0L,
                lastStatus = "IDLE"
            )
            localWorkflowsState.value = listOf(wf) + localWorkflowsState.value
            return@runCatching newId
        }
        val docRef = db.collection("users").document(uid).collection("workflows").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(120).ifBlank { "Untitled Workflow" },
            "description" to description.trim().take(500),
            "triggerType" to triggerType.takeIf { it in listOf("MANUAL", "SCHEDULED", "APP_LAUNCH", "AI_PROMPT") }.orEmpty().ifBlank { "MANUAL" },
            "scheduleCron" to scheduleCron.trim().take(100),
            "isEnabled" to isEnabled,
            "stepsJson" to stepsJson,
            "stepCount" to steps.size.coerceIn(0, 100),
            "runCount" to 0L,
            "lastStatus" to "IDLE",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun getWorkflowById(ownerUid: String, workflowId: String): Result<AutomationWorkflow?> = runCatching {
        if (isLocalEmailId(ownerUid)) {
            return@runCatching localWorkflowsState.value.firstOrNull { it.id == workflowId }
        }
        val docRef = db.collection("users").document(ownerUid).collection("workflows").document(workflowId)
        try {
            val snap = docRef.get().await()
            snap.toObject(AutomationWorkflow::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateWorkflow(workflow: AutomationWorkflow): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(workflow.id.isNotBlank()) { "Workflow ID must not be blank for update" }
        if (isLocalEmailId(uid)) {
            localWorkflowsState.value = localWorkflowsState.value.map {
                if (it.id == workflow.id) workflow else it
            }
            return@runCatching
        }
        val docRef = db.collection("users").document(uid).collection("workflows").document(workflow.id)
        val updates = mapOf(
            "title" to workflow.title.trim().take(120).ifBlank { "Untitled Workflow" },
            "description" to workflow.description.trim().take(500),
            "triggerType" to workflow.triggerType,
            "scheduleCron" to workflow.scheduleCron.trim().take(100),
            "isEnabled" to workflow.isEnabled,
            "stepsJson" to workflow.stepsJson.take(14500),
            "stepCount" to workflow.stepCount.coerceIn(0L, 100L),
            "runCount" to workflow.runCount.coerceAtLeast(0L),
            "lastStatus" to workflow.lastStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteWorkflow(workflowId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (isLocalEmailId(uid)) {
            localWorkflowsState.value = localWorkflowsState.value.filterNot { it.id == workflowId }
            return@runCatching
        }
        val docRef = db.collection("users").document(uid).collection("workflows").document(workflowId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun recordExecutionLog(
        workflowId: String,
        workflowTitle: String,
        status: String,
        summary: String,
        commandOutput: String,
        durationMs: Long
    ): Result<String> = runCatching {
        val uid = requireUserId()
        if (isLocalEmailId(uid)) {
            val logId = "local_log_${System.currentTimeMillis()}"
            val newLog = ExecutionLog(
                id = logId,
                userId = uid,
                workflowId = workflowId.take(128).ifBlank { "adhoc_cmd" },
                workflowTitle = workflowTitle.trim().take(120).ifBlank { "Ad-hoc Command" },
                status = status.takeIf { it in listOf("SUCCESS", "FAILED", "WARNING") }.orEmpty().ifBlank { "SUCCESS" },
                summary = summary.trim().take(1000).ifBlank { "Completed execution." },
                commandOutput = commandOutput.take(5000),
                durationMs = durationMs.coerceIn(0L, 3_600_000L)
            )
            localLogsState.value = listOf(newLog) + localLogsState.value
            return@runCatching logId
        }
        val docRef = db.collection("users").document(uid).collection("logs").document()
        val payload = mapOf(
            "userId" to uid,
            "workflowId" to workflowId.take(128).ifBlank { "adhoc_cmd" },
            "workflowTitle" to workflowTitle.trim().take(120).ifBlank { "Ad-hoc Command" },
            "status" to status.takeIf { it in listOf("SUCCESS", "FAILED", "WARNING") }.orEmpty().ifBlank { "SUCCESS" },
            "summary" to summary.trim().take(1000).ifBlank { "Completed execution." },
            "commandOutput" to commandOutput.take(5000),
            "durationMs" to durationMs.coerceIn(0L, 3_600_000L),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteLog(logId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (isLocalEmailId(uid)) {
            localLogsState.value = localLogsState.value.filterNot { it.id == logId }
            return@runCatching
        }
        val docRef = db.collection("users").document(uid).collection("logs").document(logId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun syncTelemetrySnapshotToCloud(telemetry: DeviceTelemetry): Result<String> = runCatching {
        val uid = requireUserId()
        if (isLocalEmailId(uid)) {
            val tid = "local_tel_${System.currentTimeMillis()}"
            val entry = SyncedTelemetryLog(
                id = tid,
                userId = uid,
                deviceModel = telemetry.deviceModel.trim().take(120).ifBlank { "Android Device" },
                androidVersion = telemetry.androidVersion.trim().take(60).ifBlank { "Android" },
                cpuUsagePercent = telemetry.cpuUsagePercent.roundToLong().coerceIn(0L, 100L),
                batteryPercent = telemetry.batteryPercent.toLong().coerceIn(0L, 100L),
                batteryTempTenthsC = (telemetry.batteryTempC * 10f).roundToLong().coerceIn(0L, 1000L),
                ramUsedMb = telemetry.ramUsedMb.coerceIn(0L, 1_000_000L),
                ramTotalMb = telemetry.ramTotalMb.coerceIn(1L, 1_000_000L),
                networkLatencyMs = telemetry.networkLatencyMs.coerceIn(0L, 60_000L),
                networkTransport = telemetry.networkTransport.trim().take(60).ifBlank { "Wi-Fi" }
            )
            localTelemetryState.value = listOf(entry) + localTelemetryState.value
            return@runCatching tid
        }
        val docRef = db.collection("users").document(uid).collection("telemetry_logs").document()
        val payload = mapOf(
            "userId" to uid,
            "deviceModel" to telemetry.deviceModel.trim().take(120).ifBlank { "Android Device" },
            "androidVersion" to telemetry.androidVersion.trim().take(60).ifBlank { "Android" },
            "cpuUsagePercent" to telemetry.cpuUsagePercent.roundToLong().coerceIn(0L, 100L),
            "batteryPercent" to telemetry.batteryPercent.toLong().coerceIn(0L, 100L),
            "batteryTempTenthsC" to (telemetry.batteryTempC * 10f).roundToLong().coerceIn(0L, 1000L),
            "ramUsedMb" to telemetry.ramUsedMb.coerceIn(0L, 1_000_000L),
            "ramTotalMb" to telemetry.ramTotalMb.coerceIn(1L, 1_000_000L),
            "networkLatencyMs" to telemetry.networkLatencyMs.coerceIn(0L, 60_000L),
            "networkTransport" to telemetry.networkTransport.trim().take(60).ifBlank { "Wi-Fi" },
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTelemetryLog(telemetryId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (isLocalEmailId(uid)) {
            localTelemetryState.value = localTelemetryState.value.filterNot { it.id == telemetryId }
            return@runCatching
        }
        val docRef = db.collection("users").document(uid).collection("telemetry_logs").document(telemetryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
