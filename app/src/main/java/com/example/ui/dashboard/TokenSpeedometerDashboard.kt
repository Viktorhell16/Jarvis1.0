package com.example.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.EncryptedPrefsManager
import com.example.data.local.JarvPrefsState
import com.example.data.local.TokenUsageEntity
import com.example.data.network.ProviderCatalog
import com.example.ui.i18n.UiStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusCriticalRed
import com.example.ui.theme.StatusHealthyGreen
import com.example.ui.theme.StatusModerateYellow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TokenSpeedometerScreen(
    prefsState: JarvPrefsState,
    prefs: EncryptedPrefsManager,
    usageList: List<TokenUsageEntity>,
    todayKey: String,
    monthKey: String,
    strings: UiStrings,
    onBack: () -> Unit,
    onUpdateBudget: (dailyLimit: Long, monthlyBudgetUsd: Double, hardStop: Boolean) -> Unit,
    onResetUsage: () -> Unit
) {
    BackHandler(onBack = onBack)

    val livePrefs by prefs.state.collectAsStateWithLifecycle()
    val nowMs = remember(usageList.size, livePrefs.budgetResetTimestampMs) { System.currentTimeMillis() }

    val todayTokens = remember(usageList, todayKey) {
        usageList.filter { it.dateKey == todayKey }.sumOf { it.promptTokens + it.completionTokens }
    }
    val monthTokens = remember(usageList, monthKey) {
        usageList.filter { it.monthKey == monthKey }.sumOf { it.promptTokens + it.completionTokens }
    }
    val monthCostUsd = remember(usageList, monthKey) {
        usageList.filter { it.monthKey == monthKey }.sumOf { it.estimatedCostUsd }
    }

    // Manual 5M token budget tracking (non-destructive reset via budgetResetTimestampMs)
    val usedSinceReset = remember(usageList, livePrefs.budgetResetTimestampMs) {
        usageList.filter { it.timestamp >= livePrefs.budgetResetTimestampMs }
            .sumOf { it.promptTokens + it.completionTokens }
    }
    val totalManualBudget = livePrefs.manualTotalTokenBudget.coerceAtLeast(1_000L)
    val remainingBudgetTokens = (totalManualBudget - usedSinceReset).coerceAtLeast(0L)
    val manualSpentFraction = (usedSinceReset.toFloat() / totalManualBudget.toFloat()).coerceIn(0f, 1f)
    val manualRemainingFraction = (1f - manualSpentFraction).coerceIn(0f, 1f)

    // Rolling rate telemetry: RPM, TPM, TPH, TPD
    val rpmCount = remember(usageList, nowMs) {
        val oneMinAgo = nowMs - 60_000L
        usageList.count { it.timestamp >= oneMinAgo }
    }
    val inputTpm = remember(usageList, nowMs) {
        val oneMinuteAgo = nowMs - 60_000L
        usageList.filter { it.timestamp >= oneMinuteAgo }.sumOf { it.promptTokens }
    }
    val tpmTotal = remember(usageList, nowMs) {
        val oneMinuteAgo = nowMs - 60_000L
        usageList.filter { it.timestamp >= oneMinuteAgo }.sumOf { it.promptTokens + it.completionTokens }
    }
    val tphTotal = remember(usageList, nowMs) {
        val oneHourAgo = nowMs - 3_600_000L
        usageList.filter { it.timestamp >= oneHourAgo }.sumOf { it.promptTokens + it.completionTokens }
    }
    val tpdTotal = remember(usageList, nowMs) {
        val oneDayAgo = nowMs - 86_400_000L
        usageList.filter { it.timestamp >= oneDayAgo }.sumOf { it.promptTokens + it.completionTokens }
    }
    val tpmHistory = remember(usageList) {
        usageList.sortedBy { it.timestamp }.takeLast(12).map { it.promptTokens.toFloat() }
    }

    // Weekly Token Usage (last 7 days)
    val last7DayKeys = remember(todayKey) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        (6 downTo 0).map { offset ->
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            fmt.format(c.time)
        }
    }
    val weeklyBuckets = remember(usageList, last7DayKeys) {
        last7DayKeys.map { day ->
            day.takeLast(2) to usageList.filter { it.dateKey == day }.sumOf { it.promptTokens + it.completionTokens }
        }
    }
    val weeklyTokens = remember(weeklyBuckets) { weeklyBuckets.sumOf { it.second } }
    val weeklyLimit = (prefsState.dailyTokenLimit * 7L).coerceAtLeast(1L)

    val remainingUsd = (prefsState.monthlyBudgetUsd - monthCostUsd).coerceAtLeast(0.0)
    val dailyFraction = (todayTokens.toFloat() / prefsState.dailyTokenLimit.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    val budgetFraction = (monthCostUsd.toFloat() / prefsState.monthlyBudgetUsd.coerceAtLeast(0.01).toFloat()).coerceIn(0f, 1f)
    val weeklyFraction = (weeklyTokens.toFloat() / weeklyLimit.toFloat()).coerceIn(0f, 1f)
    val overallUsagePct = (((dailyFraction + budgetFraction) / 2f) * 100f).toInt().coerceIn(0, 100)
    val pct = (dailyFraction * 100f).toInt()

    val animatedFraction by animateFloatAsState(
        targetValue = dailyFraction,
        animationSpec = tween(durationMillis = 700),
        label = "speedometer_sweep"
    )
    val gaugeColor by animateColorAsState(
        targetValue = when {
            pct < 60 -> StatusHealthyGreen
            pct <= 85 -> StatusModerateYellow
            else -> StatusCriticalRed
        },
        animationSpec = tween(durationMillis = 400),
        label = "speedometer_color"
    )

    val byModel = remember(usageList) {
        usageList.groupBy { it.modelId }.mapValues { (_, items) ->
            val toks = items.sumOf { it.promptTokens + it.completionTokens }
            val cost = items.sumOf { it.estimatedCostUsd }
            toks to cost
        }
    }

    var manualBudgetInput by remember(livePrefs.manualTotalTokenBudget) {
        mutableStateOf(livePrefs.manualTotalTokenBudget.toString())
    }
    var showResetBudgetConfirm by remember { mutableStateOf(false) }

    var dailyLimitInput by remember(prefsState.dailyTokenLimit) { mutableStateOf(prefsState.dailyTokenLimit.toString()) }
    var monthlyBudgetInput by remember(prefsState.monthlyBudgetUsd) { mutableStateOf(String.format(Locale.US, "%.2f", prefsState.monthlyBudgetUsd)) }
    var hardStop by remember(prefsState.hardStopEnabled) { mutableStateOf(prefsState.hardStopEnabled) }

    // Cost calculator state
    var calcPromptTokens by remember { mutableStateOf("2000") }
    var calcCompletionTokens by remember { mutableStateOf("800") }
    val calcResultUsd = remember(calcPromptTokens, calcCompletionTokens, prefsState.activeModelId) {
        val p = calcPromptTokens.toLongOrNull() ?: 0L
        val c = calcCompletionTokens.toLongOrNull() ?: 0L
        ProviderCatalog.estimateCostUsd(prefsState.activeModelId, p, c)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.tokenDashboardTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("token_dash_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onResetUsage, modifier = Modifier.testTag("reset_usage_btn")) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset counters")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Animated Circular Speedometer Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    Box(
                        modifier = Modifier.size(210.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 18.dp.toPx()
                            val diameter = min(size.width, size.height) - strokeWidth
                            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                            val arcSize = Size(diameter, diameter)

                            drawArc(
                                color = trackColor,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = gaugeColor,
                                startAngle = 135f,
                                sweepAngle = 270f * animatedFraction,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = gaugeColor
                            )
                            Text(
                                text = "$todayTokens / ${prefsState.dailyTokenLimit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Остаток: $${String.format(Locale.US, "%.2f", remainingUsd)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricColumn("За день", "$todayTokens tok")
                        MetricColumn("За неделю", "$weeklyTokens tok")
                        MetricColumn("За месяц", "$monthTokens tok")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricColumn("Input TPM", "$inputTpm tok/m")
                        MetricColumn("Общая загрузка", "$overallUsagePct%")
                        MetricColumn("Расход/мес", "$${String.format(Locale.US, "%.3f", monthCostUsd)}")
                    }
                }
            }

            // Manual Token Budget & Remaining Balance Card (Default 5M, local counter)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_token_budget_card")
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Баланс и бюджет токенов", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Осталось ${formatTokensCompact(remainingBudgetTokens)} из ${formatTokensCompact(totalManualBudget)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (manualRemainingFraction > 0.15f) MaterialTheme.colorScheme.primary else StatusCriticalRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${(manualRemainingFraction * 100f).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { manualRemainingFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )

                    Text(
                        text = "Израсходовано с последнего сброса: $usedSinceReset / $totalManualBudget tok",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val presets = listOf(
                        "1M" to 1_000_000L,
                        "5M" to 5_000_000L,
                        "10M" to 10_000_000L,
                        "50M" to 50_000_000L
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { (label, amount) ->
                            FilterChip(
                                selected = totalManualBudget == amount,
                                onClick = {
                                    manualBudgetInput = amount.toString()
                                    prefs.updateManualTokenBudget(amount)
                                },
                                label = { Text(label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("budget_preset_${label.lowercase(Locale.US)}")
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualBudgetInput,
                            onValueChange = { manualBudgetInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Ручной бюджет (токены)") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_budget_input")
                        )
                        Button(
                            onClick = {
                                val parsed = manualBudgetInput.toLongOrNull() ?: 5_000_000L
                                prefs.updateManualTokenBudget(parsed)
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("apply_manual_budget_btn")
                        ) {
                            Text("Задать")
                        }
                    }

                    OutlinedButton(
                        onClick = { showResetBudgetConfirm = true },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("reset_manual_budget_btn")
                    ) {
                        Text("Сбросить счётчик расхода")
                    }
                }
            }

            // Rolling Rate Telemetry: RPM / TPM / TPH / TPD
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rolling_rate_telemetry_card")
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Скорость расхода (RPM / TPM / TPH / TPD)", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricColumn("RPM (60с)", "$rpmCount запр")
                        MetricColumn("TPM (60с)", "${formatTokensCompact(tpmTotal)} tok")
                        MetricColumn("TPH (1ч)", "${formatTokensCompact(tphTotal)} tok")
                        MetricColumn("TPD (24ч)", "${formatTokensCompact(tpdTotal)} tok")
                    }
                }
            }

            // Input Tokens Per Minute (TPM) + Overall Budget Utilization
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Входные токены в минуту (TPM)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Текущая скорость: $inputTpm tok/min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$overallUsagePct% общ.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    val primaryColor = MaterialTheme.colorScheme.primary
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("tpm_sparkline_canvas")
                    ) {
                        val pts = if (tpmHistory.size >= 2) tpmHistory else listOf(0f, inputTpm.toFloat().coerceAtLeast(1f))
                        val maxVal = (pts.maxOrNull() ?: 1f).coerceAtLeast(10f)
                        val stepX = size.width / (pts.size - 1).coerceAtLeast(1)
                        val linePath = Path()
                        val fillPath = Path()
                        pts.forEachIndexed { idx, value ->
                            val x = idx * stepX
                            val y = size.height - ((value / maxVal) * (size.height - 12.dp.toPx())) - 6.dp.toPx()
                            if (idx == 0) {
                                linePath.moveTo(x, y)
                                fillPath.moveTo(x, size.height)
                                fillPath.lineTo(x, y)
                            } else {
                                linePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }
                        fillPath.lineTo((pts.size - 1) * stepX, size.height)
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.28f), primaryColor.copy(alpha = 0.02f))
                            )
                        )
                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Недельный расход (7 дней)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${(weeklyFraction * 100f).toInt()}% • $weeklyTokens tok",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { weeklyFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )

                    // 7-day bar overview
                    val barTrack = MaterialTheme.colorScheme.surfaceContainerHighest
                    val barActive = MaterialTheme.colorScheme.primary
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        val maxDay = (weeklyBuckets.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(100L).toFloat()
                        val slotW = size.width / weeklyBuckets.size.coerceAtLeast(1)
                        val barW = (slotW * 0.48f).coerceAtMost(24.dp.toPx())
                        weeklyBuckets.forEachIndexed { i, (_, tokens) ->
                            val left = i * slotW + (slotW - barW) / 2f
                            drawRoundRect(
                                color = barTrack,
                                topLeft = Offset(left, 4.dp.toPx()),
                                size = Size(barW, size.height - 8.dp.toPx()),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                            val h = ((tokens.toFloat() / maxDay) * (size.height - 8.dp.toPx())).coerceAtLeast(4.dp.toPx())
                            drawRoundRect(
                                color = barActive,
                                topLeft = Offset(left, size.height - 4.dp.toPx() - h),
                                size = Size(barW, h),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        weeklyBuckets.forEach { (dayLabel, _) ->
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Per-model breakdown
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Разбивка по моделям", style = MaterialTheme.typography.titleMedium)
                    if (byModel.isEmpty()) {
                        Text(
                            text = "Пока нет записей расхода токенов.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        byModel.entries.forEachIndexed { idx, (model, pair) ->
                            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(model, style = MaterialTheme.typography.bodyMedium, fontFamily = JetBrainsMonoFontFamily)
                                Text(
                                    "${pair.first} tok • $${String.format(Locale.US, "%.4f", pair.second)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Budget & Hard Stop Controls
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Лимиты и жёсткий стоп-кран", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Блокировать LLM при 100% лимита", style = MaterialTheme.typography.bodyMedium)
                            Text("Локальные 0-токен команды продолжат работать", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = hardStop, onCheckedChange = { hardStop = it })
                    }
                    OutlinedTextField(
                        value = dailyLimitInput,
                        onValueChange = { dailyLimitInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Дневной лимит токенов") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = monthlyBudgetInput,
                        onValueChange = { monthlyBudgetInput = it },
                        label = { Text("Месячный бюджет (USD)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onUpdateBudget(
                                dailyLimitInput.toLongOrNull() ?: 250_000L,
                                monthlyBudgetInput.replace(",", ".").toDoubleOrNull() ?: 20.0,
                                hardStop
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить лимиты")
                    }
                }
            }

            // Interactive Cost Calculator
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Калькулятор стоимости (${prefsState.activeModelDisplayName})", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = calcPromptTokens,
                            onValueChange = { calcPromptTokens = it.filter { c -> c.isDigit() } },
                            label = { Text("Input tok") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = calcCompletionTokens,
                            onValueChange = { calcCompletionTokens = it.filter { c -> c.isDigit() } },
                            label = { Text("Output tok") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "Оценочная стоимость: $${String.format(Locale.US, "%.5f", calcResultUsd)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedButton(
                onClick = onResetUsage,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCriticalRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сбросить статистику токенов")
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showResetBudgetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetBudgetConfirm = false },
            title = { Text("Сбросить счётчик расхода?") },
            text = {
                Text("Счётчик остатка ручного бюджета будет обнулён с текущего момента, а история RPM/TPM/TPH/TPD и статистика в Room сохранятся.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.resetManualBudgetCounter()
                        showResetBudgetConfirm = false
                    },
                    modifier = Modifier.testTag("confirm_reset_manual_budget_btn")
                ) {
                    Text("Сбросить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetBudgetConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

internal fun formatTokensCompact(tokens: Long): String {
    return when {
        tokens >= 1_000_000L -> String.format(Locale.US, "%.2fM", tokens / 1_000_000.0)
        tokens >= 1_000L -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
        else -> tokens.toString()
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

