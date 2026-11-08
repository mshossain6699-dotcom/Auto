package com.example.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AutomationStep
import com.example.data.model.DeviceTelemetry
import com.example.data.model.StepActionType
import kotlin.math.roundToInt

@Composable
fun UiOcrStudioTab(
    telemetry: DeviceTelemetry,
    isRecordingMacro: Boolean,
    recordedSteps: List<AutomationStep>,
    capturedBitmap: Bitmap?,
    ocrState: OcrState,
    onToggleRecording: () -> Unit,
    onAddStep: (AutomationStep) -> Unit,
    onUpdateSteps: (List<AutomationStep>) -> Unit,
    onRunSingleStep: (AutomationStep) -> Unit,
    onClearSteps: () -> Unit,
    onReplayMacro: () -> Unit,
    onSaveMacroToCloud: (String, String) -> Unit,
    onCaptureCurrentScreenOcr: (Activity?, String) -> Unit,
    onRunOcrOnImportedBitmap: (Bitmap, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var selectedGestureMode by rememberSaveable { mutableStateOf("TAP") }
    var tapX by rememberSaveable { mutableIntStateOf(telemetry.screenWidthPx / 2) }
    var tapY by rememberSaveable { mutableIntStateOf(telemetry.screenHeightPx / 2) }
    var swipeEndX by rememberSaveable { mutableIntStateOf(telemetry.screenWidthPx / 2) }
    var swipeEndY by rememberSaveable { mutableIntStateOf(telemetry.screenHeightPx / 4) }

    var textToType by rememberSaveable { mutableStateOf("") }
    var buttonTextToClick by rememberSaveable { mutableStateOf("") }
    var formFieldsTarget by rememberSaveable { mutableStateOf("email, username") }
    var formFillValue by rememberSaveable { mutableStateOf("alex@autopilot.ai") }

    var macroSaveTitle by rememberSaveable { mutableStateOf("Custom UI Gesture Macro") }
    var macroSchedule by rememberSaveable { mutableStateOf("") }
    var ocrGoalPrompt by rememberSaveable { mutableStateOf("Find all clickable buttons, inputs, and extract screen text") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                if (bmp != null) {
                    onRunOcrOnImportedBitmap(bmp, ocrGoalPrompt)
                }
            } catch (_: Exception) {
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("ui_ocr_studio_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Interactive Coordinate & Gesture Pad + Macro Recorder
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "UI Coordinate & Gesture Studio",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap or drag on the virtual viewport (${telemetry.screenWidthPx}×${telemetry.screenHeightPx})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = onToggleRecording,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isRecordingMacro) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier.testTag("toggle_macro_record_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = if (isRecordingMacro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRecordingMacro) "REC ON" else "Record")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("TAP" to "Tap (X,Y)", "SWIPE" to "Swipe Vector", "TEXT" to "Type / Form").forEach { (mode, label) ->
                            FilterChip(
                                selected = selectedGestureMode == mode,
                                onClick = { selectedGestureMode = mode },
                                label = { Text(label) },
                                modifier = Modifier.testTag("gesture_mode_chip_$mode")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val primaryColor = MaterialTheme.colorScheme.primary
                    val secondaryColor = MaterialTheme.colorScheme.secondary
                    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

                    // Interactive Viewport Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .testTag("interactive_gesture_canvas")
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(telemetry.screenWidthPx, telemetry.screenHeightPx) {
                                    detectTapGestures { offset ->
                                        val normX = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        val normY = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        tapX = (normX * telemetry.screenWidthPx).roundToInt()
                                        tapY = (normY * telemetry.screenHeightPx).roundToInt()
                                        if (isRecordingMacro) {
                                            onAddStep(
                                                AutomationStep(
                                                    actionType = StepActionType.TAP,
                                                    target = "Canvas Tap",
                                                    x = tapX,
                                                    y = tapY
                                                )
                                            )
                                        }
                                    }
                                }
                                .pointerInput(telemetry.screenWidthPx, telemetry.screenHeightPx) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val nx = (start.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            val ny = (start.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            tapX = (nx * telemetry.screenWidthPx).roundToInt()
                                            tapY = (ny * telemetry.screenHeightPx).roundToInt()
                                        },
                                        onDrag = { change, _ ->
                                            val nx = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            val ny = (change.position.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            swipeEndX = (nx * telemetry.screenWidthPx).roundToInt()
                                            swipeEndY = (ny * telemetry.screenHeightPx).roundToInt()
                                        },
                                        onDragEnd = {
                                            if (isRecordingMacro) {
                                                onAddStep(
                                                    AutomationStep(
                                                        actionType = StepActionType.SWIPE,
                                                        target = "Canvas Swipe",
                                                        x = tapX,
                                                        y = tapY,
                                                        endX = swipeEndX,
                                                        endY = swipeEndY,
                                                        delayMs = 450L
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                        ) {
                            val w = size.width
                            val h = size.height
                            for (i in 1..3) {
                                drawLine(
                                    color = gridColor,
                                    start = Offset(w * i / 4f, 0f),
                                    end = Offset(w * i / 4f, h),
                                    strokeWidth = 1f
                                )
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, h * i / 4f),
                                    end = Offset(w, h * i / 4f),
                                    strokeWidth = 1f
                                )
                            }

                            val startPx = Offset(
                                x = (tapX.toFloat() / telemetry.screenWidthPx.coerceAtLeast(1)) * w,
                                y = (tapY.toFloat() / telemetry.screenHeightPx.coerceAtLeast(1)) * h
                            )
                            val endPx = Offset(
                                x = (swipeEndX.toFloat() / telemetry.screenWidthPx.coerceAtLeast(1)) * w,
                                y = (swipeEndY.toFloat() / telemetry.screenHeightPx.coerceAtLeast(1)) * h
                            )

                            if (selectedGestureMode == "SWIPE") {
                                drawLine(
                                    color = secondaryColor,
                                    start = startPx,
                                    end = endPx,
                                    strokeWidth = 6f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                                )
                                drawCircle(color = secondaryColor, radius = 12f, center = endPx)
                            }

                            drawCircle(color = primaryColor.copy(alpha = 0.28f), radius = 26f, center = startPx)
                            drawCircle(color = primaryColor, radius = 10f, center = startPx)
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = if (selectedGestureMode == "SWIPE") {
                                    "Start ($tapX, $tapY) → End ($swipeEndX, $swipeEndY)"
                                } else {
                                    "Target Coordinate: X=$tapX, Y=$tapY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (selectedGestureMode) {
                        "TAP" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = buttonTextToClick,
                                    onValueChange = { buttonTextToClick = it },
                                    label = { Text("Button Text (or leave blank for X,Y tap)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("click_by_text_input")
                                )
                                Button(
                                    onClick = {
                                        if (buttonTextToClick.isNotBlank()) {
                                            onAddStep(
                                                AutomationStep(
                                                    actionType = StepActionType.CLICK_TEXT,
                                                    target = buttonTextToClick,
                                                    x = tapX,
                                                    y = tapY
                                                )
                                            )
                                        } else {
                                            onAddStep(
                                                AutomationStep(
                                                    actionType = StepActionType.TAP,
                                                    target = "Screen Tap",
                                                    x = tapX,
                                                    y = tapY
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("dispatch_tap_step_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (buttonTextToClick.isNotBlank()) "Click Text" else "Tap ($tapX,$tapY)")
                                }
                            }
                        }

                        "SWIPE" -> {
                            Button(
                                onClick = {
                                    onAddStep(
                                        AutomationStep(
                                            actionType = StepActionType.SWIPE,
                                            target = "Swipe Gesture",
                                            x = tapX,
                                            y = tapY,
                                            endX = swipeEndX,
                                            endY = swipeEndY,
                                            delayMs = 500L
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dispatch_swipe_step_button")
                            ) {
                                Text("Dispatch & Add Swipe ($tapX, $tapY) → ($swipeEndX, $swipeEndY)")
                            }
                        }

                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = textToType,
                                        onValueChange = { textToType = it },
                                        label = { Text("Type Text into Focused Input") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("type_text_input")
                                    )
                                    Button(
                                        onClick = {
                                            if (textToType.isNotBlank()) {
                                                onAddStep(
                                                    AutomationStep(
                                                        actionType = StepActionType.TYPE_TEXT,
                                                        target = "Focused Input",
                                                        value = textToType
                                                    )
                                                )
                                                textToType = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .height(54.dp)
                                            .testTag("dispatch_type_text_button")
                                    ) {
                                        Text("Type")
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = formFieldsTarget,
                                        onValueChange = { formFieldsTarget = it },
                                        label = { Text("Form Fields") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = formFillValue,
                                        onValueChange = { formFillValue = it },
                                        label = { Text("Auto-Fill Value") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            onAddStep(
                                                AutomationStep(
                                                    actionType = StepActionType.FILL_FORM,
                                                    target = formFieldsTarget,
                                                    value = formFillValue
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .height(54.dp)
                                            .testTag("dispatch_fill_form_button")
                                    ) {
                                        Text("Fill")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Dual-Mode Automation Script & Block Editor (Visual Blocks + Syntax Highlighted DSL)
        item {
            AutomationScriptEditorCard(
                steps = recordedSteps,
                screenWidthPx = telemetry.screenWidthPx,
                screenHeightPx = telemetry.screenHeightPx,
                onStepsChanged = onUpdateSteps,
                onRunSingleStep = onRunSingleStep,
                onRunAllSteps = { onReplayMacro() },
                onSaveScript = { title, schedule, updatedSteps ->
                    onUpdateSteps(updatedSteps)
                    onSaveMacroToCloud(title, schedule)
                },
                initialTitle = macroSaveTitle,
                initialSchedule = macroSchedule
            )
        }

        // 2. Multimodal Screen OCR & Smart Element Detection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Screen OCR & Smart Element Detector",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Capture the live screen or pick any screenshot to extract text and detect clickable UI coordinates with Gemini Vision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = ocrGoalPrompt,
                        onValueChange = { ocrGoalPrompt = it },
                        label = { Text("OCR & Element Detection Goal") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ocr_goal_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onCaptureCurrentScreenOcr(activity, ocrGoalPrompt) },
                            enabled = ocrState !is OcrState.Analyzing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("capture_screen_ocr_button")
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Capture & OCR")
                        }

                        FilledTonalButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = ocrState !is OcrState.Analyzing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_screenshot_ocr_button")
                        ) {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Screenshot")
                        }
                    }

                    if (capturedBitmap != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Image(
                            bitmap = capturedBitmap.asImageBitmap(),
                            contentDescription = "Captured Screenshot Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        )
                    }

                    when (ocrState) {
                        is OcrState.Analyzing -> {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Running Gemini Multimodal OCR & Element Detection...")
                            }
                        }

                        is OcrState.Error -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ocrState.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        is OcrState.Success -> {
                            val res = ocrState.result
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Context Summary",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = res.screenSummary,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Extracted OCR Text",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = res.extractedText.ifBlank { "No text detected." },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (res.elements.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Smart Detected UI Elements (Tap + to add to Macro)",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        res.elements.forEach { el ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "[${el.elementType}] \"${el.label}\"",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "Coord: (${el.centerX}, ${el.centerY}) • ${el.boundsText}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        onAddStep(
                                                            AutomationStep(
                                                                actionType = StepActionType.CLICK_TEXT,
                                                                target = el.label,
                                                                x = el.centerX,
                                                                y = el.centerY,
                                                                note = "Smart OCR Element (${el.elementType})"
                                                            )
                                                        )
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Element Step", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Automate", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OcrState.Idle -> Unit
                    }
                }
            }
        }
    }
}
