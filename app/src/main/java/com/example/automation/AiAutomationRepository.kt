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
        val currentUid = auth.currentUser?.uid
        if (currentUid != null) {
            return currentUid
        } else if (isLocalEmailId(fallbackUserId)) {
            return fallbackUserId
        } else {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
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
