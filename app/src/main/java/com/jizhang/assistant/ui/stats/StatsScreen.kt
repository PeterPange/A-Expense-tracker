package com.jizhang.assistant.ui.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jizhang.assistant.LocalRepository
import com.jizhang.assistant.data.dao.RecordDao
import com.jizhang.assistant.ui.theme.Expense
import com.jizhang.assistant.ui.theme.Primary
import com.jizhang.assistant.util.CategoryIcons
import com.jizhang.assistant.util.Money
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** 图表配色（柔和多彩） */
val chartColors = listOf(
    Color(0xFF5B8DEF), Color(0xFFF25C3B), Color(0xFF00B478), Color(0xFFF5A623),
    Color(0xFF9B59B6), Color(0xFF16A085), Color(0xFFE67E22), Color(0xFF2980B9),
    Color(0xFFE74C3C), Color(0xFF1ABC9C),
)
fun chartColor(i: Int) = chartColors[i % chartColors.size]

@Composable
fun StatsScreen(onOpenCalendar: () -> Unit = {}) {
    val repo = LocalRepository.current
    val vm: StatsViewModel = viewModel(
        key = "stats",
        factory = viewModelFactory { initializer { StatsViewModel(repo) } },
    )
    val state by vm.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
    ) {
        // ── 月份导航 + 收支概览 ──
        item {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = {
                            shift(state.year, state.month, -1)?.let { (y, m) -> vm.selectMonth(y, m) }
                        }) { Icon(Icons.Filled.ChevronLeft, "上个月") }
                        Text(
                            "${state.year}年${state.month}月",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(onClick = {
                            shift(state.year, state.month, +1)?.let { (y, m) -> vm.selectMonth(y, m) }
                        }) { Icon(Icons.Filled.ChevronRight, "下个月") }
                        IconButton(onClick = onOpenCalendar) {
                            Icon(Icons.Filled.CalendarMonth, "日历", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    // 收支概览
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        OverviewItem("支出", Money.format(state.totalExpense), Expense, true)
                        OverviewItem("收入", Money.format(state.totalIncome), Primary, !state.isExpense)
                        OverviewItem(
                            "结余",
                            Money.formatSigned(state.totalIncome - state.totalExpense),
                            MaterialTheme.colorScheme.onSurface,
                            false,
                        )
                    }
                }
            }
        }

        // ── 分类占比环形图 ──
        item {
            Spacer(Modifier.height(12.dp))
            ChartCard(title = "分类占比（${if (state.isExpense) "支出" else "收入"}）") {
                if (state.categoryTotals.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        DonutChart(
                            totals = state.categoryTotals,
                            isExpense = state.isExpense,
                            modifier = Modifier.size(150.dp),
                        )
                        Column {
                            state.categoryTotals.take(6).forEachIndexed { i, cat ->
                                LegendRow(
                                    color = chartColor(i),
                                    name = cat.name,
                                    value = Money.format(cat.total),
                                    percent = if (state.categoryTotals.sumOf { it.total } > 0)
                                        "${(cat.total / state.categoryTotals.sumOf { it.total } * 100).toInt()}%"
                                    else "0%",
                                )
                                Spacer(Modifier.height(7.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // 支出/收入切换
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        ToggleChip("看支出", state.isExpense) { vm.switchExpenseIncome(true) }
                        Spacer(Modifier.width(8.dp))
                        ToggleChip("看收入", !state.isExpense) { vm.switchExpenseIncome(false) }
                    }
                } else {
                    EmptyChart("本月暂无${if (state.isExpense) "支出" else "收入"}")
                }
            }
        }

        // ── 每日趋势（渐变面积图）──
        item {
            Spacer(Modifier.height(12.dp))
            ChartCard(title = "每日${if (state.isExpense) "支出" else "收入"}趋势") {
                if (state.dailyTotals.any { (if (state.isExpense) (it.expense ?: 0.0) else (it.income ?: 0.0)) > 0 }) {
                    AreaChart(
                        dailyTotals = state.dailyTotals,
                        isExpense = state.isExpense,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("1日", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "今日",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    EmptyChart("暂无数据")
                }
            }
        }

        // ── 星期分布（柱状图）──
        item {
            Spacer(Modifier.height(12.dp))
            ChartCard(title = "星期分布（哪天花得多）") {
                val weekdayTotals = computeWeekdayTotals(state.dailyTotals, state.year, state.month)
                if (weekdayTotals.any { it > 0 }) {
                    WeekdayChart(
                        values = weekdayTotals,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                    )
                } else {
                    EmptyChart("暂无数据")
                }
            }
        }

        // ── 分类排行（圆角渐变条）──
        item {
            Spacer(Modifier.height(12.dp))
            ChartCard(title = "分类排行") {
                if (state.categoryTotals.isNotEmpty()) {
                    val max = state.categoryTotals.maxOfOrNull { it.total } ?: 0.0
                    val sum = state.categoryTotals.sumOf { it.total }.coerceAtLeast(0.001)
                    state.categoryTotals.take(10).forEachIndexed { i, cat ->
                        RankingRow(cat, max, sum, state.isExpense, i)
                        if (i < 9) Spacer(Modifier.height(12.dp))
                    }
                } else {
                    EmptyChart("暂无数据")
                }
            }
        }

        // ── 成员排行（家庭记账）──
        if (state.memberTotals.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                ChartCard(title = "成员排行") {
                    state.memberTotals.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(m.member?.ifBlank { "我" } ?: "我", fontSize = 14.sp)
                            Text(
                                "支 ${Money.format(m.expense ?: 0.0)} · 收 ${Money.format(m.income ?: 0.0)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════ 组件 ═══════════════

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun EmptyChart(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp)
            .clickable { },
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun OverviewItem(label: String, value: String, color: Color, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) color else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LegendRow(color: Color, name: String, value: String, percent: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(name, fontSize = 12.sp, modifier = Modifier.width(48.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(4.dp))
        Text(
            percent,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        Text(value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** 环形图：分段圆弧 + 中心总额 */
@Composable
private fun DonutChart(
    totals: List<RecordDao.CategoryTotal>,
    isExpense: Boolean,
    modifier: Modifier = Modifier,
) {
    val sum = totals.sumOf { it.total }.takeIf { it > 0 } ?: 1.0
    var animationProgress by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
    )
    LaunchedEffect(Unit) { animationProgress = 1f }

    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.13f
        val radius = (size.minDimension - stroke) / 2f
        val topLeft = Offset((size.width - radius * 2 - stroke) / 2f + stroke / 2f,
            (size.height - radius * 2 - stroke) / 2f + stroke / 2f)
        val arcSize = Size(radius * 2, radius * 2)
        val gap = 2f // 分段间隙

        var startAngle = -90f
        totals.forEachIndexed { i, cat ->
            val sweep = (cat.total / sum * 360f).toFloat() * progress
            drawArc(
                color = chartColor(i),
                startAngle = startAngle + gap / 2f,
                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            startAngle += sweep
        }
    }
    // 中心总额
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (isExpense) "支出" else "收入",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                Money.format(sum),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** 面积图：平滑曲线 + 渐变填充 + 网格 */
@Composable
private fun AreaChart(
    dailyTotals: List<RecordDao.DailyTotal>,
    isExpense: Boolean,
    modifier: Modifier = Modifier,
) {
    val lineColor = if (isExpense) Expense else Primary
    var animationProgress by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
    )
    LaunchedEffect(Unit) { animationProgress = 1f }

    val daysInMonth = remember {
        Calendar.getInstance().let {
            it.set(Calendar.DAY_OF_MONTH, 1)
            it.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
    }
    val values = remember(dailyTotals) {
        (1..daysInMonth).map { d ->
            val v = dailyTotals.firstOrNull { it.day == d }
            if (isExpense) (v?.expense ?: 0.0) else (v?.income ?: 0.0)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val maxV = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)

        // 横向网格
        val gridColor = Color.Gray.copy(alpha = 0.12f)
        for (i in 1..3) {
            drawLine(gridColor, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), 1f)
        }

        val stepX = w / (daysInMonth - 1).coerceAtLeast(1)
        val points = values.mapIndexed { i, v ->
            Offset(i * stepX, (h - 10f) * (1f - (v / maxV).toFloat()) * progress + 10f * (1 - progress))
        }

        // 平滑曲线
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) {
                path.moveTo(p.x, p.y)
            } else {
                val prev = points[i - 1]
                val midX = (prev.x + p.x) / 2f
                path.cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
            }
        }

        // 渐变填充
        val fillPath = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.25f), lineColor.copy(alpha = 0.02f)),
                startY = 0f, endY = h,
            ),
        )
        // 曲线
        drawPath(path, lineColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        // 最大值点标记
        val maxIdx = values.indexOfFirst { it == values.maxOrNull() }
        if (maxIdx >= 0 && values[maxIdx] > 0) {
            val p = points[maxIdx]
            drawCircle(Color.White, radius = 5f, center = p)
            drawCircle(lineColor, radius = 5f, center = p, style = Stroke(width = 2.5f))
        }
    }
}

/** 星期分布柱状图（圆角柱） */
@Composable
private fun WeekdayChart(values: List<Double>, modifier: Modifier = Modifier) {
    var animationProgress by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
    )
    LaunchedEffect(Unit) { animationProgress = 1f }

    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val maxV = values.maxOrNull()?.coerceAtLeast(0.001) ?: 1.0
    val maxIdx = values.indexOfFirst { it == values.maxOrNull() }

    Column(modifier = modifier) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)) {
            val w = size.width
            val h = size.height
            val barSpace = w / 7f
            val barWidth = barSpace * 0.5f
            values.forEachIndexed { i, v ->
                val barH = (v / maxV).toFloat() * (h - 8f) * progress
                val left = i * barSpace + (barSpace - barWidth) / 2f
                // 圆角柱
                drawRoundRect(
                    color = if (i == maxIdx) Expense else chartColor(i).copy(alpha = 0.55f),
                    topLeft = Offset(left, h - barH),
                    size = Size(barWidth, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            labels.forEachIndexed { i, l ->
                Text(
                    l,
                    fontSize = 10.sp,
                    color = if (i == maxIdx) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == maxIdx) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 排行行：图标 + 名称 + 金额 + 百分比 + 渐变圆角条 */
@Composable
private fun RankingRow(
    cat: RecordDao.CategoryTotal,
    max: Double,
    sum: Double,
    isExpense: Boolean,
    index: Int,
) {
    val color = if (isExpense) Expense else Primary
    val percent = (cat.total / sum * 100).toInt()
    var animationProgress by remember(cat.categoryId) { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 600 + index * 60, easing = FastOutSlowInEasing),
    )
    LaunchedEffect(cat.categoryId) { animationProgress = 1f }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 排名
        Text(
            "${index + 1}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (index < 3) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        // 图标
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(color.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                CategoryIcons.iconOf(cat.categoryId, cat.type),
                contentDescription = cat.name,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    cat.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${Money.format(cat.total)} · ${cat.cnt}笔 · $percent%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            (if (max > 0) (cat.total / max).toFloat() else 0f) * progress
                        )
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(color.copy(alpha = 0.65f), color)
                            )
                        ),
                )
            }
        }
    }
}

/** 计算星期分布（周一~周日） */
private fun computeWeekdayTotals(
    dailyTotals: List<RecordDao.DailyTotal>,
    year: Int,
    month: Int,
): List<Double> {
    val result = MutableList(7) { 0.0 }
    val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (day in 1..daysInMonth) {
        cal.set(Calendar.DAY_OF_MONTH, day)
        // Calendar.DAY_OF_WEEK: 1=周日...7=周六 → 转成 周一=0...周日=6
        val idx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val v = dailyTotals.firstOrNull { it.day == day }
        result[idx] += (v?.expense ?: 0.0)
    }
    return result
}

private fun shift(year: Int, month: Int, delta: Int): Pair<Int, Int>? {
    val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
    cal.add(Calendar.MONTH, delta)
    val now = Calendar.getInstance()
    return if (cal.timeInMillis <= now.timeInMillis)
        cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH) + 1
    else null
}
