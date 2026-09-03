package com.dailymemory.app.importer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dailymemory.app.data.Member
import org.json.JSONObject

/** Pending chat text lives only in this process, never in a file, log or backup. */
object ImportSession {
    enum class Stage { SETUP, READY, CAPTURING, REVIEW }
    var stage by mutableStateOf(Stage.SETUP)
        private set
    var pending by mutableStateOf(emptyList<ChatCandidate>())
        private set
    var status by mutableStateOf("")
    var exitRequested by mutableStateOf(false)
    var imported by mutableStateOf(0)
        private set
    var discarded by mutableStateOf(0)
        private set
    var skipped by mutableStateOf(0)
        private set
    var group = ""
        private set
    var range: ImportRange? = null
        private set
    private val queue = CandidateQueue()
    var generation = 0
        private set
    fun prepare(group: String, range: ImportRange) {
        clear()
        this.group = group.trim()
        this.range = range
        stage = Stage.READY
        status = "进入目标群，在悬浮条点“开始提取”"
    }
    fun startCapture() { stage = Stage.CAPTURING; status = "正在读取已显示的消息并向前翻页" }
    fun pause(message: String) {
        if (stage !in setOf(Stage.READY, Stage.CAPTURING)) return
        stage = Stage.READY
        status = message
    }
    fun add(items: List<ChatCandidate>) {
        queue.add(items)
        pending = queue.pending.sortedWith(compareBy<ChatCandidate> { it.referenceTime == null }.thenBy { it.referenceTime })
    }
    fun review(message: String) { stage = Stage.REVIEW; status = message }
    fun resolve(key: String, accepted: Boolean, inserted: Boolean = accepted) {
        check(pending.any { it.key == key })
        queue.remove(key); pending = pending.filterNot { it.key == key }
        if (!accepted) discarded++ else if (inserted) imported++ else skipped++
    }
    fun clear() {
        generation++
        queue.clear(); pending = emptyList(); range = null; group = ""
        imported = 0; discarded = 0; skipped = 0; exitRequested = false; status = ""; stage = Stage.SETUP
    }
}

class AliasHistory(context: Context) {
    private val preferences = context.getSharedPreferences("wechat_import_aliases", Context.MODE_PRIVATE)
    fun last(group: String, nickname: String, members: List<Member>): Member? {
        if (nickname.isBlank()) return null
        val json = runCatching { JSONObject(preferences.getString(fingerprint(group, nickname), "")!!) }.getOrNull() ?: return null
        return members.firstOrNull { it.id == json.optLong("id") && it.createdAt == json.optLong("createdAt") }
    }
    fun remember(group: String, nickname: String, member: Member) {
        if (nickname.isBlank()) return
        val json = JSONObject().put("id", member.id).put("createdAt", member.createdAt).toString()
        preferences.edit().putString(fingerprint(group, nickname), json).apply()
    }
}
