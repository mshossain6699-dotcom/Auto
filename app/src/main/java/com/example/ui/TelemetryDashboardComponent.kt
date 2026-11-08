package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DeviceTelemetry
import com.example.data.model.TelemetrySample
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import kotlin.math.roundToInt

@Composable
fun RealtimeTelemetryDashboardCard(
    telemetry: DeviceTelemetry,
    history: List<TelemetrySample>,
    isLiveStreaming: Boolean,
    samplingIntervalMs: Long,
    onToggleLiveStreaming: () -> Unit,
    onChangeSamplingInterval: (Long) -> Unit,
    onRefreshAndPingNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCpuSeries by rememberSaveable { mutableStateOf(true) }
    var showRamSeries by rememberSaveable { mutableStateOf(true) }
    var showBatterySeries by rememberSaveable { mutableStateOf(true) }
    var selectedSampleIndex by remember { mutableIntStateOf(-1) }

    val samples = if (history.isEmpty()) {
        listOf(
            TelemetrySample(
                timestampMs = System.currentTimeMillis(),
                labelTime = "NOW",
                cpuUsagePercent = telemetry.cpuUsagePercent,
                batteryPercent = telemetry.batteryPercent,
                batteryTempC = telemetry.batteryTempC,
                batteryVoltageMv = telemetry.batteryVoltageMv,
                ramUsagePercent = telemetry.ramUsagePercent,
                networkLatencyMs = telemetry.networkLatencyMs,
                rxKbps = telemetry.rxKbps,
                txKbps = telemetry.txKbps
            )
        )
    } else {
        history
    }

    val activeSample = samples.getOrNull(selectedSampleIndex.coerceIn(0, samples.lastIndex))
        ?: samples.last()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("telemetry_dashboard_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header & Live Stream Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isLiveStreaming) NeonEmerald else AmberPulse)
                        )
                        Text(
                            text = "Real-Time Telemetry Dashboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${telemetry.deviceModel} • ${telemetry.androidVersion} (API ${telemetry.sdkInt}) • ${samples.size} pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onToggleLiveStreaming,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("toggle_telemetry_stream_button")
                    ) {
                        Icon(
                            imageVector = if (isLiveStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isLiveStreaming) "Pause Stream" else "Resume Stream",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLiveStreaming) "Live" else "Paused",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    IconButton(
                        onClick = onRefreshAndPingNow,
                        modifier = Modifier.testTag("refresh_telemetry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sample & Ping Now"
                        )
                    }
                }
            }

            // Sampling Interval Selector Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Poll Rate:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1000L to "1s Fast", 2000L to "2s Normal", 5000L to "5s Eco").forEach { (ms, label) ->
                        FilterChip(
                            selected = samplingIntervalMs == ms,
                            onClick = { onChangeSamplingInterval(ms) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("poll_rate_${ms}")
                        )
                    }
                }
            }

            // 2. Radial Arc Gauges Row (CPU Load, Battery Health, System RAM)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadialTelemetryGauge(
                    title = "CPU Load",
                    valueText = "${telemetry.cpuUsagePercent.roundToInt()}%",
                    subtitle = "${telemetry.cpuCores} Cores • ${telemetry.activeThreads} thr",
                    progress = (telemetry.cpuUsagePercent / 100f).coerceIn(0f, 1f),
                    accentColor = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                RadialTelemetryGauge(
                    title = "Battery",
                    valueText = "${telemetry.batteryPercent}%",
                    subtitle = "${telemetry.batteryTempC}°C • ${telemetry.batteryVoltageMv}mV",
                    progress = (telemetry.batteryPercent / 100f).coerceIn(0f, 1f),
                    accentColor = NeonEmerald,
                    modifier = Modifier.weight(1f)
                )
                RadialTelemetryGauge(
                    title = "RAM Used",
                    valueText = "${telemetry.ramUsagePercent.roundToInt()}%",
                    subtitle = "${telemetry.ramUsedMb}/${telemetry.ramTotalMb}MB",
                    progress = (telemetry.ramUsagePercent / 100f).coerceIn(0f, 1f),
                    accentColor = AmberPulse,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Multi-Series Interactive Bezier Area Chart (CPU % vs RAM % vs Battery %)
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "System Utilization Time-Series (0–100%)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Touch or drag across chart to inspect historical samples",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Interactive Legend Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SeriesLegendPill(
                            label = "CPU ${activeSample.cpuUsagePercent.roundToInt()}%",
                            color = ElectricCyan,
                            enabled = showCpuSeries,
                            onClick = { showCpuSeries = !showCpuSeries }
                        )
                        SeriesLegendPill(
                            label = "RAM ${activeSample.ramUsagePercent.roundToInt()}%",
                            color = AmberPulse,
                            enabled = showRamSeries,
                            onClick = { showRamSeries = !showRamSeries }
                        )
                        SeriesLegendPill(
                            label = "BAT ${activeSample.batteryPercent}%",
                            color = NeonEmerald,
                            enabled = showBatterySeries,
                            onClick = { showBatterySeries = !showBatterySeries }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Interactive Recharts/D3-style Tooltip Banner
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sample @ ${activeSample.labelTime}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "CPU ${activeSample.cpuUsagePercent}% • RAM ${activeSample.ramUsagePercent}% • ${activeSample.batteryTempC}°C",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Multi-Series Smooth Bezier Area Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                            .testTag("utilization_area_chart")
                            .pointerInput(samples.size) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val ratio = (offset.x / w).coerceIn(0f, 1f)
                                    selectedSampleIndex = (ratio * (samples.size - 1)).roundToInt()
                                }
                            }
                            .pointerInput(samples.size) {
                                detectDragGestures(
                                    onDragEnd = { selectedSampleIndex = -1 },
                                    onDragCancel = { selectedSampleIndex = -1 }
                                ) { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val ratio = (change.position.x / w).coerceIn(0f, 1f)
                                    selectedSampleIndex = (ratio * (samples.size - 1)).roundToInt()
                                }
                            }
                    ) {
                        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawGridLines(gridColor, rows = 4)

                            if (showBatterySeries) {
                                drawSmoothAreaSeries(
                                    values = samples.map { it.batteryPercent.toFloat() },
                                    maxValue = 100f,
                                    lineColor = NeonEmerald,
                                    fillTopAlpha = 0.18f
                                )
                            }
                            if (showRamSeries) {
                                drawSmoothAreaSeries(
                                    values = samples.map { it.ramUsagePercent },
                                    maxValue = 100f,
                                    lineColor = AmberPulse,
                                    fillTopAlpha = 0.24f
                                )
                            }
                            if (showCpuSeries) {
                                drawSmoothAreaSeries(
                                    values = samples.map { it.cpuUsagePercent },
                                    maxValue = 100f,
                                    lineColor = ElectricCyan,
                                    fillTopAlpha = 0.32f
                                )
                            }

                            // Active Crosshair & Data Nodes
                            val highlightIdx = if (selectedSampleIndex in samples.indices) {
                                selectedSampleIndex
                            } else {
                                samples.lastIndex
                            }
                            if (samples.isNotEmpty() && highlightIdx >= 0) {
                                val stepX = if (samples.size > 1) size.width / (samples.size - 1) else size.width / 2f
                                val x = if (samples.size > 1) highlightIdx * stepX else size.width / 2f

                                drawLine(
                                    color = highlightColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                )

                                val sample = samples[highlightIdx]
                                if (showCpuSeries) {
                                    val y = size.height - (sample.cpuUsagePercent / 100f).coerceIn(0f, 1f) * size.height
                                    drawDataNode(Offset(x, y), ElectricCyan)
                                }
                                if (showRamSeries) {
                                    val y = size.height - (sample.ramUsagePercent / 100f).coerceIn(0f, 1f) * size.height
                                    drawDataNode(Offset(x, y), AmberPulse)
                                }
                                if (showBatterySeries) {
                                    val y = size.height - (sample.batteryPercent / 100f).coerceIn(0f, 1f) * size.height
                                    drawDataNode(Offset(x, y), NeonEmerald)
                                }
                            }
                        }

                        // Y-Axis Labels Overlay
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("100%", "50%", "0%").forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Network Latency (RTT ms) & Throughput (RX/TX KB/s) Dual Chart
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Network Latency & Traffic Throughput",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${telemetry.networkTransport} • Link ${telemetry.downLinkMbps} Mbps • ↓${telemetry.rxKbps} KB/s ↑${telemetry.txKbps} KB/s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val rttColor = when {
                            telemetry.networkLatencyMs < 45L -> NeonEmerald
                            telemetry.networkLatencyMs < 100L -> AmberPulse
                            else -> CoralAlert
                        }
                        Surface(
                            color = rttColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${telemetry.networkLatencyMs} ms RTT",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = rttColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val maxLatency = (samples.maxOfOrNull { it.networkLatencyMs } ?: 100L)
                        .coerceAtLeast(120L)
                        .toFloat()
                    val maxThroughput = (samples.maxOfOrNull { it.rxKbps + it.txKbps } ?: 64f)
                        .coerceAtLeast(32f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("network_latency_chart")
                    ) {
                        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawGridLines(gridColor, rows = 3)

                            // Draw RX + TX throughput bars at the base
                            val barCount = samples.size.coerceAtLeast(1)
                            val slotWidth = size.width / barCount
                            val barWidth = (slotWidth * 0.55f).coerceIn(4f, 22f)

                            samples.forEachIndexed { idx, s ->
                                val totalKbps = s.rxKbps + s.txKbps
                                val barHeight = ((totalKbps / maxThroughput).coerceIn(0.03f, 0.75f)) * size.height
                                val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                                drawRoundRect(
                                    color = NeonEmerald.copy(alpha = 0.28f),
                                    topLeft = Offset(left, size.height - barHeight),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )
                            }

                            // Draw Smooth Latency Line + Area on top
                            drawSmoothAreaSeries(
                                values = samples.map { it.networkLatencyMs.toFloat() },
                                maxValue = maxLatency,
                                lineColor = ElectricCyan,
                                fillTopAlpha = 0.25f
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Cyan Curve: TCP RTT (max ${maxLatency.roundToInt()}ms)",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            Text(
                                text = "Bars: Traffic KB/s",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonEmerald
                            )
                        }
                    }
                }
            }

            // 5. Secondary Hardware Summary Strip (Storage, Thermal, Current Draw, Display)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val storageRatio = (telemetry.storageUsedGb / telemetry.storageTotalGb.coerceAtLeast(1f)).coerceIn(0f, 1f)
                CompactHardwareStatTile(
                    title = "Data Storage",
                    value = "${telemetry.storageUsedGb}/${telemetry.storageTotalGb} GB",
                    subtitle = "${(storageRatio * 100).roundToInt()}% utilized • ${telemetry.installedPackageCount} pkgs",
                    progress = storageRatio,
                    color = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                val thermalProgress = ((telemetry.batteryTempC - 20f) / 30f).coerceIn(0.1f, 1f)
                CompactHardwareStatTile(
                    title = "Thermal & Power",
                    value = "${telemetry.batteryTempC}°C • ${telemetry.batteryVoltageMv}mV",
                    subtitle = "Current: ${telemetry.batteryCurrentMa} mA • Up ${telemetry.uptimeMinutes}m",
                    progress = thermalProgress,
                    color = if (telemetry.batteryTempC >= 40f) CoralAlert else NeonEmerald,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RadialTelemetryGauge(
    title: String,
    valueText: String,
    subtitle: String,
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "gauge_progress"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(74.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 7.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

                    drawArc(
                        color = trackColor,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    drawArc(
                        color = accentColor,
                        startAngle = 135f,
                        sweepAngle = 270f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SeriesLegendPill(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (enabled) color else color.copy(alpha = 0.35f))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactHardwareStatTile(
    title: String,
    value: String,
    subtitle: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = color,
                trackColor = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun DrawScope.drawGridLines(gridColor: Color, rows: Int) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
    for (i in 0..rows) {
        val y = (size.height / rows) * i
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )
    }
}

private fun DrawScope.drawSmoothAreaSeries(
    values: List<Float>,
    maxValue: Float,
    lineColor: Color,
    fillTopAlpha: Float
) {
    if (values.isEmpty()) return
    val safeMax = maxValue.coerceAtLeast(1f)
    val points = if (values.size == 1) {
        val y = size.height - (values[0] / safeMax).coerceIn(0f, 1f) * size.height
        listOf(Offset(0f, y), Offset(size.width, y))
    } else {
        val stepX = size.width / (values.size - 1)
        values.mapIndexed { idx, v ->
            val x = idx * stepX
            val y = size.height - (v / safeMax).coerceIn(0f, 1f) * size.height
            Offset(x, y)
        }
    }

    val linePath = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val ctrlX = (p0.x + p1.x) / 2f
            cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
        }
    }

    val fillPath = Path().apply {
        addPath(linePath)
        lineTo(points.last().x, size.height)
        lineTo(points.first().x, size.height)
        close()
    }

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                lineColor.copy(alpha = fillTopAlpha),
                lineColor.copy(alpha = 0.0f)
            ),
            startY = 0f,
            endY = size.height
        )
    )

    drawPath(
        path = linePath,
        color = lineColor,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawDataNode(center: Offset, color: Color) {
    drawCircle(
        color = color.copy(alpha = 0.3f),
        radius = 7.dp.toPx(),
        center = center
    )
    drawCircle(
        color = color,
        radius = 4.dp.toPx(),
        center = center
    )
}
