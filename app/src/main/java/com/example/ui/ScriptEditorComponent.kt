package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AutomationStep
import com.example.data.model.StepActionType
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonEmerald
import kotlin.math.roundToInt

data class DslParseResult(
    val steps: List<AutomationStep>,
    val warnings: List<String>
)

object AutoPilotDslCodec {

    private val coordPairRegex = Regex("""\(\s*(\d+)\s*,\s*(\d+)\s*\)""")
    private val swipeRegex = Regex("""\(\s*(\d+)\s*,\s*(\d+)\s*\)\s*->\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)""")
    private val durationRegex = Regex("""(?:duration=|delay=)?(\d+)\s*ms""", RegexOption.IGNORE_CASE)
    private val quotedStringRegex = Regex(""""([^"]*)"""")
    private val keyValQuotedRegex = Regex("""(target|value|into)=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

    fun stepsToDsl(steps: List<AutomationStep>): String {
        if (steps.isEmpty()) {
            return "# AutoPilot Gesture & Tap Script\n# Use Visual Blocks or type commands below"
        }
        val sb = StringBuilder()
        sb.appendLine("# AutoPilot Gesture & Tap Sequence (${steps.size} steps)")
        steps.forEach { step ->
            val commentSuffix = step.note.takeIf { it.isNotBlank() }?.let { " # $it" }.orEmpty()
            val line = when (step.actionType) {
                StepActionType.TAP -> {
                    val targetAttr = step.target.takeIf { it.isNotBlank() }?.let { """ target="$it"""" }.orEmpty()
                    "TAP (${step.x}, ${step.y})$targetAttr$commentSuffix"
                }
                StepActionType.SWIPE -> {
                    val targetAttr = step.target.takeIf { it.isNotBlank() && it != "Swipe Gesture" }?.let { """ target="$it"""" }.orEmpty()
                    "SWIPE (${step.x}, ${step.y}) -> (${step.endX}, ${step.endY}) duration=${step.delayMs}ms$targetAttr$commentSuffix"
                }
                StepActionType.CLICK_TEXT -> {
                    """CLICK_TEXT "${step.target}" at (${step.x}, ${step.y})$commentSuffix"""
                }
                StepActionType.TYPE_TEXT -> {
                    val intoPart = step.target.takeIf { it.isNotBlank() }?.let { """ into="$it"""" }.orEmpty()
                    """TYPE_TEXT "${step.value.ifBlank { step.target }}"$intoPart$commentSuffix"""
                }
                StepActionType.FILL_FORM -> {
                    """FILL_FORM "${step.target.ifBlank { "form" }}" value="${step.value}"$commentSuffix"""
                }
                StepActionType.OPEN_APP -> {
                    """OPEN_APP "${step.target}"$commentSuffix"""
                }
                StepActionType.SCREENSHOT_OCR -> {
                    """SCREENSHOT_OCR "${step.target.ifBlank { "Full Screen" }}"$commentSuffix"""
                }
                StepActionType.SHELL_CMD -> {
                    """SHELL_CMD "${step.target.ifBlank { step.value }}"$commentSuffix"""
                }
                StepActionType.WAIT -> {
                    "WAIT ${step.delayMs}ms$commentSuffix"
                }
            }
            sb.appendLine(line)
        }
        return sb.toString().trimEnd()
    }

    fun parseDsl(dslText: String, defaultWidth: Int = 1080, defaultHeight: Int = 2400): DslParseResult {
        val steps = mutableListOf<AutomationStep>()
        val warnings = mutableListOf<String>()

        dslText.lines().forEachIndexed { idx, rawLine ->
            val lineNum = idx + 1
            val commentSplit = rawLine.split("#", limit = 2)
            val codePart = commentSplit[0].trim()
            val notePart = commentSplit.getOrNull(1)?.trim().orEmpty()

            if (codePart.isEmpty()) return@forEachIndexed

            val firstToken = codePart.substringBefore(" ").trim().uppercase()
            val rest = codePart.substringAfter(" ", "").trim()

            val kvMatches = keyValQuotedRegex.findAll(rest).associate {
                it.groupValues[1].lowercase() to it.groupValues[2]
            }
            val firstQuoted = quotedStringRegex.find(rest)?.groupValues?.get(1).orEmpty()

            when (firstToken) {
                "TAP" -> {
                    val m = coordPairRegex.find(rest)
                    val x = m?.groupValues?.get(1)?.toIntOrNull() ?: (defaultWidth / 2)
                    val y = m?.groupValues?.get(2)?.toIntOrNull() ?: (defaultHeight / 2)
                    if (m == null) {
                        warnings.add("Line $lineNum: TAP missing (x, y), defaulting to ($x, $y)")
                    }
                    val target = kvMatches["target"] ?: firstQuoted
                    steps.add(
                        AutomationStep(
                            id = "dsl_${lineNum}_${System.nanoTime()}",
                            actionType = StepActionType.TAP,
                            target = target,
                            x = x,
                            y = y,
                            note = notePart
                        )
                    )
                }

                "SWIPE" -> {
                    val sm = swipeRegex.find(rest)
                    val coords = coordPairRegex.findAll(rest).toList()
                    val sx = sm?.groupValues?.get(1)?.toIntOrNull()
                        ?: coords.getOrNull(0)?.groupValues?.get(1)?.toIntOrNull()
                        ?: (defaultWidth / 2)
                    val sy = sm?.groupValues?.get(2)?.toIntOrNull()
                        ?: coords.getOrNull(0)?.groupValues?.get(2)?.toIntOrNull()
                        ?: (defaultHeight * 3 / 4)
                    val ex = sm?.groupValues?.get(3)?.toIntOrNull()
                        ?: coords.getOrNull(1)?.groupValues?.get(1)?.toIntOrNull()
                        ?: (defaultWidth / 2)
                    val ey = sm?.groupValues?.get(4)?.toIntOrNull()
                        ?: coords.getOrNull(1)?.groupValues?.get(2)?.toIntOrNull()
                        ?: (defaultHeight / 4)
                    val dur = durationRegex.find(rest)?.groupValues?.get(1)?.toLongOrNull() ?: 550L
                    if (sm == null && coords.size < 2) {
                        warnings.add("Line $lineNum: SWIPE expects (x1, y1) -> (x2, y2)")
                    }
                    steps.add(
                        AutomationStep(
                            id = "dsl_${lineNum}_${System.nanoTime()}",
                            actionType = StepActionType.SWIPE,
                            target = kvMatches["target"] ?: "Swipe Gesture",
                            x = sx,
                            y = sy,
                            endX = ex,
                            endY = ey,
                            delayMs = dur,
                            note = notePart
                        )
                    )
                }

                "CLICK_TEXT", "CLICK" -> {
                    val cm = coordPairRegex.find(rest)
                    val x = cm?.groupValues?.get(1)?.toIntOrNull() ?: (defaultWidth / 2)
                    val y = cm?.groupValues?.get(2)?.toIntOrNull() ?: (defaultHeight / 2)
                    val label = firstQuoted.ifBlank { rest.substringBefore("at").trim() }
                    steps.add(
                        AutomationStep(
                            id = "dsl_${lineNum}_${System.nanoTime()}",
                            actionType = StepActionType.CLICK_TEXT,
                            target = label.ifBlank { "Button" },
                            x = x,
                            y = y,
                            note = notePart
                        )
                    )
                }

                "TYPE_TEXT", "TYPE" -> {
                    val textVal = firstQuoted.ifBlank { rest }
                    val intoTarget = kvMatches["into"] ?: kvMatches["target"] ?: "Focused Input"
                    steps.add(
                        AutomationStep(
                            id = "dsl_${lineNum}_${System.nanoTime()}",
                            actionType = StepActionType.TYPE_TEXT,
                            target = intoTarget,
                            value = textVal,
                            note = notePart
                        )
                    )
                }

                "FILL_FORM", "FILL" -> {
                    val formTarget = firstQuoted.ifBlank { "form_fields" }
                    val fillVal = kvMatches["value"]
                        ?: quotedStringRegex.findAll(rest).drop(1).firstOrNull()?.groupValues?.get(1)
                        ?: ""
                    steps.add(
                        AutomationStep(
                            id = "dsl_${lineNum}_${System.nanoTime()}",
                            actionType = StepActionType.FILL_FORM,
                            target = formTarget,
                            value = fillVal,
                            note = notePart
                        )
                    )
                }

                "OPEN_APP", "LAUNCH" -> {
                    val pkg = firstQuoted.ifBlank { rest }
                    steps.add(
                        AutomationStep(
                            id = "dsl_${lineNum}_${System.nanoTime()}",
                            actionType = StepActionType.OPEN_APP,
                            target = pkg.ifBlank { "com.android.settings" },
                            note = notePart
                        )
                    )
                }

                "SCREENSHOT_OCR", "OCR" -> {
                    val region = firstQuoted.ifBlank { rest.ifBlank { "Full Screen" } }
                    steps.add(
                        AutomationStep(
                            id = "dsl_${lineNum}_${System.nanoTime()}",
                            actionType = StepActionType.SCREENSHOT_OCR,
                            target = region,
                            note = notePart
                        )
                    )
                }

                "SHELL_CMD", "SHELL", "ADB" -> {
                    val cmd = firstQuoted.ifBlank { rest }
                    steps.add(
                        AutomationStep(
                            id = "dsl_${lineNum}_${System.nanoTime()}",
                            actionType = StepActionType.SHELL_CMD,
                            target = cmd.ifBlank { "getprop ro.product.model" },
                            note = notePart
                        )
                    )
                }

                "WAIT", "SLEEP", "DELAY" -> {
                    val dur = durationRegex.find(rest)?.groupValues?.get(1)?.toLongOrNull()
                        ?: rest.filter { it.isDigit() }.toLongOrNull()
                        ?: 800L
                    steps.add(
                        AutomationStep(
                            id = "dsl_${lineNum}_${System.nanoTime()}",
                            actionType = StepActionType.WAIT,
                            delayMs = dur,
                            note = notePart
                        )
                    )
                }

                else -> {
                    warnings.add("Line $lineNum: Unrecognized command '$firstToken'")
                }
            }
        }

        return DslParseResult(steps = steps, warnings = warnings)
    }
}

class DslSyntaxHighlightTransformation : VisualTransformation {

    private val keywordRegex = Regex(
        """\b(TAP|SWIPE|CLICK_TEXT|CLICK|TYPE_TEXT|TYPE|FILL_FORM|FILL|OPEN_APP|LAUNCH|SCREENSHOT_OCR|OCR|SHELL_CMD|SHELL|ADB|WAIT|SLEEP|DELAY)\b""",
        RegexOption.IGNORE_CASE
    )
    private val propertyRegex = Regex("""\b(target|value|into|duration|delay|at)\b|->""")
    private val numberCoordRegex = Regex("""\(\s*\d+\s*,\s*\d+\s*\)|\b\d+\s*ms\b|\b\d+\b""")
    private val stringLiteralRegex = Regex(""""[^"\n]*"""")
    private val commentRegex = Regex("""#.*$""", RegexOption.MULTILINE)

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder(raw)

        // 1. Properties & arrows (Violet)
        propertyRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(color = Color(0xFFC084FC), fontWeight = FontWeight.SemiBold),
                match.range.first,
                match.range.last + 1
            )
        }

        // 2. Coordinates & numbers (Neon Emerald)
        numberCoordRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(color = NeonEmerald, fontWeight = FontWeight.Medium),
                match.range.first,
                match.range.last + 1
            )
        }

        // 3. Keywords (Electric Cyan Bold)
        keywordRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(color = ElectricCyan, fontWeight = FontWeight.Bold),
                match.range.first,
                match.range.last + 1
            )
        }

        // 4. String literals (Amber Gold)
        stringLiteralRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(color = AmberPulse),
                match.range.first,
                match.range.last + 1
            )
        }

        // 5. Comments (Muted Italic Slate — applied last so comments override tokens inside them)
        commentRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(color = Color(0xFF64748B), fontStyle = FontStyle.Italic),
                match.range.first,
                match.range.last + 1
            )
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun AutomationScriptEditorCard(
    steps: List<AutomationStep>,
    screenWidthPx: Int,
    screenHeightPx: Int,
    onStepsChanged: (List<AutomationStep>) -> Unit,
    onRunSingleStep: (AutomationStep) -> Unit,
    onRunAllSteps: (List<AutomationStep>) -> Unit,
    onSaveScript: ((String, String, List<AutomationStep>) -> Unit)? = null,
    initialTitle: String = "Custom Gesture & Tap Script",
    initialSchedule: String = "",
    modifier: Modifier = Modifier
) {
    var editorMode by rememberSaveable { mutableStateOf("BLOCKS") } // "BLOCKS" or "CODE"
    var dslText by remember(steps) { mutableStateOf(AutoPilotDslCodec.stepsToDsl(steps)) }
    var dslWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingStepIndex by rememberSaveable { mutableStateOf(-1) }
    var showVisualPathMap by rememberSaveable { mutableStateOf(true) }
    var scriptTitle by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var scriptSchedule by rememberSaveable(initialSchedule) { mutableStateOf(initialSchedule) }

    val syntaxTransformation = remember { DslSyntaxHighlightTransformation() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("automation_script_editor_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header & Mode Switcher (Blocks vs Code DSL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automation Script & Block Editor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Build custom gesture & tap chains with visual blocks or syntax-highlighted DSL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Toggle Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = editorMode == "BLOCKS",
                        onClick = {
                            if (editorMode == "CODE") {
                                val parsed = AutoPilotDslCodec.parseDsl(dslText, screenWidthPx, screenHeightPx)
                                dslWarnings = parsed.warnings
                                onStepsChanged(parsed.steps)
                            }
                            editorMode = "BLOCKS"
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ViewAgenda,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Visual Blocks (${steps.size})") },
                        modifier = Modifier.testTag("script_editor_mode_blocks")
                    )

                    FilterChip(
                        selected = editorMode == "CODE",
                        onClick = {
                            dslText = AutoPilotDslCodec.stepsToDsl(steps)
                            dslWarnings = emptyList()
                            editorMode = "CODE"
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Syntax DSL Code") },
                        modifier = Modifier.testTag("script_editor_mode_code")
                    )
                }

                FilterChip(
                    selected = showVisualPathMap,
                    onClick = { showVisualPathMap = !showVisualPathMap },
                    label = { Text("Path Map", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("toggle_sequence_path_map")
                )
            }

            // Interactive Sequence Trajectory Canvas (shows numbered tap & swipe nodes)
            AnimatedVisibility(visible = showVisualPathMap) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    SequencePathPreviewCanvas(
                        steps = steps,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        onCanvasTapAddStep = { newStep ->
                            val updated = steps + newStep
                            onStepsChanged(updated)
                            dslText = AutoPilotDslCodec.stepsToDsl(updated)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Insert Palette
            Text(
                text = "QUICK INSERT BLOCK / SNIPPET",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickBlocks = listOf(
                    "+ TAP" to AutomationStep(
                        id = "step_${System.nanoTime()}",
                        actionType = StepActionType.TAP,
                        target = "Primary Button",
                        x = screenWidthPx / 2,
                        y = (screenHeightPx * 0.65).roundToInt(),
                        note = "Tap screen coordinate"
                    ),
                    "+ SWIPE ↑" to AutomationStep(
                        id = "step_${System.nanoTime()}",
                        actionType = StepActionType.SWIPE,
                        target = "Scroll Down",
                        x = screenWidthPx / 2,
                        y = (screenHeightPx * 0.75).roundToInt(),
                        endX = screenWidthPx / 2,
                        endY = (screenHeightPx * 0.25).roundToInt(),
                        delayMs = 500L,
                        note = "Swipe up to scroll"
                    ),
                    "+ SWIPE →" to AutomationStep(
                        id = "step_${System.nanoTime()}",
                        actionType = StepActionType.SWIPE,
                        target = "Horizontal Pager",
                        x = (screenWidthPx * 0.82).roundToInt(),
                        y = screenHeightPx / 2,
                        endX = (screenWidthPx * 0.18).roundToInt(),
                        endY = screenHeightPx / 2,
                        delayMs = 420L,
                        note = "Swipe next page"
                    ),
                    "+ CLICK TEXT" to AutomationStep(
                        id = "step_${System.nanoTime()}",
                        actionType = StepActionType.CLICK_TEXT,
                        target = "Continue",
                        x = screenWidthPx / 2,
                        y = (screenHeightPx * 0.8).roundToInt(),
                        note = "Click element by label"
                    ),
                    "+ TYPE TEXT" to AutomationStep(
                        id = "step_${System.nanoTime()}",
                        actionType = StepActionType.TYPE_TEXT,
                        target = "search_input",
                        value = "AutoPilot query",
                        note = "Type into active field"
                    ),
                    "+ WAIT 1s" to AutomationStep(
                        id = "step_${System.nanoTime()}",
                        actionType = StepActionType.WAIT,
                        delayMs = 1000L,
                        note = "Wait for UI animation"
                    ),
                    "+ OPEN APP" to AutomationStep(
                        id = "step_${System.nanoTime()}",
                        actionType = StepActionType.OPEN_APP,
                        target = "com.android.settings",
                        note = "Launch package"
                    ),
                    "+ OCR" to AutomationStep(
                        id = "step_${System.nanoTime()}",
                        actionType = StepActionType.SCREENSHOT_OCR,
                        target = "Active Viewport",
                        note = "Extract screen text"
                    )
                )

                quickBlocks.forEachIndexed { idx, (label, templateStep) ->
                    AssistChip(
                        onClick = {
                            val freshStep = templateStep.copy(id = "step_${System.nanoTime()}")
                            val updated = steps + freshStep
                            onStepsChanged(updated)
                            dslText = AutoPilotDslCodec.stepsToDsl(updated)
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier.testTag("quick_insert_block_$idx")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (editorMode == "BLOCKS") {
                // VISUAL BLOCKS EDITOR
                if (steps.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No gesture or tap blocks yet.",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap any chip above or click/drag on the Path Map canvas to add blocks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        steps.forEachIndexed { index, step ->
                            VisualStepBlockItem(
                                index = index,
                                totalCount = steps.size,
                                step = step,
                                isExpanded = editingStepIndex == index,
                                onToggleExpand = {
                                    editingStepIndex = if (editingStepIndex == index) -1 else index
                                },
                                onMoveUp = {
                                    if (index > 0) {
                                        val mutable = steps.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index - 1, item)
                                        onStepsChanged(mutable)
                                    }
                                },
                                onMoveDown = {
                                    if (index < steps.lastIndex) {
                                        val mutable = steps.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index + 1, item)
                                        onStepsChanged(mutable)
                                    }
                                },
                                onDuplicate = {
                                    val mutable = steps.toMutableList()
                                    mutable.add(index + 1, step.copy(id = "dup_${System.nanoTime()}"))
                                    onStepsChanged(mutable)
                                },
                                onDelete = {
                                    val mutable = steps.toMutableList()
                                    mutable.removeAt(index)
                                    if (editingStepIndex == index) editingStepIndex = -1
                                    onStepsChanged(mutable)
                                },
                                onUpdateStep = { updatedStep ->
                                    val mutable = steps.toMutableList()
                                    mutable[index] = updatedStep
                                    onStepsChanged(mutable)
                                },
                                onTestSingleStep = { onRunSingleStep(step) }
                            )
                        }
                    }
                }
            } else {
                // SYNTAX-HIGHLIGHTED DSL CODE EDITOR
                Column {
                    val lineCount = dslText.lines().size.coerceAtLeast(1)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background( Color(0xFF070A12))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Line Numbers Gutter
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(end = 10.dp)
                            ) {
                                for (i in 1..lineCount) {
                                    Text(
                                        text = i.toString().padStart(2, '0'),
                                        style = TextStyle(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 12.sp,
                                            lineHeight = 20.sp,
                                            color = Color(0xFF475569)
                                        )
                                    )
                                }
                            }

                            // Syntax Highlighted Code Field
                            BasicTextField(
                                value = dslText,
                                onValueChange = { newText ->
                                    dslText = newText
                                    val parsed = AutoPilotDslCodec.parseDsl(newText, screenWidthPx, screenHeightPx)
                                    dslWarnings = parsed.warnings
                                    onStepsChanged(parsed.steps)
                                },
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 20.sp,
                                    color = Color(0xFFF1F5F9)
                                ),
                                cursorBrush = SolidColor(ElectricCyan),
                                visualTransformation = syntaxTransformation,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dsl_code_editor_input")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live DSL Parser Status Bar
                    Surface(
                        color = if (dslWarnings.isEmpty()) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (dslWarnings.isEmpty()) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (dslWarnings.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (dslWarnings.isEmpty()) {
                                    "Valid AutoPilot DSL • ${steps.size} executable steps synced"
                                } else {
                                    dslWarnings.first()
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (dslWarnings.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // Optional Save & Execute Footer
            if (steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                if (onSaveScript != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = scriptTitle,
                            onValueChange = { scriptTitle = it },
                            label = { Text("Script Title") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("script_editor_title_input")
                        )
                        OutlinedTextField(
                            value = scriptSchedule,
                            onValueChange = { scriptSchedule = it },
                            label = { Text("Schedule (opt)") },
                            placeholder = { Text("08:00 Daily") },
                            singleLine = true,
                            modifier = Modifier.weight(0.75f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onSaveScript != null) {
                        FilledTonalButton(
                            onClick = { onSaveScript(scriptTitle, scriptSchedule, steps) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("script_editor_save_button")
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Script")
                        }
                    }

                    Button(
                        onClick = { onRunAllSteps(steps) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("script_editor_run_button")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Sequence (${steps.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SequencePathPreviewCanvas(
    steps: List<AutomationStep>,
    screenWidthPx: Int,
    screenHeightPx: Int,
    onCanvasTapAddStep: (AutomationStep) -> Unit
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(175.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070A12))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("script_editor_path_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(screenWidthPx, screenHeightPx) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val x = ((offset.x / w).coerceIn(0f, 1f) * screenWidthPx).roundToInt()
                        val y = ((offset.y / h).coerceIn(0f, 1f) * screenHeightPx).roundToInt()
                        onCanvasTapAddStep(
                            AutomationStep(
                                id = "tap_${System.nanoTime()}",
                                actionType = StepActionType.TAP,
                                target = "Point (${steps.size + 1})",
                                x = x,
                                y = y,
                                note = "Added from Path Map"
                            )
                        )
                    }
                }
                .pointerInput(screenWidthPx, screenHeightPx) {
                    detectDragGestures(
                        onDragStart = { start ->
                            dragStart = start
                            dragCurrent = start
                        },
                        onDrag = { change, _ ->
                            dragCurrent = change.position
                        },
                        onDragEnd = {
                            val s = dragStart
                            val e = dragCurrent
                            if (s != null && e != null) {
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val sx = ((s.x / w).coerceIn(0f, 1f) * screenWidthPx).roundToInt()
                                val sy = ((s.y / h).coerceIn(0f, 1f) * screenHeightPx).roundToInt()
                                val ex = ((e.x / w).coerceIn(0f, 1f) * screenWidthPx).roundToInt()
                                val ey = ((e.y / h).coerceIn(0f, 1f) * screenHeightPx).roundToInt()
                                onCanvasTapAddStep(
                                    AutomationStep(
                                        id = "swipe_${System.nanoTime()}",
                                        actionType = StepActionType.SWIPE,
                                        target = "Swipe Path",
                                        x = sx,
                                        y = sy,
                                        endX = ex,
                                        endY = ey,
                                        delayMs = 500L,
                                        note = "Dragged on Path Map"
                                    )
                                )
                            }
                            dragStart = null
                            dragCurrent = null
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val gridColor = Color(0xFF1E293B)

            for (i in 1..3) {
                drawLine(gridColor, Offset(w * i / 4f, 0f), Offset(w * i / 4f, h), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), strokeWidth = 1f)
            }

            // Draw existing tap & swipe steps
            var prevPoint: Offset? = null
            steps.forEach { step ->
                val sx = (step.x.toFloat() / screenWidthPx.coerceAtLeast(1)) * w
                val sy = (step.y.toFloat() / screenHeightPx.coerceAtLeast(1)) * h
                val pt = Offset(sx.coerceIn(0f, w), sy.coerceIn(0f, h))

                if (prevPoint != null && (step.actionType == StepActionType.TAP || step.actionType == StepActionType.CLICK_TEXT || step.actionType == StepActionType.SWIPE)) {
                    drawLine(
                        color = ElectricCyan.copy(alpha = 0.3f),
                        start = prevPoint!!,
                        end = pt,
                        strokeWidth = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                }

                when (step.actionType) {
                    StepActionType.TAP, StepActionType.CLICK_TEXT -> {
                        drawCircle(color = ElectricCyan.copy(alpha = 0.25f), radius = 18f, center = pt)
                        drawCircle(color = ElectricCyan, radius = 7f, center = pt)
                        prevPoint = pt
                    }

                    StepActionType.SWIPE -> {
                        val ex = (step.endX.toFloat() / screenWidthPx.coerceAtLeast(1)) * w
                        val ey = (step.endY.toFloat() / screenHeightPx.coerceAtLeast(1)) * h
                        val endPt = Offset(ex.coerceIn(0f, w), ey.coerceIn(0f, h))
                        drawLine(
                            color = NeonEmerald,
                            start = pt,
                            end = endPt,
                            strokeWidth = 5f
                        )
                        drawCircle(color = ElectricCyan, radius = 6f, center = pt)
                        drawCircle(color = NeonEmerald, radius = 9f, center = endPt)
                        prevPoint = endPt
                    }

                    else -> Unit
                }
            }

            // Active drag preview
            val ds = dragStart
            val dc = dragCurrent
            if (ds != null && dc != null) {
                drawLine(
                    color = AmberPulse,
                    start = ds,
                    end = dc,
                    strokeWidth = 5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                )
                drawCircle(color = AmberPulse, radius = 8f, center = dc)
            }
        }

        Surface(
            color = Color(0xCC0B0F19),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = "Tap canvas to append TAP • Drag to append SWIPE • Cyan=Tap, Emerald=Swipe",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun VisualStepBlockItem(
    index: Int,
    totalCount: Int,
    step: AutomationStep,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onUpdateStep: (AutomationStep) -> Unit,
    onTestSingleStep: () -> Unit
) {
    val blockAccent = when (step.actionType) {
        StepActionType.TAP, StepActionType.CLICK_TEXT -> ElectricCyan
        StepActionType.SWIPE -> NeonEmerald
        StepActionType.TYPE_TEXT, StepActionType.FILL_FORM -> AmberPulse
        StepActionType.OPEN_APP -> Color(0xFFC084FC)
        StepActionType.SCREENSHOT_OCR -> Color(0xFF38BDF8)
        StepActionType.SHELL_CMD -> Color(0xFFF472B6)
        StepActionType.WAIT -> Color(0xFF94A3B8)
    }

    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = blockAccent.copy(alpha = if (isExpanded) 0.75f else 0.35f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("visual_step_block_$index")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Number + Type Pill
                Surface(
                    color = blockAccent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${index + 1} • ${step.actionType.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = blockAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.summaryText(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (step.note.isNotBlank()) {
                        Text(
                            text = "# ${step.note}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onTestSingleStep, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Test Block",
                        tint = NeonEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Block Parameters",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove Block",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expandable Parameter Editor + Reorder Controls
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action Type Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StepActionType.entries.forEach { type ->
                            FilterChip(
                                selected = step.actionType == type,
                                onClick = { onUpdateStep(step.copy(actionType = type)) },
                                label = { Text(type.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Target / Value / Coordinate Editors depending on step type
                    when (step.actionType) {
                        StepActionType.TAP, StepActionType.CLICK_TEXT -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = step.x.toString(),
                                    onValueChange = { v ->
                                        onUpdateStep(step.copy(x = v.filter { it.isDigit() }.toIntOrNull() ?: 0))
                                    },
                                    label = { Text("X (px)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = step.y.toString(),
                                    onValueChange = { v ->
                                        onUpdateStep(step.copy(y = v.filter { it.isDigit() }.toIntOrNull() ?: 0))
                                    },
                                    label = { Text("Y (px)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = step.target,
                                    onValueChange = { onUpdateStep(step.copy(target = it)) },
                                    label = { Text("Element Text / Label") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.6f)
                                )
                            }
                        }

                        StepActionType.SWIPE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = step.x.toString(),
                                    onValueChange = { v ->
                                        onUpdateStep(step.copy(x = v.filter { it.isDigit() }.toIntOrNull() ?: 0))
                                    },
                                    label = { Text("Start X") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = step.y.toString(),
                                    onValueChange = { v ->
                                        onUpdateStep(step.copy(y = v.filter { it.isDigit() }.toIntOrNull() ?: 0))
                                    },
                                    label = { Text("Start Y") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = step.endX.toString(),
                                    onValueChange = { v ->
                                        onUpdateStep(step.copy(endX = v.filter { it.isDigit() }.toIntOrNull() ?: 0))
                                    },
                                    label = { Text("End X") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = step.endY.toString(),
                                    onValueChange = { v ->
                                        onUpdateStep(step.copy(endY = v.filter { it.isDigit() }.toIntOrNull() ?: 0))
                                    },
                                    label = { Text("End Y") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = step.delayMs.toString(),
                                onValueChange = { v ->
                                    onUpdateStep(step.copy(delayMs = v.filter { it.isDigit() }.toLongOrNull() ?: 400L))
                                },
                                label = { Text("Swipe Duration (ms)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StepActionType.TYPE_TEXT, StepActionType.FILL_FORM -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = step.target,
                                    onValueChange = { onUpdateStep(step.copy(target = it)) },
                                    label = { Text("Target Field / Selector") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = step.value,
                                    onValueChange = { onUpdateStep(step.copy(value = it)) },
                                    label = { Text("Text / Payload to Type") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.2f)
                                )
                            }
                        }

                        StepActionType.WAIT -> {
                            OutlinedTextField(
                                value = step.delayMs.toString(),
                                onValueChange = { v ->
                                    onUpdateStep(step.copy(delayMs = v.filter { it.isDigit() }.toLongOrNull() ?: 500L))
                                },
                                label = { Text("Wait / Delay (ms)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        else -> {
                            OutlinedTextField(
                                value = step.target,
                                onValueChange = { onUpdateStep(step.copy(target = it)) },
                                label = { Text("Package Name / Shell Command / OCR Region") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OutlinedTextField(
                        value = step.note,
                        onValueChange = { onUpdateStep(step.copy(note = it)) },
                        label = { Text("Step Comment (# note)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Block Order & Duplicate Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onMoveUp,
                                enabled = index > 0,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Up", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onMoveDown,
                                enabled = index < totalCount - 1,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Down", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        OutlinedButton(
                            onClick = onDuplicate,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Duplicate Block", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Duplicate", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScriptEditorModalDialog(
    initialTitle: String,
    initialDescription: String,
    initialTriggerType: String,
    initialScheduleCron: String,
    initialSteps: List<AutomationStep>,
    screenWidthPx: Int,
    screenHeightPx: Int,
    onDismiss: () -> Unit,
    onRunStep: (AutomationStep) -> Unit,
    onRunAllSteps: (String, List<AutomationStep>) -> Unit,
    onSaveWorkflow: (String, String, String, String, List<AutomationStep>) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var triggerType by rememberSaveable { mutableStateOf(initialTriggerType.ifBlank { "MANUAL" }) }
    var scheduleCron by rememberSaveable { mutableStateOf(initialScheduleCron) }
    var workingSteps by remember { mutableStateOf(initialSteps) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Dialog Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automation Script IDE",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Visual Blocks + Syntax-Highlighted AutoPilot DSL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Script IDE")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Workflow Title") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ide_workflow_title_input")
                        )
                        OutlinedTextField(
                            value = scheduleCron,
                            onValueChange = { scheduleCron = it },
                            label = { Text("Schedule (opt)") },
                            placeholder = { Text("08:30 Daily") },
                            singleLine = true,
                            modifier = Modifier.weight(0.75f)
                        )
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Workflow Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("MANUAL", "SCHEDULED", "APP_LAUNCH", "AI_PROMPT").forEach { mode ->
                            FilterChip(
                                selected = triggerType == mode,
                                onClick = { triggerType = mode },
                                label = { Text(mode, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    AutomationScriptEditorCard(
                        steps = workingSteps,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        onStepsChanged = { workingSteps = it },
                        onRunSingleStep = onRunStep,
                        onRunAllSteps = { stepsToRun ->
                            onRunAllSteps(title.ifBlank { "IDE Script Run" }, stepsToRun)
                        },
                        onSaveScript = null
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onSaveWorkflow(
                                title.ifBlank { "Custom Gesture Script" },
                                description.ifBlank { "Created in Automation Script IDE" },
                                triggerType,
                                scheduleCron,
                                workingSteps
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("ide_save_workflow_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Script (${workingSteps.size} Steps)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
