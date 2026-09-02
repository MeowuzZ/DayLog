package com.dailymemory.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailymemory.app.data.BackupSummary
import com.dailymemory.app.data.DailyEntry
import com.dailymemory.app.data.DailyRepository
import com.dailymemory.app.data.ExportFormat
import com.dailymemory.app.data.Member
import com.dailymemory.app.data.MemberStats
import com.dailymemory.app.data.Milestone
import com.dailymemory.app.export.ExportService
import com.dailymemory.app.ui.DailyMemoryTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = DailyRepository(applicationContext)
        setContent { DailyMemoryTheme { DailyMemoryApp(repository) } }
    }
}

private enum class MainTab(val label: String) {
    HOME("日报"), MEMBERS("成员"), MILESTONES("大事记"), SETTINGS("数据")
}
private val RANK_LEVELS = listOf("T0", "T1-1", "T1-2", "T1-3", "T2-1", "T2-2", "T2-3", "T3-1", "T3-2", "T3-3")
private data class PendingExport(val member: Member, val format: ExportFormat)

@Stable
private class CalendarCollapseState(val maxOffsetPx: Float) {
    var offsetPx by mutableStateOf(0f)
    val fraction: Float get() = (offsetPx / maxOffsetPx).coerceIn(0f, 1f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyMemoryApp(repository: DailyRepository) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var selectedMemberId by rememberSaveable { mutableStateOf(0L) }
    var refresh by remember { mutableStateOf(0) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var showMemberDialog by remember { mutableStateOf(false) }
    var showMilestoneDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<Member?>(null) }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    var restoreCandidate by remember { mutableStateOf<Pair<android.net.Uri, BackupSummary>?>(null) }
    var selectedJournalDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    fun notify(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val request = pendingExport
        if (uri != null && request != null) {
            runCatching {
                ExportService.write(
                    context,
                    uri,
                    request.format,
                    request.member,
                    repository.entriesForMember(request.member.id),
                    repository.milestonesForMember(request.member.id),
                )
            }.onSuccess { notify("已导出 ${request.member.name} 的${request.format.label}文件") }
                .onFailure { notify("导出失败：${it.message}") }
        }
        pendingExport = null
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) runCatching { repository.writeBackup(uri) }
            .onSuccess { notify("备份完成：${it.members} 位成员，${it.entries} 篇日报，${it.milestones} 条大事记") }
            .onFailure { notify("备份失败：${it.message}") }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            uri to repository.readBackupSummary(uri)
        }.onSuccess { restoreCandidate = it }
            .onFailure { notify("无法读取备份：${it.message}") }
    }

    val title = when {
        selectedMemberId > 0 -> repository.member(selectedMemberId)?.name ?: "成员详情"
        tab == MainTab.HOME -> "日报纪念册"
        tab == MainTab.MEMBERS -> "团队成员"
        tab == MainTab.MILESTONES -> "大事记"
        else -> "数据与备份"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (selectedMemberId > 0) {
                        TextButton(onClick = { selectedMemberId = 0L }) { Text("‹ 返回") }
                    }
                },
                actions = {
                    if (selectedMemberId == 0L && tab != MainTab.SETTINGS) {
                        IconButton(
                            onClick = {
                                when (tab) {
                                    MainTab.HOME -> showEntryDialog = true
                                    MainTab.MEMBERS -> {
                                        editingMember = null
                                        showMemberDialog = true
                                    }
                                    MainTab.MILESTONES -> showMilestoneDialog = true
                                    MainTab.SETTINGS -> Unit
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = when (tab) {
                                    MainTab.HOME -> "添加日报"
                                    MainTab.MEMBERS -> "添加成员"
                                    MainTab.MILESTONES -> "添加大事记"
                                    MainTab.SETTINGS -> null
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selectedMemberId == 0L) {
                NavigationBar {
                    MainTab.values().forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(
                                    when (item) {
                                        MainTab.HOME -> Icons.Default.Home
                                        MainTab.MEMBERS -> Icons.Default.Person
                                        MainTab.MILESTONES -> Icons.Default.Star
                                        MainTab.SETTINGS -> Icons.Default.Settings
                                    },
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                selectedMemberId > 0 -> MemberDetailScreen(
                    memberId = selectedMemberId,
                    repository = repository,
                    refresh = refresh,
                    onEdit = { editingMember = it; showMemberDialog = true },
                    onDeleted = { refresh++; selectedMemberId = 0L },
                    onExport = { member, format ->
                        pendingExport = PendingExport(member, format)
                        exportLauncher.launch(ExportService.suggestedName(member, format))
                    },
                    onEntryDeleted = { refresh++ },
                    onMilestoneDeleted = { refresh++ },
                )
                tab == MainTab.HOME -> HomeScreen(
                    repository = repository,
                    refresh = refresh,
                    selectedDateText = selectedJournalDateText,
                    onSelectedDateChange = { selectedJournalDateText = it },
                )
                tab == MainTab.MEMBERS -> MembersScreen(
                    repository = repository,
                    refresh = refresh,
                    onSelect = { selectedMemberId = it },
                    onEdit = { editingMember = it; showMemberDialog = true },
                )
                tab == MainTab.MILESTONES -> MilestonesScreen(
                    repository = repository,
                    refresh = refresh,
                    onDelete = { repository.deleteMilestone(it.id); refresh++ },
                )
                else -> SettingsScreen(
                    onBackup = { backupLauncher.launch("日报纪念册备份_${LocalDate.now()}.rhb") },
                    onRestore = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                )
            }
        }
    }

    if (showEntryDialog) {
        EntryDialog(
            members = repository.members(),
            initialDate = runCatching { LocalDate.parse(selectedJournalDateText) }.getOrDefault(LocalDate.now()),
            onDismiss = { showEntryDialog = false },
            onSave = { entry ->
                runCatching { repository.addEntry(entry) }
                    .onSuccess { refresh++; showEntryDialog = false; notify("日报已添加") }
                    .onFailure { notify(it.message ?: "日报保存失败") }
            },
        )
    }

    if (showMemberDialog) {
        MemberDialog(
            member = editingMember,
            tags = repository.tags(),
            onAddTag = {
                repository.addTag(it).also { refresh++ }
            },
            onDismiss = { showMemberDialog = false; editingMember = null },
            onSave = { member ->
                runCatching { repository.saveMember(member) }
                    .onSuccess { refresh++; showMemberDialog = false; editingMember = null; notify("成员档案已保存") }
                    .onFailure { notify(it.message ?: "成员保存失败") }
            },
        )
    }

    if (showMilestoneDialog) {
        MilestoneDialog(
            members = repository.members(),
            onDismiss = { showMilestoneDialog = false },
            onSave = { milestone ->
                runCatching { repository.addMilestone(milestone) }
                    .onSuccess { refresh++; showMilestoneDialog = false; notify("大事记已添加") }
                    .onFailure { notify(it.message ?: "大事记保存失败") }
            },
        )
    }

    restoreCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { restoreCandidate = null },
            title = { Text("确认恢复备份？") },
            text = {
                Text("备份包含 ${candidate.second.members} 位成员、${candidate.second.entries} 篇日报和 ${candidate.second.milestones} 条大事记。\n\n恢复会用备份内容替换当前所有数据，建议先导出一份当前备份。")
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { repository.restoreBackup(candidate.first) }
                        .onSuccess { refresh++; restoreCandidate = null; notify("已恢复 ${it.members} 位成员、${it.entries} 篇日报和 ${it.milestones} 条大事记") }
                        .onFailure { notify("恢复失败：${it.message}") }
                }) { Text("确认替换") }
            },
            dismissButton = { TextButton(onClick = { restoreCandidate = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun HomeScreen(
    repository: DailyRepository,
    refresh: Int,
    selectedDateText: String,
    onSelectedDateChange: (String) -> Unit,
) {
    val selectedDate = runCatching { LocalDate.parse(selectedDateText) }.getOrDefault(LocalDate.now())
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selectedDate).toString()) }
    val month = runCatching { YearMonth.parse(monthText) }.getOrDefault(YearMonth.from(selectedDate))
    val counts = remember(month, refresh) {
        buildMap {
            putAll(repository.entryCountsForMonth(month.minusMonths(1).toString()))
            putAll(repository.entryCountsForMonth(month.toString()))
            putAll(repository.entryCountsForMonth(month.plusMonths(1).toString()))
        }
    }
    val entries = remember(selectedDate, refresh) { repository.entriesForDate(selectedDate.toString()) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val expandedHeightPx = with(density) { 360.dp.toPx() }
    val collapsedHeightPx = with(density) { 140.dp.toPx() }
    val collapseRangePx = expandedHeightPx - collapsedHeightPx
    val collapseState = remember(collapseRangePx) { CalendarCollapseState(collapseRangePx) }
    val nestedScrollConnection = remember(collapseState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || collapseState.offsetPx >= collapseState.maxOffsetPx) return Offset.Zero
                val consumed = (-available.y).coerceAtMost(collapseState.maxOffsetPx - collapseState.offsetPx)
                collapseState.offsetPx += consumed
                return Offset(0f, -consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f || collapseState.offsetPx <= 0f) return Offset.Zero
                val expanded = available.y.coerceAtMost(collapseState.offsetPx)
                collapseState.offsetPx -= expanded
                return Offset(0f, expanded)
            }
        }
    }
    val selectDate: (LocalDate) -> Unit = { date ->
        monthText = YearMonth.from(date).toString()
        onSelectedDateChange(date.toString())
    }

    Column(Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        CollapsibleCalendarCard(
            month = month,
            selectedDate = selectedDate,
            counts = counts,
            collapseState = collapseState,
            onMonthChange = {
                monthText = it.toString()
                val day = selectedDate.dayOfMonth.coerceAtMost(it.lengthOfMonth())
                onSelectedDateChange(it.atDay(day).toString())
            },
            onDateSelected = selectDate,
            onWeekChange = selectDate,
        )
        Text(
            text = "${selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))} · ${entries.size} 篇日报",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxHeight(), contentAlignment = Alignment.Center) {
                        EmptyState("当天还没有录入日报", "点击右上角 + 开始收集", compact = true)
                    }
                }
            } else {
                items(entries, key = { it.id }) { TimelineEntry(it) }
            }
        }
    }
}

@Composable
private fun CollapsibleCalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    counts: Map<String, Int>,
    collapseState: CalendarCollapseState,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onWeekChange: (LocalDate) -> Unit,
) {
    Box(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .collapsibleCalendarHeight(collapseState, 360.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
        ) {
            MorphingCalendar(
                month = month,
                selectedDate = selectedDate,
                counts = counts,
                collapseState = collapseState,
                onMonthChange = onMonthChange,
                onDateSelected = onDateSelected,
                onWeekChange = onWeekChange,
            )
        }
    }
}

private fun Modifier.collapsibleCalendarHeight(
    state: CalendarCollapseState,
    expandedHeight: androidx.compose.ui.unit.Dp,
): Modifier = layout { measurable, constraints ->
    val expandedHeightPx = expandedHeight.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
    val currentHeightPx = (expandedHeightPx - state.offsetPx.roundToInt())
        .coerceIn((expandedHeightPx - state.maxOffsetPx).roundToInt(), expandedHeightPx)
    val placeable = measurable.measure(
        constraints.copy(minHeight = expandedHeightPx, maxHeight = expandedHeightPx)
    )
    layout(placeable.width, currentHeightPx) { placeable.placeRelative(0, 0) }
}

@Composable
private fun MorphingCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    counts: Map<String, Int>,
    collapseState: CalendarCollapseState,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onWeekChange: (LocalDate) -> Unit,
) {
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val gridStart = month.atDay(1).minusDays(firstOffset.toLong())
    val selectedWeek = (((selectedDate.toEpochDay() - gridStart.toEpochDay()) / 7).toInt()).coerceIn(0, 5)
    val weekStart = gridStart.plusWeeks(selectedWeek.toLong())
    val regularRowHeight = 44.dp
    val selectedRowTranslation = with(LocalDensity.current) { -(selectedWeek * regularRowHeight.toPx()) }

    Column(Modifier.fillMaxSize()) {
        CalendarHeader(
            title = "${selectedDate.year}年${selectedDate.monthValue}月",
            expandedSubtitle = "上滑日报可收起月历",
            compactSubtitle = "${weekStart.monthValue}月${weekStart.dayOfMonth}日 — ${weekStart.plusDays(6).monthValue}月${weekStart.plusDays(6).dayOfMonth}日",
            collapseState = collapseState,
            onPrevious = {
                if (collapseState.fraction >= .6f) onWeekChange(selectedDate.minusWeeks(1))
                else onMonthChange(month.minusMonths(1))
            },
            onNext = {
                if (collapseState.fraction >= .6f) onWeekChange(selectedDate.plusWeeks(1))
                else onMonthChange(month.plusMonths(1))
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(26.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        repeat(6) { week ->
            val selectedRow = week == selectedWeek
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    .height(regularRowHeight)
                    .graphicsLayer {
                        val progress = collapseState.fraction
                        if (selectedRow) {
                            alpha = 1f
                            translationY = selectedRowTranslation * progress
                        } else {
                            alpha = (1f - progress * 1.8f).coerceIn(0f, 1f)
                        }
                    }
            ) {
                repeat(7) { weekday ->
                    val date = gridStart.plusDays((week * 7 + weekday).toLong())
                    CalendarDay(
                        date = date,
                        selected = date == selectedDate,
                        hasEntries = (counts[date.toString()] ?: 0) > 0,
                        muted = date.monthValue != month.monthValue,
                        onClick = onDateSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    title: String,
    expandedSubtitle: String,
    compactSubtitle: String,
    collapseState: CalendarCollapseState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrevious) { Text("‹", fontSize = 28.sp) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Box(Modifier.height(17.dp), contentAlignment = Alignment.Center) {
                Text(
                    expandedSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer {
                        alpha = (1f - collapseState.fraction * 2f).coerceIn(0f, 1f)
                    },
                )
                Text(
                    compactSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer {
                        alpha = ((collapseState.fraction - .5f) * 2f).coerceIn(0f, 1f)
                    },
                )
            }
        }
        TextButton(onClick = onNext) { Text("›", fontSize = 28.sp) }
    }
}

@Composable
private fun RowScope.CalendarDay(
    date: LocalDate,
    selected: Boolean,
    hasEntries: Boolean,
    muted: Boolean,
    onClick: (LocalDate) -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick(date) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    muted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (hasEntries || selected) FontWeight.Bold else FontWeight.Normal,
            )
            if (hasEntries) {
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun TimelineEntry(entry: DailyEntry, onDelete: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Box(Modifier.width(2.dp).height(54.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .25f)))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.memberName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.time, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                        if (onDelete != null) {
                            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
                Text(entry.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(entry.content, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun MilestoneTimelineItem(milestone: Milestone, onDelete: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                Box(Modifier.width(2.dp).height(48.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha = .25f)))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(milestone.memberName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "删除大事记", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(19.dp))
                        }
                    }
                }
                Text(milestone.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(6.dp))
                Text(milestone.content, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun MilestonesScreen(
    repository: DailyRepository,
    refresh: Int,
    onDelete: (Milestone) -> Unit,
) {
    val milestones = remember(refresh) { repository.allMilestones() }
    var pendingDelete by remember { mutableStateOf<Milestone?>(null) }
    if (milestones.isEmpty()) {
        EmptyState("还没有大事记", "点击右上角 + 记录团队成员的重要时刻")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "全部大事记 · ${milestones.size} 条",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            items(milestones, key = { it.id }) { milestone ->
                MilestoneTimelineItem(milestone) { pendingDelete = milestone }
            }
        }
    }
    pendingDelete?.let { milestone ->
        ConfirmDialog(
            title = "删除这条大事记？",
            text = "${milestone.memberName} · ${milestone.date}\n${milestone.content}",
            onDismiss = { pendingDelete = null },
            onConfirm = { onDelete(milestone); pendingDelete = null },
        )
    }
}

@Composable
private fun MembersScreen(
    repository: DailyRepository,
    refresh: Int,
    onSelect: (Long) -> Unit,
    onEdit: (Member) -> Unit,
) {
    val stats = remember(refresh) { repository.memberStats() }
    if (stats.isEmpty()) {
        EmptyState("还没有团队成员", "点击右上角 + 建立档案")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val achieved = stats.count { it.rewardReached }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("团队进度", fontWeight = FontWeight.Bold); Text("共 ${stats.size} 位成员") }
                        Column(horizontalAlignment = Alignment.End) { Text("$achieved", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("人达成 90 天") }
                    }
                }
            }
            items(stats, key = { it.member.id }) { item ->
                MemberCard(item, onClick = { onSelect(item.member.id) }, onEdit = { onEdit(item.member) })
            }
        }
    }
}

@Composable
private fun MemberCard(stats: MemberStats, onClick: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text(stats.member.name.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stats.member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(stats.member.major, stats.member.grade, stats.member.rank, stats.member.tag)
                            .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "档案待完善" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已记录 ${stats.reportDays} / 90 天", fontWeight = FontWeight.SemiBold)
                Text(if (stats.rewardReached) "✅ 已达成奖励" else "还差 ${(90 - stats.reportDays).coerceAtLeast(0)} 天", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = (stats.reportDays / 90f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(6.dp))
            Text("共 ${stats.reportCount} 篇日报 · 点击查看时间轴", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemberDetailScreen(
    memberId: Long,
    repository: DailyRepository,
    refresh: Int,
    onEdit: (Member) -> Unit,
    onDeleted: () -> Unit,
    onExport: (Member, ExportFormat) -> Unit,
    onEntryDeleted: () -> Unit,
    onMilestoneDeleted: () -> Unit,
) {
    val member = remember(memberId, refresh) { repository.member(memberId) } ?: return
    val entries = remember(memberId, refresh) { repository.entriesForMember(memberId) }
    val milestones = remember(memberId, refresh) { repository.milestonesForMember(memberId) }
    val days = entries.map { it.date }.distinct().size
    var showExport by remember { mutableStateOf(false) }
    var confirmDeleteMember by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<DailyEntry?>(null) }
    var deleteMilestone by remember { mutableStateOf<Milestone?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(member.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(listOf(member.major, member.grade).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "档案待完善" })
                        }
                        Text("$days / 90", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator((days / 90f).coerceIn(0f, 1f), Modifier.fillMaxWidth().height(9.dp).clip(CircleShape))
                    Spacer(Modifier.height(12.dp))
                    ProfileLine("加入时间", member.joinedDate.ifBlank { "未填写" })
                    ProfileLine("职级", member.rank.ifBlank { "未设置" })
                    ProfileLine("标签", member.tag.ifBlank { "未设置" })
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showExport = true }, modifier = Modifier.weight(1f)) { Text("导出纪念册") }
                        OutlinedButton(onClick = { onEdit(member) }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(4.dp)); Text("编辑") }
                        IconButton(onClick = { confirmDeleteMember = true }) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        item {
            Text("个人大事记 · ${milestones.size} 条", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
        if (milestones.isEmpty()) item { EmptyState("还没有大事记", "可在“大事记”页点击右上角 + 添加", compact = true) }
        items(milestones, key = { "milestone-${it.id}" }) { milestone ->
            MilestoneTimelineItem(milestone) { deleteMilestone = milestone }
        }
        item {
            Text("日报时间轴 · ${entries.size} 篇", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (entries.isEmpty()) item { EmptyState("还没有日报", "在日报页点击右上角 + 进行录入", compact = true) }
        items(entries, key = { it.id }) { entry -> TimelineEntry(entry) { deleteEntry = entry } }
    }

    if (showExport) ExportFormatDialog(
        member = member,
        onDismiss = { showExport = false },
        onSelect = { showExport = false; onExport(member, it) },
    )
    if (confirmDeleteMember) ConfirmDialog(
        title = "删除 ${member.name}？",
        text = "该成员的 ${entries.size} 篇日报也会一并删除。建议先导出纪念册和完整备份。",
        onDismiss = { confirmDeleteMember = false },
        onConfirm = { repository.deleteMember(member.id); confirmDeleteMember = false; onDeleted() },
    )
    deleteEntry?.let { entry ->
        ConfirmDialog(
            title = "删除这篇日报？",
            text = "${entry.date} ${entry.time} 的日报将被删除。",
            onDismiss = { deleteEntry = null },
            onConfirm = { repository.deleteEntry(entry.id); deleteEntry = null; onEntryDeleted() },
        )
    }
    deleteMilestone?.let { milestone ->
        ConfirmDialog(
            title = "删除这条大事记？",
            text = "${milestone.date} 的大事记将被删除。",
            onDismiss = { deleteMilestone = null },
            onConfirm = {
                repository.deleteMilestone(milestone.id)
                deleteMilestone = null
                onMilestoneDeleted()
            },
        )
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text("$label：", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(92.dp))
        Text(value, Modifier.weight(1f))
    }
}

@Composable
private fun SettingsScreen(onBackup: () -> Unit, onRestore: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(18.dp)) {
                Text("数据保护", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("直接覆盖安装新版本时，本地数据会自动保留。卸载 App 会清除本地数据，因此建议定期导出备份。")
            }
        }
        Card {
            Column(Modifier.padding(18.dp)) {
                Text("完整备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("将成员档案、标签、全部日报和大事记打包为 .rhb 文件，可保存到本机、网盘或移动硬盘。", modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("导出完整备份") }
            }
        }
        Card {
            Column(Modifier.padding(18.dp)) {
                Text("恢复备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("重新安装或更换手机后，选择之前导出的 .rhb 文件即可恢复。恢复前会二次确认。", modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("选择备份并恢复") }
            }
        }
        Card {
            Column(Modifier.padding(18.dp)) {
                Text("安全更新步骤", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("1. 更新前导出一份完整备份\n2. 不要先卸载旧版\n3. 直接安装同一应用的新版 APK\n4. 打开后抽查几位成员的日报")
            }
        }
        Text("日报纪念册 · 版本 ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntryDialog(
    members: List<Member>,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (DailyEntry) -> Unit,
) {
    val context = LocalContext.current
    var memberId by remember { mutableStateOf(members.firstOrNull()?.id ?: 0L) }
    var date by remember { mutableStateOf(initialDate) }
    var time by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var content by remember { mutableStateOf("") }
    var showMemberChoices by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加日报") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (members.isEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                        Text("请先到“成员”页建立至少一位成员档案。", Modifier.padding(12.dp))
                    }
                } else {
                    Box {
                        OutlinedButton(onClick = { showMemberChoices = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("成员：${members.firstOrNull { it.id == memberId }?.name ?: "请选择"}")
                        }
                        androidx.compose.material3.DropdownMenu(expanded = showMemberChoices, onDismissRequest = { showMemberChoices = false }) {
                            members.forEach { member ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(member.name) },
                                    onClick = { memberId = member.id; showMemberChoices = false },
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDatePicker(context, date) { date = it } },
                        modifier = Modifier.weight(1f),
                    ) { Text(date.toString()) }
                    OutlinedButton(
                        onClick = { showTimePicker(context, time) { time = it } },
                        modifier = Modifier.weight(1f),
                    ) { Text(time.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("日报内容") },
                    minLines = 7,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("今天完成了什么、有什么收获、明天的计划……") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = members.isNotEmpty() && content.isNotBlank(),
                onClick = {
                    onSave(
                        DailyEntry(
                            memberId = memberId,
                            date = date.toString(),
                            time = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                            content = content,
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MilestoneDialog(
    members: List<Member>,
    onDismiss: () -> Unit,
    onSave: (Milestone) -> Unit,
) {
    val context = LocalContext.current
    var memberId by remember { mutableStateOf(members.firstOrNull()?.id ?: 0L) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var content by remember { mutableStateOf("") }
    var showMemberChoices by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加大事记") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (members.isEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                        Text("请先到“成员”页建立至少一位成员档案。", Modifier.padding(12.dp))
                    }
                } else {
                    Box {
                        OutlinedButton(onClick = { showMemberChoices = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("成员：${members.firstOrNull { it.id == memberId }?.name ?: "请选择"}")
                        }
                        androidx.compose.material3.DropdownMenu(expanded = showMemberChoices, onDismissRequest = { showMemberChoices = false }) {
                            members.forEach { member ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(member.name) },
                                    onClick = { memberId = member.id; showMemberChoices = false },
                                )
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { showDatePicker(context, date) { date = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text("日期：$date")
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("大事记内容") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如：获得荣誉、完成重要项目、职级变化……") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = members.isNotEmpty() && content.isNotBlank(),
                onClick = {
                    onSave(
                        Milestone(
                            memberId = memberId,
                            date = date.toString(),
                            content = content,
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MemberDialog(
    member: Member?,
    tags: List<String>,
    onAddTag: (String) -> String,
    onDismiss: () -> Unit,
    onSave: (Member) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(member) { mutableStateOf(member?.name.orEmpty()) }
    var major by remember(member) { mutableStateOf(member?.major.orEmpty()) }
    var grade by remember(member) { mutableStateOf(member?.grade.orEmpty()) }
    var joinedDate by remember(member) { mutableStateOf(runCatching { LocalDate.parse(member?.joinedDate) }.getOrDefault(LocalDate.now())) }
    var rank by remember(member) { mutableStateOf(member?.rank.orEmpty()) }
    var tag by remember(member) { mutableStateOf(member?.tag.orEmpty()) }
    var showRankChoices by remember { mutableStateOf(false) }
    var showTagChoices by remember { mutableStateOf(false) }
    var addingTag by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (member == null) "添加成员" else "编辑成员") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("姓名 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(major, { major = it }, label = { Text("专业") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(grade, { grade = it }, label = { Text("年级") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { showDatePicker(context, joinedDate) { joinedDate = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text("加入时间：$joinedDate")
                }
                Box {
                    OutlinedButton(onClick = { showRankChoices = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("职级：${rank.ifBlank { "请选择" }}")
                    }
                    androidx.compose.material3.DropdownMenu(expanded = showRankChoices, onDismissRequest = { showRankChoices = false }) {
                        RANK_LEVELS.forEach { option ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (rank == option) "✓ $option" else option) },
                                onClick = { rank = option; showRankChoices = false },
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { showTagChoices = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("标签：${tag.ifBlank { "未设置" }}")
                    }
                    androidx.compose.material3.DropdownMenu(expanded = showTagChoices, onDismissRequest = { showTagChoices = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (tag.isBlank()) "✓ 不设置" else "不设置") },
                            onClick = { tag = ""; showTagChoices = false },
                        )
                        tags.forEach { option ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (tag == option) "✓ $option" else option) },
                                onClick = { tag = option; showTagChoices = false },
                            )
                        }
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("＋ 新建标签") },
                            onClick = { showTagChoices = false; addingTag = true },
                        )
                    }
                }
                if (addingTag) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            label = { Text("新标签") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = newTag.isNotBlank(),
                            onClick = {
                                tag = onAddTag(newTag)
                                newTag = ""
                                addingTag = false
                            },
                        ) { Text("添加") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        Member(
                            id = member?.id ?: 0,
                            name = name,
                            major = major,
                            grade = grade,
                            joinedDate = joinedDate.toString(),
                            rank = rank,
                            tag = tag,
                            createdAt = member?.createdAt ?: System.currentTimeMillis(),
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ExportFormatDialog(member: Member, onDismiss: () -> Unit, onSelect: (ExportFormat) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出 ${member.name} 的纪念册") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("导出内容包含个人档案、大事记、统计与全部日报时间轴。")
                ExportFormat.values().forEach { format ->
                    OutlinedButton(onClick = { onSelect(format) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${format.label}  (.${format.extension})")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EmptyState(title: String, subtitle: String, compact: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().then(if (compact) Modifier.padding(24.dp) else Modifier.fillMaxHeight()).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

private fun showDatePicker(context: android.content.Context, current: LocalDate, onSelected: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
        current.year,
        current.monthValue - 1,
        current.dayOfMonth,
    ).show()
}

private fun showTimePicker(context: android.content.Context, current: LocalTime, onSelected: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onSelected(LocalTime.of(hour, minute)) },
        current.hour,
        current.minute,
        true,
    ).show()
}
