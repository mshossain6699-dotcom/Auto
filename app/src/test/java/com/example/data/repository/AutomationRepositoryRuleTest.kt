package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AutomationStep
import com.example.data.model.DeviceTelemetry
import com.example.data.model.StepActionType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AutomationRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private val sampleSteps = listOf(
        AutomationStep(
            id = "s1",
            actionType = StepActionType.OPEN_APP,
            target = "com.android.settings",
            note = "Open system settings"
        ),
        AutomationStep(
            id = "s2",
            actionType = StepActionType.TAP,
            target = "Battery",
            x = 540,
            y = 920
        )
    )

    @Test
    fun createAndObserveWorkflow_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = AutomationRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createWorkflow(
                title = "Battery Diagnostic Macro",
                description = "Opens settings and inspects battery telemetry",
                triggerType = "MANUAL",
                scheduleCron = "",
                isEnabled = true,
                steps = sampleSteps
            )
        }
        assertTrue("Expected workflow creation to succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val workflowId = createResult.getOrThrow()
        assertTrue(workflowId.isNotEmpty())

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWorkflows(aliceUid).first { list -> list.any { it.id == workflowId } }
        }
        assertTrue(emitted.any { it.id == workflowId && it.title == "Battery Diagnostic Macro" })
    }

    @Test
    fun getWorkflowById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = AutomationRepository(firestore)
        val workflowId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createWorkflow(
                title = "Private Alice Script",
                description = "Secret automation",
                triggerType = "SCHEDULED",
                scheduleCron = "09:00 Daily",
                isEnabled = true,
                steps = sampleSteps
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = AutomationRepository(firestore)
        val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getWorkflowById(aliceUid, workflowId)
        }
        assertTrue("Expected cross-user read to fail", bobReadResult.isFailure)
        val fsEx = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observeWorkflows_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = AutomationRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeWorkflows(aliceUid).first()
            }
            fail("Expected unauthenticated observeWorkflows to throw exception")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun recordAndObserveExecutionLog_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = AutomationRepository(firestore)

        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordExecutionLog(
                workflowId = "wf_test_1",
                workflowTitle = "Battery Diagnostic Macro",
                status = "SUCCESS",
                summary = "Executed 2 steps in 410ms",
                commandOutput = "> input tap 540 920\nOK",
                durationMs = 410L
            ).getOrThrow()
        }
        assertTrue(logId.isNotEmpty())

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLogs(aliceUid).first { list -> list.any { it.id == logId } }
        }
        assertTrue(logs.any { it.id == logId && it.status == "SUCCESS" })
    }

    @Test
    fun syncAndObserveTelemetryLog_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = AutomationRepository(firestore)

        val telemetryId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.syncTelemetrySnapshotToCloud(
                DeviceTelemetry(
                    deviceModel = "Google Pixel 9 Pro",
                    androidVersion = "Android 16",
                    cpuUsagePercent = 31f,
                    batteryPercent = 92,
                    batteryTempC = 30.5f,
                    ramUsedMb = 4100L,
                    ramTotalMb = 8192L,
                    networkLatencyMs = 19L,
                    networkTransport = "Wi-Fi"
                )
            ).getOrThrow()
        }
        assertTrue(telemetryId.isNotEmpty())

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTelemetryLogs(aliceUid).first { list -> list.any { it.id == telemetryId } }
        }
        assertTrue(logs.any { it.id == telemetryId && it.deviceModel == "Google Pixel 9 Pro" })
    }

    private companion object {
        const val ALICE_EMAIL = "alice@autopilot.test"
        const val BOB_EMAIL = "bob@autopilot.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
