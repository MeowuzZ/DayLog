package com.dailymemory.app.importer

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import com.dailymemory.app.data.DailyEntry
import com.dailymemory.app.data.DailyRepository
import com.dailymemory.app.data.Member
import com.dailymemory.app.ui.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ChatImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        val repository = DailyRepository(applicationContext)
        setContent { DailyMemoryTheme { ImportScreen(repository) } }
    }
    override fun onDestroy() {
        if (isFinishing) ImportSession.clear()
        super.onDestroy()
    }
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun finishImport(complete: Boolean) {
        setResult(if (complete) RESULT_OK else RESULT_CANCELED)
        ImportSession.clear(); finish()
    }
    private fun openWeChat() {
        val launch = packageManager.getLaunchIntentForPackage(WeChatCaptureService.WECHAT)
        if (launch == null) message("未找到微信，请安装微信并登录后重试")
        else runCatching { startActivity(launch) }.onFailure { message("无法打开微信：${it.message}") }
    }
    private fun captureEnabled(): Boolean {
        val expected = ComponentName(this, WeChatCaptureService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    @Composable
    private fun ImportScreen(repository: DailyRepository) {
        var confirmExit by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<ChatCandidate?>(null) }
        val members = repository.members()
        val history = remember { AliasHistory(applicationContext) }
        BackHandler { confirmExit = true }
        LaunchedEffect(ImportSession.exitRequested) {
            if (ImportSession.exitRequested) { confirmExit = true; ImportSession.exitRequested = false }
        }
        Scaffold(
            containerColor = JournalColors.Background,
            topBar = { JournalTopBar(if (ImportSession.stage == ImportSession.Stage.REVIEW) "审核导入日报" else "从微信导入日报", { confirmExit = true }) },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                when (ImportSession.stage) {
                    ImportSession.Stage.SETUP -> SetupScreen(members.isNotEmpty())
                    ImportSession.Stage.READY, ImportSession.Stage.CAPTURING -> Column(
                        Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("目标群：${ImportSession.group}", style = MaterialTheme.typography.titleMedium)
                        Text("时间范围：\n${ImportSession.range?.start?.display()} 至\n${ImportSession.range?.end?.display()}")
                        Text(ImportSession.status, color = JournalColors.Muted)
                        Text("已找到 ${ImportSession.pending.size} 条候选消息。请选择目标群，并尽量定位到结束时间附近。开始提取后会向更早的消息翻页。")
                        JournalButton({ openWeChat() }, Modifier.fillMaxWidth()) { Text("打开微信选择群聊") }
                        JournalOutlinedButton({ ImportSession.review("已返回本次提取结果，请逐条核对") }, Modifier.fillMaxWidth()) { Text("结束提取并审核") }
                        Text("切换到其他应用或离开目标群会暂停提取。未处理内容仅在本次导入中临时保留。", style = MaterialTheme.typography.bodySmall, color = JournalColors.Muted)
                    }
                    ImportSession.Stage.REVIEW -> {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                            Text("待审核 ${ImportSession.pending.size} 条 · 已导入 ${ImportSession.imported} · 已删除 ${ImportSession.discarded}", fontWeight = FontWeight.Medium)
                            if (ImportSession.skipped > 0) Text("已跳过 ${ImportSession.skipped} 条重复日报", style = MaterialTheme.typography.bodySmall)
                            Text(ImportSession.status, style = MaterialTheme.typography.bodySmall, color = JournalColors.Muted)
                        }
                        if (ImportSession.pending.isEmpty()) Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("本次没有找到符合规则的消息。请确认群名、时间范围，以及微信是否允许读取屏幕内容。")
                            JournalButton({ ImportSession.clear() }) { Text("重新选择范围") }
                            JournalOutlinedButton({ finishImport(false) }) { Text("返回上一级") }
                        } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(ImportSession.pending, key = { it.key }) { candidate ->
                                BlushCard(Modifier.fillMaxWidth().clickable { selected = candidate }) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Text(candidate.nickname.ifEmpty { "昵称未显示，待补充" }, style = MaterialTheme.typography.titleMedium)
                                        Text(candidate.referenceTime?.display() ?: "发送时间未显示，待补充", color = JournalColors.Muted, fontSize = 13.sp)
                                        Text(candidate.content, maxLines = 5, overflow = TextOverflow.Ellipsis)
                                        Text("点开核对并选择团队成员", color = JournalColors.Ink, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        selected?.let { candidate ->
            ReviewDialog(candidate, members, history, onDismiss = { selected = null }, onResolve = { accepted, nickname, time, member ->
                if (accepted) {
                    runCatching {
                        val person = requireNotNull(member)
                        val timestamp = requireNotNull(time)
                        require(ImportSession.range?.contains(timestamp) == true) { "发送时间不在所选范围内，请重新核对" }
                        val inserted = repository.addImportedEntry(DailyEntry(memberId = person.id, date = timestamp.toLocalDate().toString(), time = timestamp.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")), content = candidate.content))
                        history.remember(candidate.group, nickname.trim(), person)
                        if (!inserted) message("同一成员已有相同时间和内容的日报，已跳过重复导入")
                        inserted
                    }.onFailure { message(it.message ?: "导入失败，请重试") }.onSuccess { inserted ->
                        ImportSession.resolve(candidate.key, true, inserted); selected = null
                        if (ImportSession.pending.isEmpty()) completeAndReturn()
                    }
                } else {
                    ImportSession.resolve(candidate.key, false); selected = null
                    if (ImportSession.pending.isEmpty()) completeAndReturn()
                }
            })
        }
        if (confirmExit) JournalDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("退出本次导入？") },
            text = { Text("尚未处理的 ${ImportSession.pending.size} 条提取内容将全部清空。已经导入的日报会保留，微信原消息不会删除。") },
            confirmButton = { JournalButton({ finishImport(false) }) { Text("清空并退出") } },
            dismissButton = { TextButton({ confirmExit = false }) { Text("继续处理") } },
        )
    }
    private fun completeAndReturn() {
        message("处理完成：导入 ${ImportSession.imported} 条，跳过重复 ${ImportSession.skipped} 条，删除 ${ImportSession.discarded} 条")
        finishImport(true)
    }

    @Composable
    private fun SetupScreen(hasMembers: Boolean) {
        var group by rememberSaveable { mutableStateOf("") }
        var start by rememberSaveable(saver = LocalDateTimeSaver) { mutableStateOf(LocalDate.now().atStartOfDay()) }
        var end by rememberSaveable(saver = LocalDateTimeSaver) { mutableStateOf(LocalDate.now().atTime(23, 59, 59)) }
        var consent by rememberSaveable { mutableStateOf(false) }
        var permissionRefresh by remember { mutableStateOf(0) }
        var showTextImport by remember { mutableStateOf(false) }
        var pasted by remember { mutableStateOf("") }
        val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissionRefresh++ }
        val enabled = remember(permissionRefresh) { captureEnabled() }
        fun rangeOrNull(): ImportRange? = runCatching { ImportRange(start, end) }.onFailure { message(it.message ?: "请选择有效范围") }.getOrNull()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BlushCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("选择群聊 · 按时间提取 · 逐条确认", style = MaterialTheme.typography.titleMedium)
                    Text("输入群名和时间范围后打开微信，手动进入该群；在悬浮条点击开始提取。建议先在微信按日期找到结束时间附近，再向更早的消息提取。", style = MaterialTheme.typography.bodyMedium)
                    Text("这是经你授权的屏幕读取：只能识别微信显示且允许读取的文字，不能保证获取全部历史。图片、语音、撤回消息不提取；相邻时间标签需要你再次核对。", style = MaterialTheme.typography.bodySmall, color = JournalColors.Muted)
                }
            }
            OutlinedTextField(group, { group = it }, Modifier.fillMaxWidth(), label = { Text("群聊名称（与微信顶部一致）") }, singleLine = true)
            DateTimeFields("开始时间", start) { start = it }
            DateTimeFields("结束时间", end, endOfMinute = true) { end = it }
            if (!hasMembers) Text("请先返回成员页添加团队成员，或先恢复一份完整备份。", color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth().clickable { consent = !consent }) {
                Checkbox(consent, { consent = it })
                Text("我同意仅在本次提取中读取所选微信群的屏幕文字，用于识别日报；内容仅在本机处理。", Modifier.padding(top = 10.dp).weight(1f), fontSize = 13.sp)
            }
            JournalOutlinedButton({
                settings.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }, Modifier.fillMaxWidth(), enabled = consent) { Text(if (enabled) "屏幕读取已开启 · 查看设置" else "开启日报导入屏幕读取") }
            JournalButton({
                val range = rangeOrNull() ?: return@JournalButton
                if (!captureEnabled()) { message("请先在无障碍设置中开启“日报导入屏幕读取”"); return@JournalButton }
                if (packageManager.getLaunchIntentForPackage(WeChatCaptureService.WECHAT) == null) { message("未找到微信，请安装微信并登录后重试"); return@JournalButton }
                ImportSession.prepare(group, range); openWeChat()
            }, Modifier.fillMaxWidth(), enabled = hasMembers && group.isNotBlank() && consent) { Text("打开微信，选择群聊") }
            TextButton({ showTextImport = true }) { Text("屏幕无法读取？粘贴聊天文本导入") }
            Text("部分 Android 13 及以上设备会提示受限设置。可在本应用的信息页允许受限设置，再回来开启屏幕读取。", style = MaterialTheme.typography.bodySmall, color = JournalColors.Muted)
        }
        if (showTextImport) JournalDialog(
            onDismissRequest = { showTextImport = false },
            title = { Text("粘贴聊天记录") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("每条消息格式如下，正文可以换行：\n[2026-09-03 18:30:00] 微信昵称\n9.3 今日工作内容\n\n只会提取符合开头规则且在所选时间范围内的消息。", fontSize = 13.sp)
                    OutlinedTextField(pasted, { if (it.length <= 500_000) pasted = it else message("每次最多粘贴 50 万字，请分段导入") }, Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp), label = { Text("聊天文本") })
                }
            },
            confirmButton = { JournalButton({
                if (!hasMembers || group.isBlank()) { message("请先填写群名并添加团队成员"); return@JournalButton }
                val range = rangeOrNull() ?: return@JournalButton
                val candidates = ChatTextImport.parse(group.trim(), pasted, range)
                if (candidates.isEmpty()) { message("没有找到符合格式、开头规则及时间范围的消息"); return@JournalButton }
                ImportSession.prepare(group, range); ImportSession.add(candidates)
                ImportSession.review("已从你粘贴的聊天文本提取，请核对昵称、发送时间和正文")
                pasted = ""; showTextImport = false
            }) { Text("提取候选日报") } },
            dismissButton = { TextButton({ showTextImport = false; pasted = "" }) { Text("取消") } },
        )
    }

    @Composable
    private fun ReviewDialog(candidate: ChatCandidate, members: List<Member>, history: AliasHistory, onDismiss: () -> Unit,
                             onResolve: (Boolean, String, LocalDateTime?, Member?) -> Unit) {
        var nickname by remember(candidate.key) { mutableStateOf(candidate.nickname) }
        var time by remember(candidate.key) { mutableStateOf(candidate.referenceTime) }
        var confirmedTime by remember(candidate.key) { mutableStateOf(false) }
        var memberId by remember(candidate.key) { mutableStateOf(0L) }
        var pickingMember by remember { mutableStateOf(false) }
        val last = history.last(candidate.group, nickname.trim(), members)
        val selectedMember = members.firstOrNull { it.id == memberId }
        JournalDialog(
            onDismissRequest = onDismiss,
            title = { Text("这是团队成员的日报吗？") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("来自：${candidate.group}", fontSize = 13.sp, color = JournalColors.Muted)
                    OutlinedTextField(nickname, { nickname = it; memberId = 0L }, Modifier.fillMaxWidth(), label = { Text("微信发送人昵称") }, singleLine = true)
                    Text(candidate.content)
                    Text("微信显示：${candidate.displayedTime.ifEmpty { "未显示时间" }}。相邻时间标签不等于精确发送时间，请核对后确认。", color = JournalColors.Muted, fontSize = 12.sp)
                    if (time == null) JournalOutlinedButton({
                        time = ImportSession.range!!.start
                        confirmedTime = false
                    }) { Text("补充发送日期与时间") }
                    time?.let { current -> DateTimeFields("发送时间", current) { time = it; confirmedTime = false } }
                    Row(Modifier.clickable(enabled = time != null) { confirmedTime = !confirmedTime }) {
                        Checkbox(confirmedTime, { confirmedTime = it }, enabled = time != null)
                        Text("已核对消息发送日期和时间", Modifier.padding(top = 11.dp), fontSize = 13.sp)
                    }
                    JournalOutlinedButton({ pickingMember = true }, Modifier.fillMaxWidth()) { Text(selectedMember?.name ?: "选择团队成员姓名") }
                    if (last != null) Text("该昵称上次对应：${last.name}", fontSize = 12.sp, color = JournalColors.Muted)
                }
            },
            confirmButton = { JournalButton({ onResolve(true, nickname, time, selectedMember) }, enabled = nickname.isNotBlank() && time != null && confirmedTime && selectedMember != null) { Text("是，导入日报") } },
            dismissButton = { TextButton({ onResolve(false, nickname, time, null) }) { Text("不是，删除此条") } },
        )
        if (pickingMember) JournalDialog(
            onDismissRequest = { pickingMember = false }, title = { Text("选择团队成员") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (last != null) item {
                        JournalOutlinedButton({ memberId = last.id; pickingMember = false }, Modifier.fillMaxWidth()) {
                            Text("上次该昵称为 ${last.name}")
                        }
                    }
                    items(members, key = { it.id }) { member ->
                        TextButton({ memberId = member.id; pickingMember = false }, Modifier.fillMaxWidth()) { Text(member.name) }
                    }
                }
            }, confirmButton = { TextButton({ pickingMember = false }) { Text("取消") } },
        )
    }

    @Composable
    private fun DateTimeFields(
        label: String,
        value: LocalDateTime,
        endOfMinute: Boolean = false,
        onChange: (LocalDateTime) -> Unit,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, fontSize = 13.sp, color = JournalColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JournalOutlinedButton({
                    DatePickerDialog(this@ChatImportActivity, { _, y, m, d -> onChange(LocalDateTime.of(LocalDate.of(y, m + 1, d), value.toLocalTime())) }, value.year, value.monthValue - 1, value.dayOfMonth).show()
                }, Modifier.weight(1f)) { Text(value.toLocalDate().toString(), fontSize = 13.sp) }
                JournalOutlinedButton({
                    TimePickerDialog(this@ChatImportActivity, { _, h, m ->
                        onChange(value.toLocalDate().atTime(h, m, if (endOfMinute) 59 else 0))
                    }, value.hour, value.minute, true).show()
                }, Modifier.weight(.65f)) { Text(value.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 13.sp) }
            }
        }
    }
}

private val LocalDateTimeSaver = Saver<MutableState<LocalDateTime>, String>(
    save = { it.value.toString() },
    restore = { mutableStateOf(LocalDateTime.parse(it)) },
)

private fun LocalDateTime.display(): String = format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
