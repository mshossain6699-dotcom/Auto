package com.example.automation

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AutomationStep
import com.example.data.model.DetectedUiElement
import com.example.data.model.DeviceTelemetry
import com.example.data.model.StepActionType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiAutomationRepo"

data class GeneratedWorkflowPlan(
    val title: String,
    val description: String,
    val triggerType: String,
    val scheduleCron: String,
    val reasoning: String,
    val steps: List<AutomationStep>
)

data class ScreenOcrAnalysisResult(
    val extractedText: String,
    val screenSummary: String,
    val elements: List<DetectedUiElement>,
    val suggestedSteps: List<AutomationStep>
)

class AiAutomationRepository {

    private val workflowSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short descriptive workflow title (max 80 chars)"),
            "description" to Schema.string(description = "Clear summary of what this mobile automation workflow does"),
            "triggerType" to Schema.enumeration(
                values = listOf("MANUAL", "SCHEDULED", "APP_LAUNCH", "AI_PROMPT"),
                description = "Recommended trigger mode"
            ),
            "scheduleCron" to Schema.string(description = "Human-readable schedule like '08:00 Daily' or empty string if manual"),
            "reasoning" to Schema.string(description = "Context-aware explanation of how the steps accomplish the user's goal"),
            "steps" to Schema.array(
                description = "Ordered list of mobile automation actions",
                items = Schema.obj(
                    properties = mapOf(
                        "actionType" to Schema.enumeration(
                            values = listOf(
                                "OPEN_APP",
                                "TAP",
                                "CLICK_TEXT",
                                "SWIPE",
                                "TYPE_TEXT",
                                "FILL_FORM",
                                "SCREENSHOT_OCR",
                                "SHELL_CMD",
                                "WAIT"
                            )
                        ),
                        "target" to Schema.string(description = "Package name, button text, form field name, or shell command"),
                        "value" to Schema.string(description = "Text to type, form value, or secondary parameter"),
                        "x" to Schema.integer(description = "Screen X coordinate in pixels (0..1080)"),
                        "y" to Schema.integer(description = "Screen Y coordinate in pixels (0..2400)"),
                        "endX" to Schema.integer(description = "Swipe end X coordinate in pixels"),
                        "endY" to Schema.integer(description = "Swipe end Y coordinate in pixels"),
                        "delayMs" to Schema.integer(description = "Duration or wait delay in milliseconds"),
                        "note" to Schema.string(description = "Brief explanation of this step")
                    )
                )
            )
        )
    )

    private val ocrSchema = Schema.obj(
        properties = mapOf(
            "extractedText" to Schema.string(description = "All visible text extracted from the screenshot via OCR"),
            "screenSummary" to Schema.string(description = "Context-aware summary of the current screen state and active app UI"),
            "elements" to Schema.array(
                description = "Interactive UI elements, buttons, inputs, and labels detected on screen",
                items = Schema.obj(
                    properties = mapOf(
                        "label" to Schema.string(description = "Visible text or accessibility label of the UI element"),
                        "elementType" to Schema.enumeration(
                            values = listOf("BUTTON", "INPUT_FIELD", "TOGGLE", "CARD", "ICON", "TEXT_LABEL")
                        ),
                        "boundsText" to Schema.string(description = "Approximate bounding region description"),
                        "centerX" to Schema.integer(description = "Estimated tap X coordinate on a 1080px wide screen"),
                        "centerY" to Schema.integer(description = "Estimated tap Y coordinate on a 2400px tall screen"),
                        "suggestedAction" to Schema.enumeration(
                            values = listOf("TAP", "CLICK_TEXT", "TYPE_TEXT", "SWIPE")
                        )
                    )
                )
            ),
            "suggestedSteps" to Schema.array(
                description = "Recommended next automation steps based on the screen context",
                items = Schema.obj(
                    properties = mapOf(
                        "actionType" to Schema.enumeration(
                            values = listOf("TAP", "CLICK_TEXT", "TYPE_TEXT", "FILL_FORM", "SWIPE", "WAIT")
                        ),
                        "target" to Schema.string(),
                        "value" to Schema.string(),
                        "x" to Schema.integer(),
                        "y" to Schema.integer(),
                        "note" to Schema.string()
                    )
                )
            )
        )
    )

    private val workflowGeneratorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = workflowSchema
            }
        )
    }

    private val screenOcrModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = ocrSchema
            }
        )
    }

    private val chatAssistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    suspend fun generateWorkflowFromNaturalLanguage(
        userPrompt: String,
        telemetry: DeviceTelemetry
    ): Result<GeneratedWorkflowPlan> {
        return try {
            val fullPrompt = """
                You are an expert Android Mobile Automation Architect for AutoPilot AI.
                Device Context:
                - Device: ${telemetry.deviceModel} (${telemetry.androidVersion}, SDK ${telemetry.sdkInt})
                - Display Resolution: ${telemetry.screenWidthPx}x${telemetry.screenHeightPx} (${telemetry.densityDpi} dpi)
                - Battery: ${telemetry.batteryPercent}% (Charging: ${telemetry.isCharging})

                Convert the following natural language user request into a complete, practical multi-step mobile automation workflow:
                "$userPrompt"

                Use realistic coordinates within ${telemetry.screenWidthPx}x${telemetry.screenHeightPx}, real Android package names or settings intents where applicable, and clear step notes.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                workflowGeneratorModel.generateContent(fullPrompt)
            }
            val jsonString = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty workflow response."))

            val json = JSONObject(jsonString)
            val stepsArray = json.optJSONArray("steps")
            val parsedSteps = mutableListOf<AutomationStep>()
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    val item = stepsArray.optJSONObject(i) ?: continue
                    parsedSteps.add(
                        AutomationStep(
                            id = "ai_step_${i + 1}_${System.nanoTime()}",
                            actionType = StepActionType.fromName(item.optString("actionType", "TAP")),
                            target = item.optString("target", ""),
                            value = item.optString("value", ""),
                            x = item.optInt("x", telemetry.screenWidthPx / 2),
                            y = item.optInt("y", telemetry.screenHeightPx / 2),
                            endX = item.optInt("endX", telemetry.screenWidthPx / 2),
                            endY = item.optInt("endY", telemetry.screenHeightPx / 4),
                            delayMs = item.optLong("delayMs", 800L),
                            note = item.optString("note", "")
                        )
                    )
                }
            }

            Result.success(
                GeneratedWorkflowPlan(
                    title = json.optString("title", "AI Generated Automation"),
                    description = json.optString("description", userPrompt),
                    triggerType = json.optString("triggerType", "AI_PROMPT"),
                    scheduleCron = json.optString("scheduleCron", ""),
                    reasoning = json.optString("reasoning", ""),
                    steps = parsedSteps
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Workflow generation timed out", e)
            Result.failure(IllegalStateException("AI workflow generation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate workflow from natural language", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeScreenOcrAndElements(
        bitmap: Bitmap,
        userGoal: String,
        screenWidth: Int,
        screenHeight: Int
    ): Result<ScreenOcrAnalysisResult> {
        return try {
            val promptText = """
                Analyze this Android screen capture (target device resolution: ${screenWidth}x${screenHeight}).
                1. Perform complete OCR text extraction of all visible labels, headers, buttons, and status text.
                2. Detect interactive UI elements with estimated center (X, Y) coordinates scaled to ${screenWidth}x${screenHeight}.
                3. Suggest actionable mobile automation steps to accomplish: "${userGoal.ifBlank { "Inspect and automate primary actions on this screen" }}".
            """.trimIndent()

            val response = withTimeout(60_000L) {
                screenOcrModel.generateContent(
                    content {
                        image(bitmap)
                        text(promptText)
                    }
                )
            }
            val jsonString = response.text
                ?: return Result.failure(IllegalStateException("Model returned an empty OCR analysis response."))

            val json = JSONObject(jsonString)
            val elementsArr = json.optJSONArray("elements")
            val elements = mutableListOf<DetectedUiElement>()
            if (elementsArr != null) {
                for (i in 0 until elementsArr.length()) {
                    val obj = elementsArr.optJSONObject(i) ?: continue
                    elements.add(
                        DetectedUiElement(
                            label = obj.optString("label", "Element ${i + 1}"),
                            elementType = obj.optString("elementType", "BUTTON"),
                            boundsText = obj.optString("boundsText", "Screen region"),
                            centerX = obj.optInt("centerX", screenWidth / 2),
                            centerY = obj.optInt("centerY", screenHeight / 2),
                            suggestedAction = obj.optString("suggestedAction", "TAP")
                        )
                    )
                }
            }

            val stepsArr = json.optJSONArray("suggestedSteps")
            val suggestedSteps = mutableListOf<AutomationStep>()
            if (stepsArr != null) {
                for (i in 0 until stepsArr.length()) {
                    val obj = stepsArr.optJSONObject(i) ?: continue
                    suggestedSteps.add(
                        AutomationStep(
                            id = "ocr_step_${i + 1}_${System.nanoTime()}",
                            actionType = StepActionType.fromName(obj.optString("actionType", "TAP")),
                            target = obj.optString("target", ""),
                            value = obj.optString("value", ""),
                            x = obj.optInt("x", screenWidth / 2),
                            y = obj.optInt("y", screenHeight / 2),
                            note = obj.optString("note", "Detected from screen OCR")
                        )
                    )
                }
            }

            Result.success(
                ScreenOcrAnalysisResult(
                    extractedText = json.optString("extractedText", ""),
                    screenSummary = json.optString("screenSummary", ""),
                    elements = elements,
                    suggestedSteps = suggestedSteps
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Screen OCR analysis timed out", e)
            Result.failure(IllegalStateException("Screen OCR & element analysis timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Screen OCR analysis failed", e)
            Result.failure(e)
        }
    }

    fun streamAutomationAssistant(prompt: String, deviceSummary: String): Flow<String> = flow {
        val fullPrompt = """
            You are AutoPilot AI, a concise, technical Android Mobile Automation Assistant.
            Device State: $deviceSummary
            User Request: $prompt
            Provide actionable ADB shell commands, UI coordinate strategies, or workflow scripting advice.
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chatAssistantModel.generateContentStream(fullPrompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
    }
}
