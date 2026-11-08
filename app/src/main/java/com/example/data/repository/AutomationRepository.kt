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

class AutomationRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeWorkflows(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<AutomationWorkflow>> = flow {
        val targetUid = userId.ifBlank { auth.currentUser?.uid ?: "unauthenticated" }
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
        val targetUid = userId.ifBlank { auth.currentUser?.uid ?: "unauthenticated" }
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
        val targetUid = userId.ifBlank { auth.currentUser?.uid ?: "unauthenticated" }
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
        val docRef = db.collection("users").document(uid).collection("workflows").document()
        val stepsJson = AutomationStep.encodeSteps(steps).take(14500)
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
