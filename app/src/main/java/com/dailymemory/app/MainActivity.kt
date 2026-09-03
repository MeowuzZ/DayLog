package com.dailymemory.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dailymemory.app.export.WeChatShareService
import com.dailymemory.app.ui.*
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    var restoreCandidate by remember { mutableStateOf<Pair<android.net.Uri, BackupSummary>?>(null) }
    var selectedJournalDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    fun notify(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

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

    BackHandler(enabled = selectedMemberId > 0L) {
        selectedMemberId = 0L
    }

    val addAction: () -> Unit = {
        when (tab) {
            MainTab.HOME -> showEntryDialog = true
            MainTab.MEMBERS -> { editingMember = null; showMemberDialog = true }
            MainTab.MILESTONES -> showMilestoneDialog = true
            MainTab.SETTINGS -> Unit
        }
    }
    val addDescription = when (tab) {
        MainTab.HOME -> "添加日报"
        MainTab.MEMBERS -> "添加成员"
        MainTab.MILESTONES -> "添加大事记"
        MainTab.SETTINGS -> ""
    }
    val window = (context as? Activity)?.window
    SideEffect {
        window?.let {
            it.statusBarColor = (if (tab == MainTab.SETTINGS && selectedMemberId == 0L) JournalColors.Hero else JournalColors.Background).toArgb()
            it.navigationBarColor = Color.White.toArgb()
            WindowInsetsControllerCompat(it, it.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    Scaffold(
        containerColor = JournalColors.Background,
        topBar = {
            if (selectedMemberId > 0L || (tab != MainTab.HOME && tab != MainTab.SETTINGS)) {
                JournalTopBar(
                    title = title,
                    onBack = { if (selectedMemberId > 0L) selectedMemberId = 0L else tab = MainTab.HOME },
                    onAction = if (selectedMemberId == 0L) addAction else null,
                    actionDescription = addDescription,
                )
            }
        },
        bottomBar = {
            if (selectedMemberId == 0L) {
                FruitNavigationBar(MainTab.values().map { it.label }, tab.ordinal) { tab = MainTab.values()[it] }
            }
        },
        floatingActionButton = {
            if (selectedMemberId == 0L && tab != MainTab.SETTINGS) {
                JournalAddButton(addDescription, addAction)
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
                        runCatching {
                            WeChatShareService.share(
                                context,
                                ExportService.suggestedName(member, format),
                                format.mimeType,
                            ) { output ->
                                ExportService.write(
                                    output,
                                    format,
                                    member,
                                    repository.entriesForMember(member.id),
                                    repository.milestonesForMember(member.id),
                                )
                            }
                        }.onSuccess { notify("正在打开微信……") }
                            .onFailure { notify(it.message ?: "无法打开微信分享") }
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
                    repository = repository,
                    refresh = refresh,
                    onBackup = {
                        runCatching {
                            WeChatShareService.share(
                                context,
                                "日报纪念册备份_${LocalDate.now()}.rhb",
                                "application/octet-stream",
                            ) { output -> repository.writeBackup(output) }
                        }.onSuccess { notify("备份已生成，正在打开微信……") }
                            .onFailure { notify(it.message ?: "无法打开微信分享") }
                    },
                    onSaveBackup = { backupLauncher.launch("日报纪念册备份_${LocalDate.now()}.rhb") },
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
        JournalDialog(
            onDismissRequest = { restoreCandidate = null },
            title = { Text("确认恢复备份？") },
            text = {
                Text("备份包含 ${candidate.second.members} 位成员、${candidate.second.entries} 篇日报和 ${candidate.second.milestones} 条大事记。\n\n恢复会用备份内容替换当前所有数据，建议先导出一份当前备份。")
            },
            confirmButton = {
                JournalButton(onClick = {
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
    val month = YearMonth.from(selectedDate)
    val counts = remember(month, refresh) {
        buildMap {
            putAll(repository.entryCountsForMonth(month.minusMonths(1).toString()))
            putAll(repository.entryCountsForMonth(month.toString()))
            putAll(repository.entryCountsForMonth(month.plusMonths(1).toString()))
        }
    }
    val entries = remember(selectedDate, refresh) { repository.entriesForDate(selectedDate.toString()) }
    val listState = rememberLazyListState()
    val rowCount = (month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7
    val expandedHeight = (62 + 30 + rowCount * 60 + 24).dp
    val collapseRangePx = with(LocalDensity.current) { ((rowCount - 1) * 60).dp.toPx() }
    val collapseState = remember(collapseRangePx) { CalendarCollapseState(collapseRangePx) }
    val nestedScrollConnection = remember(collapseState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || collapseState.offsetPx >= collapseState.maxOffsetPx) return Offset.Zero
                val consumed = (-available.y).coerceAtMost(collapseState.maxOffsetPx - collapseState.offsetPx)
                collapseState.offsetPx += consumed
                return Offset(0f, -consumed)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || collapseState.offsetPx <= 0f) return Offset.Zero
                val expanded = available.y.coerceAtMost(collapseState.offsetPx)
                collapseState.offsetPx -= expanded
                return Offset(0f, expanded)
            }
        }
    }
    val selectDate: (LocalDate) -> Unit = { onSelectedDateChange(it.toString()) }
    val scope = rememberCoroutineScope()
    val isCalendarCollapsed by remember(collapseState) { derivedStateOf { collapseState.fraction >= .5f } }
    fun toggleCalendar() {
        scope.launch {
            animate(collapseState.offsetPx, if (collapseState.fraction < .5f) collapseState.maxOffsetPx else 0f, animationSpec = tween(220)) { value, _ ->
                collapseState.offsetPx = value
            }
        }
    }
    Column(Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).collapsibleCalendarHeight(collapseState, expandedHeight)) {
            MorphingCalendar(
                month, selectedDate, counts, rowCount, collapseState,
                onMonthChange = { selectDate(it.atDay(selectedDate.dayOfMonth.coerceAtMost(it.lengthOfMonth()))) },
                onDateSelected = selectDate,
                onWeekChange = selectDate,
            )
        }
        Box(Modifier.fillMaxWidth().height(24.dp).clickable { toggleCalendar() },contentAlignment=Alignment.Center) {
            JournalIcon(if(isCalendarCollapsed) JournalSymbol.DOWN else JournalSymbol.UP,
                if(isCalendarCollapsed) "展开月历" else "收起月历",Modifier.size(22.dp),JournalColors.Border)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(selectedDate==LocalDate.now()) "今日" else selectedDate.format(DateTimeFormatter.ofPattern("M月d日")),
                Modifier.weight(1f),fontSize=17.sp,color=JournalColors.Muted)
            Text("${entries.size}",fontSize=16.sp,color=JournalColors.Muted)
            Spacer(Modifier.width(7.dp))
            JournalIcon(JournalSymbol.DOWN,modifier=Modifier.size(15.dp),color=JournalColors.Muted)
        }
        LazyColumn(
            state=listState,
            modifier=Modifier.fillMaxWidth().weight(1f),
            contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=100.dp),
            verticalArrangement=Arrangement.spacedBy(3.dp),
        ) {
            if(entries.isEmpty()) item {
                Box(Modifier.fillMaxWidth().heightIn(min=210.dp),contentAlignment=Alignment.Center) {
                    EmptyState("当天还没有日报", "点击“+”记录今天的成长", compact=true)
                }
            } else items(entries,key={it.id}) { TimelineEntry(it) }
        }
    }
}

private fun Modifier.collapsibleCalendarHeight(
    state: CalendarCollapseState,
    expandedHeight: androidx.compose.ui.unit.Dp,
): Modifier = layout { measurable, constraints ->
    val expandedHeightPx = (expandedHeight - 24.dp).roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
    val minimumHeight = (expandedHeightPx - state.maxOffsetPx).roundToInt().coerceIn(0, expandedHeightPx)
    val currentHeightPx = (expandedHeightPx - state.offsetPx.roundToInt()).coerceIn(minimumHeight, expandedHeightPx)
    val placeable = measurable.measure(constraints.copy(minHeight=expandedHeightPx,maxHeight=expandedHeightPx))
    layout(placeable.width,currentHeightPx) { placeable.placeRelative(0,0) }
}

@Composable
private fun MorphingCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    counts: Map<String,Int>,
    rowCount: Int,
    collapseState: CalendarCollapseState,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onWeekChange: (LocalDate) -> Unit,
) {
    val context=LocalContext.current
    val gridStart=month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value-1).toLong())
    val selectedWeek=((selectedDate.toEpochDay()-gridStart.toEpochDay())/7).toInt().coerceIn(0,rowCount-1)
    val translation=with(LocalDensity.current) { -(selectedWeek*60.dp.toPx()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={ if(collapseState.fraction>=.6f) onWeekChange(selectedDate.minusWeeks(1)) else onMonthChange(month.minusMonths(1)) }) {
                JournalIcon(JournalSymbol.BACK,"上一月或上一周",Modifier.size(23.dp))
            }
            Row(Modifier.weight(1f).heightIn(min=48.dp).clickable { showDatePicker(context,selectedDate,onDateSelected) },
                horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                Text("${month.year}.${month.monthValue.toString().padStart(2,'0')}",fontSize=23.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                JournalIcon(JournalSymbol.DOWN,"选择日期",Modifier.size(13.dp))
            }
            IconButton(onClick={ if(collapseState.fraction>=.6f) onWeekChange(selectedDate.plusWeeks(1)) else onMonthChange(month.plusMonths(1)) }) {
                JournalIcon(JournalSymbol.NEXT,"下一月或下一周",Modifier.size(23.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=7.dp).height(30.dp),verticalAlignment=Alignment.CenterVertically) {
            listOf("一","二","三","四","五","六","日").forEachIndexed { index,label ->
                Text(label,Modifier.weight(1f),fontSize=14.sp,textAlign=TextAlign.Center,
                    color=if(index>=5) JournalColors.Coral else JournalColors.Ink)
            }
        }
        repeat(rowCount) { week ->
            Row(Modifier.fillMaxWidth().padding(horizontal=7.dp).height(60.dp).graphicsLayer {
                val progress=collapseState.fraction
                if(week==selectedWeek) { alpha=1f;translationY=translation*progress }
                else alpha=(1f-progress*1.8f).coerceIn(0f,1f)
            }) {
                repeat(7) { weekday ->
                    val date=gridStart.plusDays((week*7+weekday).toLong())
                    CalendarDay(date,date==selectedDate,(counts[date.toString()]?:0)>0,date.monthValue!=month.monthValue,onDateSelected)
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalendarDay(date: LocalDate,selected: Boolean,hasEntries: Boolean,muted: Boolean,onClick: (LocalDate)->Unit) {
    val lunar=remember(date) { lunarDateLabel(date) }
    val fontScale=LocalDensity.current.fontScale
    val foreground=when { selected->Color.White; muted->JournalColors.Muted.copy(alpha=.48f);else->JournalColors.Ink }
    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clickable { onClick(date) },contentAlignment=Alignment.Center) {
        val diameter=minOf(maxWidth-2.dp,54.dp)
        // Keep the two-line date inside its cell even with large system text.
        val textScale=diameter.value/54f*fontScale.coerceAtMost(1.1f)/fontScale
        Box(Modifier.size(diameter).clip(CircleShape).background(if(selected) JournalColors.Coral else Color.Transparent),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(if(date==LocalDate.now()) "今" else "${date.dayOfMonth}",fontSize=(19*textScale).sp,lineHeight=(24*textScale).sp,fontWeight=FontWeight.SemiBold,color=foreground)
                Text(lunar,fontSize=(12*textScale).sp,lineHeight=(17*textScale).sp,color=if(selected) Color.White else if(muted) JournalColors.Muted.copy(alpha=.48f) else JournalColors.Muted)
                Box(Modifier.size(4.dp).clip(CircleShape).background(if(hasEntries) { if(selected) Color.White else JournalColors.Muted } else Color.Transparent))
            }
        }
    }
}

@Composable
private fun TimelineEntry(entry: DailyEntry, onDelete: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.Top) {
        Box(Modifier.padding(top=5.dp).size(16.dp).border(1.4.dp,JournalColors.Gold,CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(entry.memberName,Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.Medium)
                if(onDelete!=null) IconButton(onClick=onDelete,modifier=Modifier.size(40.dp)) {
                    JournalIcon(JournalSymbol.DELETE,"删除日报",Modifier.size(19.dp),JournalColors.Muted)
                }
            }
            Text("${entry.date}  ${entry.time}",fontSize=13.sp,color=JournalColors.Muted)
            Spacer(Modifier.height(6.dp))
            Text(entry.content,style=MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MilestoneTimelineItem(milestone: Milestone, onDelete: (() -> Unit)? = null) {
    BlushCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.Top) {
            JournalIcon(JournalSymbol.STAR,modifier=Modifier.padding(top=4.dp).size(21.dp),color=JournalColors.Gold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(milestone.memberName,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    if(onDelete!=null) IconButton(onClick=onDelete,modifier=Modifier.size(36.dp)) {
                        JournalIcon(JournalSymbol.DELETE,"删除大事记",Modifier.size(18.dp),JournalColors.Muted)
                    }
                }
                Text(milestone.date,fontSize=13.sp,color=JournalColors.Muted)
                Spacer(Modifier.height(7.dp))
                Text(milestone.content,style=MaterialTheme.typography.bodyLarge)
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
        EmptyState("还没有大事记", "点击右下角 + 记录团队成员的重要时刻")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
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
        EmptyState("还没有团队成员", "点击右下角 + 建立档案")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val achieved = stats.count { it.rewardReached }
                BlushCard(color = JournalColors.Cream, borderColor = Color(0xFFF4E5C6)) {
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
    BlushCard(Modifier.fillMaxWidth().clickable(onClick=onClick)) {
        Column(Modifier.padding(17.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(53.dp).clip(CircleShape).background(JournalColors.Background),contentAlignment=Alignment.Center) {
                    Text(stats.member.name.take(1),fontSize=23.sp,fontWeight=FontWeight.Medium)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(stats.member.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text(listOf(stats.member.major,stats.member.grade,stats.member.rank,stats.member.tag)
                        .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "档案待完善" },
                        style=MaterialTheme.typography.bodySmall,color=JournalColors.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                IconButton(onClick=onEdit) { JournalIcon(JournalSymbol.EDIT,"编辑成员",Modifier.size(23.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("记录 ${stats.reportDays} 天",fontSize=14.sp)
                Text(if(stats.rewardReached) "已达成 90 天" else "还差 ${90-stats.reportDays} 天",fontSize=13.sp,color=JournalColors.Muted)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator((stats.reportDays/90f).coerceIn(0f,1f),Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),color=JournalColors.Coral,trackColor=JournalColors.Background)
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("${stats.reportCount} 篇日报 · 查看成长时间轴",Modifier.weight(1f),fontSize=12.sp,color=JournalColors.Muted)
                JournalIcon(JournalSymbol.NEXT,modifier=Modifier.size(13.dp),color=JournalColors.Muted)
            }
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
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BlushCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(member.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
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
                        JournalButton(onClick = { showExport = true }, modifier = Modifier.weight(1f)) { Text("导出纪念册") }
                        JournalOutlinedButton(onClick = { onEdit(member) }) { JournalIcon(JournalSymbol.EDIT, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(4.dp)); Text("编辑") }
                        IconButton(onClick = { confirmDeleteMember = true }) { JournalIcon(JournalSymbol.DELETE, "删除成员", Modifier.size(22.dp), JournalColors.Muted) }
                    }
                }
            }
        }
        item {
            Text("个人大事记 · ${milestones.size} 条", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
        if (milestones.isEmpty()) item { EmptyState("还没有大事记", "可在“大事记”页点击右下角 + 添加", compact = true) }
        items(milestones, key = { "milestone-${it.id}" }) { milestone ->
            MilestoneTimelineItem(milestone) { deleteMilestone = milestone }
        }
        item {
            Text("日报时间轴 · ${entries.size} 篇", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (entries.isEmpty()) item { EmptyState("还没有日报", "在日报页点击右下角 + 进行录入", compact = true) }
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
        Text("$label：", color = JournalColors.Muted, modifier = Modifier.width((92f * LocalDensity.current.fontScale.coerceAtMost(1.4f)).dp))
        Text(value, Modifier.weight(1f))
    }
}

@Composable
private fun SettingsScreen(repository: DailyRepository, refresh: Int, onBackup: () -> Unit, onSaveBackup: () -> Unit, onRestore: () -> Unit) {
    val stats=remember(refresh) { repository.memberStats() }
    val milestoneCount=remember(refresh) { repository.allMilestones().size }
    var showProtection by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(bottomStart=40.dp,bottomEnd=40.dp)).background(JournalColors.Hero))
            BlushCard(Modifier.align(Alignment.BottomCenter).padding(horizontal=16.dp,vertical=12.dp).fillMaxWidth()) {
                Row(Modifier.padding(start=23.dp,end=20.dp,top=19.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(67.dp).clip(CircleShape).background(JournalColors.Background),contentAlignment=Alignment.Center) {
                        FruitIcon(Fruit.PEACH,false,Modifier.size(55.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("日报纪念册",fontSize=22.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("记录每一天的成长",fontSize=13.sp,color=JournalColors.Muted)
                    }
                    JournalIcon(JournalSymbol.BELL,modifier=Modifier.size(25.dp),color=JournalColors.Gold)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=14.dp)) {
                    JournalStat("${stats.size}","团队成员",Modifier.weight(1f))
                    JournalStat("${stats.sumOf { it.reportCount }}","收集日报",Modifier.weight(1f))
                    JournalStat("$milestoneCount","重要时刻",Modifier.weight(1f))
                }
            }
        }
        Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            BlushCard(Modifier.fillMaxWidth(),JournalColors.Cream,Color(0xFFF4E5C6)) {
                Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically) {
                    JournalIcon(JournalSymbol.SHIELD,modifier=Modifier.size(34.dp),color=JournalColors.Gold)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("本地数据保护",fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=JournalColors.GoldInk)
                        Text("离线记录 · 无需注册登录",fontSize=13.sp,color=JournalColors.GoldInk)
                    }
                }
            }
            BlushCard(Modifier.fillMaxWidth()) {
                SettingsRow("导出完整备份","生成 .rhb 文件并分享到微信",JournalSymbol.UPLOAD,Color(0xFF8970E8),onBackup)
                Divider(Modifier.padding(horizontal=14.dp),color=JournalColors.Border.copy(alpha=.65f))
                SettingsRow("另存到本机或网盘","保存一份完整的数据备份",JournalSymbol.FOLDER,Color(0xFFFFB544),onSaveBackup)
            }
            BlushCard(Modifier.fillMaxWidth()) {
                SettingsRow("恢复备份","导入之前保存的 .rhb 文件",JournalSymbol.DOWNLOAD,Color(0xFF41AEF1),onRestore)
                Divider(Modifier.padding(horizontal=14.dp),color=JournalColors.Border.copy(alpha=.65f))
                SettingsRow("更新与数据保护",null,JournalSymbol.SHIELD,Color(0xFF1CCCB6)) { showProtection=true }
            }
            Text("日报纪念册 · ${BuildConfig.VERSION_NAME}",Modifier.fillMaxWidth().padding(vertical=13.dp),textAlign=TextAlign.Center,fontSize=12.sp,color=JournalColors.Muted)
        }
    }
    if(showProtection) JournalDialog(
        onDismissRequest={showProtection=false},
        title={Text("更新与数据保护")},
        text={Text("成员、标签、日报和大事记均保存在当前手机。\n\n建议定期导出完整备份。更新时直接覆盖安装同签名的新版 APK，不要先卸载旧版。\n\n卸载应用会清除本地数据，换机后可导入 .rhb 备份恢复。")},
        confirmButton={JournalButton(onClick={showProtection=false}) { Text("知道了") }},
    )
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

    JournalDialog(
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
                        JournalOutlinedButton(onClick = { showMemberChoices = true }, modifier = Modifier.fillMaxWidth()) {
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
                    JournalOutlinedButton(
                        onClick = { showDatePicker(context, date) { date = it } },
                        modifier = Modifier.weight(1f),
                    ) { Text(date.toString()) }
                    JournalOutlinedButton(
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
            JournalButton(
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

    JournalDialog(
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
                        JournalOutlinedButton(onClick = { showMemberChoices = true }, modifier = Modifier.fillMaxWidth()) {
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
                JournalOutlinedButton(onClick = { showDatePicker(context, date) { date = it } }, modifier = Modifier.fillMaxWidth()) {
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
            JournalButton(
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
    var showNewTagDialog by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }

    JournalDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (member == null) "添加成员" else "编辑成员") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("姓名 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(major, { major = it }, label = { Text("专业") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(grade, { grade = it }, label = { Text("年级") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                JournalOutlinedButton(onClick = { showDatePicker(context, joinedDate) { joinedDate = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text("加入时间：$joinedDate")
                }
                Box {
                    JournalOutlinedButton(onClick = { showRankChoices = true }, modifier = Modifier.fillMaxWidth()) {
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
                    JournalOutlinedButton(onClick = { showTagChoices = true }, modifier = Modifier.fillMaxWidth()) {
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
                            onClick = { showTagChoices = false; showNewTagDialog = true },
                        )
                    }
                }
            }
        },
        confirmButton = {
            JournalButton(
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

    if (showNewTagDialog) {
        JournalDialog(
            onDismissRequest = { showNewTagDialog = false; newTag = "" },
            title = { Text("新建标签") },
            text = {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("标签名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                JournalButton(
                    enabled = newTag.isNotBlank(),
                    onClick = {
                        tag = onAddTag(newTag)
                        newTag = ""
                        showNewTagDialog = false
                    },
                ) { Text("创建并选中") }
            },
            dismissButton = {
                TextButton(onClick = { showNewTagDialog = false; newTag = "" }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ExportFormatDialog(member: Member, onDismiss: () -> Unit, onSelect: (ExportFormat) -> Unit) {
    JournalDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出 ${member.name} 的纪念册") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("导出内容包含个人档案、大事记、统计与全部日报时间轴。选择任意格式后直接打开微信，以文件形式分享。")
                ExportFormat.values().forEach { format ->
                    JournalOutlinedButton(onClick = { onSelect(format) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${format.label}  (.${format.extension}) · 微信分享")
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
    JournalDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { JournalButton(onClick = onConfirm) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EmptyState(title: String, subtitle: String, compact: Boolean = false) {
    Box(Modifier.fillMaxWidth().then(if(compact) Modifier else Modifier.fillMaxHeight()).padding(22.dp),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            EmptyJournalIllustration(Modifier.size(if(compact) 72.dp else 142.dp))
            Spacer(Modifier.height(10.dp))
            Text(title,fontSize=if(compact) 16.sp else 18.sp,color=JournalColors.Ink.copy(alpha=.78f),textAlign=TextAlign.Center)
            Spacer(Modifier.height(7.dp))
            Text(subtitle,fontSize=14.sp,color=JournalColors.Muted,textAlign=TextAlign.Center)
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
