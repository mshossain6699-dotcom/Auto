package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

enum class StepActionType(val label: String, val badge: String) {
    OPEN_APP("Launch App", "APP"),
    TAP("Tap Coordinate", "UI"),
    CLICK_TEXT("Click Element by Text", "SMART"),
    SWIPE("Swipe Gesture", "UI"),
    TYPE_TEXT("Type / Input Text", "INPUT"),
    FILL_FORM("Auto-Fill Form Fields", "FORM"),
    SCREENSHOT_OCR("Capture & Extract OCR", "OCR"),
    SHELL_CMD("Execute ADB / Shell", "ADB"),
    WAIT("Wait / Delay", "FLOW");

    companion object {
        fun fromName(name: String): StepActionType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TAP
        }
    }
}

data class AutomationStep(
    val id: String = "",
    val actionType: StepActionType = StepActionType.TAP,
    val target: String = "",
    val value: String = "",
    val x: Int = 540,
    val y: Int = 1200,
    val endX: Int = 540,
    val endY: Int = 400,
    val delayMs: Long = 800L,
    val note: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id.ifBlank { "step_${System.nanoTime()}" })
        put("actionType", actionType.name)
        put("target", target)
        put("value", value)
        put("x", x)
        put("y", y)
        put("endX", endX)
        put("endY", endY)
        put("delayMs", delayMs)
        put("note", note)
    }

    fun summaryText(): String = when (actionType) {
        StepActionType.OPEN_APP -> "Open ${target.ifBlank { "App" }}"
        StepActionType.TAP -> "Tap ($x, $y) ${if (target.isNotBlank()) "on '$target'" else ""}".trim()
        StepActionType.CLICK_TEXT -> "Click button/text \"$target\""
        StepActionType.SWIPE -> "Swipe ($x, $y) → ($endX, $endY)"
        StepActionType.TYPE_TEXT -> "Type \"${value.ifBlank { target }}\""
        StepActionType.FILL_FORM -> "Fill form [${target.ifBlank { "fields" }}] = \"${value}\""
        StepActionType.SCREENSHOT_OCR -> "Capture screen & extract OCR (${target.ifBlank { "Full Screen" }})"
        StepActionType.SHELL_CMD -> "ADB Shell: ${target.ifBlank { value }}"
        StepActionType.WAIT -> "Wait ${delayMs}ms ${note.takeIf { it.isNotBlank() }?.let { "($it)" } ?: ""}".trim()
    }

    companion object {
        fun fromJson(obj: JSONObject): AutomationStep {
            return AutomationStep(
                id = obj.optString("id", "step_${System.nanoTime()}"),
                actionType = StepActionType.fromName(obj.optString("actionType", "TAP")),
                target = obj.optString("target", ""),
                value = obj.optString("value", ""),
                x = obj.optInt("x", 540),
                y = obj.optInt("y", 1200),
                endX = obj.optInt("endX", 540),
                endY = obj.optInt("endY", 400),
                delayMs = obj.optLong("delayMs", 800L),
                note = obj.optString("note", "")
            )
        }

        fun encodeSteps(steps: List<AutomationStep>): String {
            val arr = JSONArray()
            steps.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun decodeSteps(json: String): List<AutomationStep> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { idx ->
                    arr.optJSONObject(idx)?.let { fromJson(it) }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

@Keep
@IgnoreExtraProperties
data class AutomationWorkflow(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val triggerType: String = "MANUAL",
    val scheduleCron: String = "",
    val isEnabled: Boolean = true,
    val stepsJson: String = "[]",
    val stepCount: Long = 0L,
    val runCount: Long = 0L,
    val lastStatus: String = "IDLE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedSteps(): List<AutomationStep> = AutomationStep.decodeSteps(stepsJson)
}

@Keep
@IgnoreExtraProperties
data class ExecutionLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workflowId: String = "",
    val workflowTitle: String = "",
    val status: String = "SUCCESS",
    val summary: String = "",
    val commandOutput: String = "",
    val durationMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SyncedTelemetryLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val cpuUsagePercent: Long = 0L,
    val batteryPercent: Long = 100L,
    val batteryTempTenthsC: Long = 290L,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 1L,
    val networkLatencyMs: Long = 0L,
    val networkTransport: String = "Wi-Fi",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val batteryTempC: Float
        get() = batteryTempTenthsC / 10f
}

data class DeviceTelemetry(
    val deviceModel: String = "",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 36,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempC: Float = 29.0f,
    val batteryVoltageMv: Int = 4150,
    val batteryCurrentMa: Int = 0,
    val cpuUsagePercent: Float = 12.0f,
    val cpuCores: Int = 8,
    val activeThreads: Int = 24,
    val networkLatencyMs: Long = 18L,
    val networkTransport: String = "Wi-Fi / Bridge",
    val downLinkMbps: Int = 100,
    val rxKbps: Float = 0f,
    val txKbps: Float = 0f,
    val storageUsedGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val screenWidthPx: Int = 1080,
    val screenHeightPx: Int = 2400,
    val densityDpi: Int = 420,
    val uptimeMinutes: Long = 0L,
    val adbBridgeMode: String = "Local Shell + Accessibility Bridge Ready",
    val installedPackageCount: Int = 0
) {
    val ramUsagePercent: Float
        get() = ((ramUsedMb.toFloat() / ramTotalMb.coerceAtLeast(1L)) * 100f).coerceIn(0f, 100f)
}

data class TelemetrySample(
    val timestampMs: Long = System.currentTimeMillis(),
    val labelTime: String = "",
    val cpuUsagePercent: Float = 0f,
    val batteryPercent: Int = 100,
    val batteryTempC: Float = 29f,
    val batteryVoltageMv: Int = 4150,
    val ramUsagePercent: Float = 50f,
    val networkLatencyMs: Long = 20L,
    val rxKbps: Float = 0f,
    val txKbps: Float = 0f
)

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val versionName: String
)

data class DetectedUiElement(
    val label: String,
    val elementType: String,
    val boundsText: String,
    val centerX: Int,
    val centerY: Int,
    val confidence: Float = 0.96f,
    val suggestedAction: String = "TAP"
)

