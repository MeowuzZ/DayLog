package com.dailymemory.app.importer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.security.MessageDigest

data class ImportRange(val start: LocalDateTime, val end: LocalDateTime) {
    init { require(!end.isBefore(start)) { "结束时间不能早于开始时间" } }
    fun contains(time: LocalDateTime) = !time.isBefore(start) && !time.isAfter(end)
    fun containsDate(time: LocalDateTime): Boolean {
        val date = time.toLocalDate()
        return !date.isBefore(start.toLocalDate()) && !date.isAfter(end.toLocalDate())
    }
}

object ReportRule {
    // Only the beginning of the original message is eligible; prose, bullets and leading spaces are not removed.
    private val prefix = Regex("^([0-9０-９]{1,2})\\p{P}([0-9０-９]{1,2})(?![0-9０-９])")
    fun matches(content: String): Boolean {
        val match = prefix.find(content) ?: return false
        fun number(value: String) = value.map { if (it in '０'..'９') '0' + (it - '０') else it }.joinToString("").toInt()
        return number(match.groupValues[1]) in 1..12 && number(match.groupValues[2]) in 1..31
    }
}

data class ChatCandidate(
    val group: String,
    val nickname: String,
    val content: String,
    val displayedTime: String,
    val referenceTime: LocalDateTime?,
) {
    val key: String get() = fingerprint(group, nickname, content, displayedTime, referenceTime?.toString().orEmpty())
}

fun fingerprint(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 255) }

/** A displayed WeChat time label is a reference, never an exact per-message timestamp. */
object ChatTimeParser {
    private val full = Regex("^(\\d{4})[年/.-](\\d{1,2})[月/.-](\\d{1,2})日?\\s+(.+)$")
    private val monthDay = Regex("^(\\d{1,2})月(\\d{1,2})日\\s+(.+)$")
    private val clock = Regex("^(凌晨|早上|上午|中午|下午|晚上)?\\s*(\\d{1,2})[:：](\\d{2})(?::(\\d{2}))?$")
    fun isLabel(text: String): Boolean = full.matches(text) || monthDay.matches(text) || clock.matches(text) ||
        Regex("^(今天|昨天|前天|星期[一二三四五六日天]|周[一二三四五六日天])\\s+.+$").matches(text)
    fun parse(text: String, today: LocalDate, range: ImportRange, fallbackDate: LocalDate = today): LocalDateTime? = runCatching {
        // A clock-only label belongs to the most recently resolved date on the page.
        // Relative labels below deliberately continue to resolve from the real `today` argument.
        var date = fallbackDate
        var timeText = text.trim()
        val f = full.matchEntire(timeText)
        val m = monthDay.matchEntire(timeText)
        if (f != null) {
            date = LocalDate.of(f.groupValues[1].toInt(), f.groupValues[2].toInt(), f.groupValues[3].toInt())
            timeText = f.groupValues[4]
        } else if (m != null) {
            // Resolve labels outside a same-year interval too, so an obviously different date can be discarded.
            // Across multiple years the label remains ambiguous unless only one matching date is in the interval.
            val dates = (range.start.year..range.end.year).mapNotNull { year ->
                runCatching { LocalDate.of(year, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
            }
            val unambiguous = if (range.start.year == range.end.year) dates else dates.filter {
                !it.isBefore(range.start.toLocalDate()) && !it.isAfter(range.end.toLocalDate())
            }
            date = unambiguous.singleOrNull() ?: return null
            timeText = m.groupValues[3]
        } else {
            val relative = Regex("^(今天|昨天|前天|星期[一二三四五六日天]|周[一二三四五六日天])\\s+(.+)$").matchEntire(timeText)
            if (relative != null) {
                val label = relative.groupValues[1]
                date = when (label) {
                    "今天" -> today
                    "昨天" -> today.minusDays(1)
                    "前天" -> today.minusDays(2)
                    else -> {
                        val day = "一二三四五六日".indexOf(label.last().let { if (it == '天') '日' else it }) + 1
                        today.minusDays(((today.dayOfWeek.value - day + 7) % 7).toLong())
                    }
                }
                timeText = relative.groupValues[2]
            }
        }
        val c = clock.matchEntire(timeText.trim()) ?: return null
        var hour = c.groupValues[2].toInt()
        val period = c.groupValues[1]
        if (period.isNotEmpty()) {
            require(hour in 0..12)
            if (period in listOf("下午", "晚上", "中午") && hour < 12) hour += 12
            if (period in listOf("凌晨", "早上", "上午") && hour == 12) hour = 0
        }
        LocalDateTime.of(date, LocalTime.of(hour, c.groupValues[3].toInt(), c.groupValues[4].toIntOrNull() ?: 0))
    }.getOrNull()
}

/** Small immutable snapshot, so the parser can be tested without WeChat or Android nodes. */
data class ChatText(val text: String, val description: String = "", val top: Int = 0, val left: Int = 0)
data class ChatRow(val labels: List<ChatText>)
data class ParsedPage(val candidates: List<ChatCandidate>, val observedTimes: List<LocalDateTime>) {
    val latestObservedTime: LocalDateTime? get() = observedTimes.maxOrNull()
}

object ChatPageParser {
    fun parse(group: String, rows: List<ChatRow>, today: LocalDate, range: ImportRange): ParsedPage {
        var timeLabel = ""
        var reference: LocalDateTime? = null
        var lastResolvedDate: LocalDate? = null
        val candidates = mutableListOf<ChatCandidate>()
        val observed = mutableListOf<LocalDateTime>()
        rows.forEach { row ->
            val labels = row.labels.sortedWith(compareBy<ChatText> { it.top }.thenBy { it.left })
            val avatar = labels.map { it.description }.firstOrNull { it.endsWith("头像") }
                ?.removeSuffix("头像")?.removeSuffix("的")?.trim().orEmpty()
            val bodyIndex = labels.indexOfLast {
                it.text.isNotBlank() && !it.description.endsWith("头像") && it.text !in setOf("已读", "未读", "发送失败")
            }
            labels.forEachIndexed { index, item ->
                val isBody = index == bodyIndex && ReportRule.matches(item.text) &&
                    (!ChatTimeParser.isLabel(item.text) || avatar.isNotEmpty() || labels.take(index).any {
                        it.text.isNotBlank() && !ChatTimeParser.isLabel(it.text)
                })
                if (!isBody && ChatTimeParser.isLabel(item.text)) {
                    timeLabel = item.text
                    reference = ChatTimeParser.parse(item.text, today, range, lastResolvedDate ?: today)
                    reference?.let {
                        lastResolvedDate = it.toLocalDate()
                        observed.add(it)
                    }
                } else if (isBody) {
                    // Only labels inside the same message row may identify its sender.
                    val nickname = avatar.ifEmpty {
                        labels.take(index).lastOrNull {
                            it.text.isNotBlank() && it.text.length <= 80 && !ChatTimeParser.isLabel(it.text) &&
                                !ReportRule.matches(it.text) && it.text !in setOf("已读", "未读", "发送失败")
                        }?.text.orEmpty()
                    }
                    // WeChat displays one sparse time label for a run of messages. Its clock value is
                    // therefore only a review hint: filter clearly different dates, never a same-day minute.
                    if (reference == null || range.containsDate(reference!!)) {
                        candidates.add(ChatCandidate(group, nickname, item.text, timeLabel, reference))
                    }
                }
            }
        }
        return ParsedPage(candidates.distinctBy { it.key }, observed)
    }
}

class CandidateQueue {
    private val seen = mutableSetOf<String>()
    private val items = linkedMapOf<String, ChatCandidate>()
    val pending: List<ChatCandidate> get() = items.values.toList()
    fun add(candidates: List<ChatCandidate>) {
        candidates.forEach { candidate ->
            if (!seen.add(candidate.key)) return@forEach
            // An overlapping row can lose its time or avatar at a screen edge. Reconcile that partial
            // observation with its complete version; keep messages with different known times separate.
            val overlap = items.values.firstOrNull {
                it.group == candidate.group && it.content == candidate.content &&
                    (it.nickname == candidate.nickname || it.nickname.isBlank() || candidate.nickname.isBlank()) &&
                    (it.referenceTime == candidate.referenceTime || it.referenceTime == null || candidate.referenceTime == null)
            }
            if (overlap == null) items[candidate.key] = candidate else {
                val merged = overlap.copy(
                    nickname = overlap.nickname.ifBlank { candidate.nickname },
                    displayedTime = if (overlap.referenceTime == null) candidate.displayedTime else overlap.displayedTime,
                    referenceTime = overlap.referenceTime ?: candidate.referenceTime,
                )
                items.remove(overlap.key)
                seen.add(merged.key)
                items[merged.key] = merged
            }
        }
    }
    fun remove(key: String) { items.remove(key) }
    fun clear() { seen.clear(); items.clear() }
}
