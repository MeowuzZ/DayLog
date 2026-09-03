package com.dailymemory.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DailyRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = DailyDatabase(appContext)

    fun members() = db.members()
    fun member(id: Long) = db.member(id)
    fun memberStats() = db.memberStats()
    fun entriesForDate(date: String) = db.entriesForDate(date)
    fun entriesForMember(memberId: Long) = db.entriesForMember(memberId)
    fun entryCountsForMonth(month: String) = db.entryCountsForMonth(month)
    fun milestonesForMember(memberId: Long) = db.milestonesForMember(memberId)
    fun allMilestones() = db.allMilestones()
    fun tags() = db.tags()

    fun saveMember(member: Member): Long {
        require(member.name.isNotBlank()) { "请填写姓名" }
        if (member.tag.isNotBlank()) db.insertTag(member.tag)
        return if (member.id == 0L) db.insertMember(member) else {
            db.updateMember(member)
            member.id
        }
    }

    fun deleteMember(id: Long) = db.deleteMember(id)

    fun addEntry(entry: DailyEntry): Long {
        require(entry.memberId > 0) { "请选择团队成员" }
        require(entry.content.isNotBlank()) { "请填写日报内容" }
        return db.insertEntry(entry)
    }

    fun deleteEntry(id: Long) = db.deleteEntry(id)

    fun addImportedEntry(entry: DailyEntry): Boolean {
        require(db.member(entry.memberId) != null) { "所选成员已不存在，请重新选择" }
        require(entry.content.isNotBlank()) { "日报内容不能为空" }
        return db.insertImportedEntry(entry)
    }

    fun addMilestone(milestone: Milestone): Long {
        require(milestone.memberId > 0) { "请选择团队成员" }
        require(milestone.content.isNotBlank()) { "请填写大事记内容" }
        return db.insertMilestone(milestone)
    }

    fun deleteMilestone(id: Long) = db.deleteMilestone(id)

    fun addTag(name: String): String {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "标签不能为空" }
        db.insertTag(normalized)
        return normalized
    }

    fun writeBackup(uri: Uri): BackupSummary {
        return appContext.contentResolver.openOutputStream(uri, "w")!!.use { output ->
            writeBackup(output)
        }
    }

    fun writeBackup(output: OutputStream): BackupSummary {
        val members = db.members()
        val entries = db.allEntries()
        val milestones = db.allMilestones()
        val tags = db.tags()
        val json = JSONObject().apply {
            put("format", "daily-memory-backup")
            put("schemaVersion", 2)
            put("createdAt", System.currentTimeMillis())
            put("members", JSONArray().apply { members.forEach { put(it.toJson()) } })
            put("entries", JSONArray().apply { entries.forEach { put(it.toJson()) } })
            put("milestones", JSONArray().apply { milestones.forEach { put(it.toJson()) } })
            put("tags", JSONArray().apply { tags.forEach { put(it) } })
        }.toString(2)

        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return BackupSummary(members.size, entries.size, milestones.size)
    }

    fun readBackupSummary(uri: Uri): BackupSummary {
        val data = readBackup(uri)
        return BackupSummary(data.members.size, data.entries.size, data.milestones.size)
    }

    fun restoreBackup(uri: Uri): BackupSummary {
        val data = readBackup(uri)
        db.replaceAll(data.members, data.entries, data.milestones, data.tags)
        return BackupSummary(data.members.size, data.entries.size, data.milestones.size)
    }

    private fun readBackup(uri: Uri): BackupData {
        val bytes = appContext.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && entry.name != "backup.json") entry = zip.nextEntry
                require(entry != null) { "备份文件缺少 backup.json" }
                ByteArrayOutputStream().also { zip.copyTo(it) }.toByteArray()
            }
        }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("format") == "daily-memory-backup") { "不支持的备份格式" }
        val schemaVersion = root.optInt("schemaVersion")
        require(schemaVersion in 1..2) { "备份版本暂不受支持" }

        val membersJson = root.getJSONArray("members")
        val legacyBios = mutableMapOf<Long, String>()
        val members = buildList {
            repeat(membersJson.length()) { index ->
                val item = membersJson.getJSONObject(index)
                val member = item.toMember()
                add(member)
                item.optString("bio").takeIf { it.isNotBlank() }?.let { legacyBios[member.id] = it }
            }
        }
        require(members.map { it.id }.toSet().size == members.size) { "备份中存在重复成员" }
        val memberIds = members.map { it.id }.toSet()

        val entriesJson = root.getJSONArray("entries")
        val entries = buildList {
            repeat(entriesJson.length()) { index ->
                val item = entriesJson.getJSONObject(index).toEntry()
                require(item.memberId in memberIds) { "备份中的日报无法匹配成员" }
                add(item)
            }
        }
        require(entries.map { it.id }.toSet().size == entries.size) { "备份中存在重复日报" }

        val milestones = if (schemaVersion >= 2) {
            val milestonesJson = root.optJSONArray("milestones") ?: JSONArray()
            buildList {
                repeat(milestonesJson.length()) { index ->
                    val item = milestonesJson.getJSONObject(index).toMilestone()
                    require(item.memberId in memberIds) { "备份中的大事记无法匹配成员" }
                    add(item)
                }
            }
        } else {
            legacyBios.entries.mapIndexed { index, item ->
                val member = members.first { it.id == item.key }
                Milestone(
                    id = index + 1L,
                    memberId = member.id,
                    date = member.joinedDate.ifBlank {
                        Instant.ofEpochMilli(member.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    },
                    content = "原个人简介：${item.value}",
                    createdAt = member.createdAt,
                )
            }
        }
        require(milestones.map { it.id }.toSet().size == milestones.size) { "备份中存在重复大事记" }

        val tags = buildSet {
            val tagsJson = root.optJSONArray("tags") ?: JSONArray()
            repeat(tagsJson.length()) { index -> tagsJson.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add) }
            members.map { it.tag }.filter { it.isNotBlank() }.forEach(::add)
        }.toList()
        return BackupData(members, entries, milestones, tags)
    }
}

private data class BackupData(
    val members: List<Member>,
    val entries: List<DailyEntry>,
    val milestones: List<Milestone>,
    val tags: List<String>,
)

private fun Member.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("major", major)
    put("grade", grade)
    put("joinedDate", joinedDate)
    put("rank", rank)
    put("tag", tag)
    put("createdAt", createdAt)
}

private fun DailyEntry.toJson() = JSONObject().apply {
    put("id", id)
    put("memberId", memberId)
    put("date", date)
    put("time", time)
    put("content", content)
    put("createdAt", createdAt)
}

private fun Milestone.toJson() = JSONObject().apply {
    put("id", id)
    put("memberId", memberId)
    put("date", date)
    put("content", content)
    put("createdAt", createdAt)
}

private fun JSONObject.toMember() = Member(
    id = getLong("id"),
    name = getString("name"),
    major = optString("major"),
    grade = optString("grade"),
    joinedDate = optString("joinedDate"),
    rank = optString("rank"),
    tag = optString("tag"),
    createdAt = optLong("createdAt", System.currentTimeMillis()),
)

private fun JSONObject.toEntry() = DailyEntry(
    id = getLong("id"),
    memberId = getLong("memberId"),
    date = getString("date"),
    time = getString("time"),
    content = getString("content"),
    createdAt = optLong("createdAt", System.currentTimeMillis()),
)

private fun JSONObject.toMilestone() = Milestone(
    id = getLong("id"),
    memberId = getLong("memberId"),
    date = getString("date"),
    content = getString("content"),
    createdAt = optLong("createdAt", System.currentTimeMillis()),
)
