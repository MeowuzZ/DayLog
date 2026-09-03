package com.dailymemory.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DailyDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE members (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                major TEXT NOT NULL DEFAULT '',
                grade TEXT NOT NULL DEFAULT '',
                joined_date TEXT NOT NULL DEFAULT '',
                bio TEXT NOT NULL DEFAULT '',
                rank TEXT NOT NULL DEFAULT '',
                tag TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE daily_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                member_id INTEGER NOT NULL,
                entry_date TEXT NOT NULL,
                entry_time TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(member_id) REFERENCES members(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_entries_date_time ON daily_entries(entry_date, entry_time)")
        db.execSQL("CREATE INDEX idx_entries_member_date ON daily_entries(member_id, entry_date)")
        createMilestoneTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE members ADD COLUMN rank TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE members ADD COLUMN tag TEXT NOT NULL DEFAULT ''")
            createMilestoneTables(db)
            db.execSQL(
                """
                INSERT INTO milestones(member_id, event_date, content, created_at)
                SELECT id,
                       CASE WHEN joined_date <> '' THEN joined_date
                            ELSE strftime('%Y-%m-%d', created_at / 1000, 'unixepoch', 'localtime') END,
                       '原个人简介：' || bio,
                       created_at
                FROM members WHERE trim(bio) <> ''
                """.trimIndent()
            )
        }
    }

    fun insertMember(member: Member): Long = writableDatabase.insertOrThrow(
        "members", null, member.values(includeId = false)
    )

    fun updateMember(member: Member) {
        writableDatabase.update("members", member.values(false), "id = ?", arrayOf(member.id.toString()))
    }

    fun deleteMember(id: Long) {
        writableDatabase.delete("members", "id = ?", arrayOf(id.toString()))
    }

    fun insertEntry(entry: DailyEntry): Long = writableDatabase.insertOrThrow(
        "daily_entries", null, entry.values(includeId = false)
    )

    fun insertImportedEntry(entry: DailyEntry): Boolean {
        var inserted = false
        writableDatabase.inTransaction {
            val exists = rawQuery(
                "SELECT 1 FROM daily_entries WHERE member_id = ? AND entry_date = ? AND substr(entry_time || ':00', 1, 8) = ? AND content = ? LIMIT 1",
                arrayOf(entry.memberId.toString(), entry.date, entry.time, entry.content.trim()),
            ).use { it.moveToFirst() }
            if (!exists) {
                insertOrThrow("daily_entries", null, entry.values(includeId = false))
                inserted = true
            }
        }
        return inserted
    }

    fun deleteEntry(id: Long) {
        writableDatabase.delete("daily_entries", "id = ?", arrayOf(id.toString()))
    }

    fun insertMilestone(milestone: Milestone): Long = writableDatabase.insertOrThrow(
        "milestones", null, milestone.values(includeId = false)
    )

    fun deleteMilestone(id: Long) {
        writableDatabase.delete("milestones", "id = ?", arrayOf(id.toString()))
    }

    fun insertTag(name: String) {
        writableDatabase.insertWithOnConflict(
            "member_tags",
            null,
            ContentValues().apply { put("name", name.trim()) },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun tags(): List<String> = readableDatabase.rawQuery(
        "SELECT name FROM member_tags ORDER BY name COLLATE NOCASE", null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun members(): List<Member> = readableDatabase.rawQuery(
        "SELECT * FROM members ORDER BY joined_date, created_at", null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.member()) } }

    fun member(id: Long): Member? = readableDatabase.rawQuery(
        "SELECT * FROM members WHERE id = ?", arrayOf(id.toString())
    ).use { cursor -> if (cursor.moveToFirst()) cursor.member() else null }

    fun memberStats(): List<MemberStats> = readableDatabase.rawQuery(
        """
        SELECT m.*, COUNT(DISTINCT e.entry_date) AS report_days, COUNT(e.id) AS report_count
        FROM members m LEFT JOIN daily_entries e ON e.member_id = m.id
        GROUP BY m.id ORDER BY report_days DESC, m.joined_date, m.created_at
        """.trimIndent(), null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    MemberStats(
                        member = cursor.member(),
                        reportDays = cursor.int("report_days"),
                        reportCount = cursor.int("report_count"),
                    )
                )
            }
        }
    }

    fun entriesForDate(date: String): List<DailyEntry> = entryQuery(
        """
        SELECT e.*, m.name AS member_name FROM daily_entries e
        JOIN members m ON m.id = e.member_id
        WHERE e.entry_date = ? ORDER BY e.entry_time, e.created_at
        """.trimIndent(), arrayOf(date)
    )

    fun entriesForMember(memberId: Long): List<DailyEntry> = entryQuery(
        """
        SELECT e.*, m.name AS member_name FROM daily_entries e
        JOIN members m ON m.id = e.member_id
        WHERE e.member_id = ? ORDER BY e.entry_date, e.entry_time, e.created_at
        """.trimIndent(), arrayOf(memberId.toString())
    )

    fun allEntries(): List<DailyEntry> = entryQuery(
        """
        SELECT e.*, m.name AS member_name FROM daily_entries e
        JOIN members m ON m.id = e.member_id
        ORDER BY e.entry_date, e.entry_time, e.created_at
        """.trimIndent(), emptyArray()
    )

    fun milestonesForMember(memberId: Long): List<Milestone> = milestoneQuery(
        """
        SELECT x.*, m.name AS member_name FROM milestones x
        JOIN members m ON m.id = x.member_id
        WHERE x.member_id = ? ORDER BY x.event_date DESC, x.created_at DESC
        """.trimIndent(), arrayOf(memberId.toString())
    )

    fun allMilestones(): List<Milestone> = milestoneQuery(
        """
        SELECT x.*, m.name AS member_name FROM milestones x
        JOIN members m ON m.id = x.member_id
        ORDER BY x.event_date DESC, x.created_at DESC
        """.trimIndent(), emptyArray()
    )

    fun entryCountsForMonth(month: String): Map<String, Int> = readableDatabase.rawQuery(
        """
        SELECT entry_date, COUNT(*) AS entry_count FROM daily_entries
        WHERE entry_date LIKE ? GROUP BY entry_date
        """.trimIndent(), arrayOf("$month%")
    ).use { cursor ->
        buildMap {
            while (cursor.moveToNext()) put(cursor.string("entry_date"), cursor.int("entry_count"))
        }
    }

    fun replaceAll(
        members: List<Member>,
        entries: List<DailyEntry>,
        milestones: List<Milestone>,
        tags: List<String>,
    ) {
        writableDatabase.inTransaction {
            delete("milestones", null, null)
            delete("daily_entries", null, null)
            delete("members", null, null)
            delete("member_tags", null, null)
            tags.forEach {
                insertWithOnConflict(
                    "member_tags",
                    null,
                    ContentValues().apply { put("name", it.trim()) },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            members.forEach { insertOrThrow("members", null, it.values(includeId = true)) }
            entries.forEach { insertOrThrow("daily_entries", null, it.values(includeId = true)) }
            milestones.forEach { insertOrThrow("milestones", null, it.values(includeId = true)) }
        }
    }

    private fun entryQuery(sql: String, args: Array<String>): List<DailyEntry> =
        readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.entry()) }
        }

    private fun milestoneQuery(sql: String, args: Array<String>): List<Milestone> =
        readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.milestone()) }
        }

    private inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    companion object {
        private const val DB_NAME = "daily_memory.db"
        private const val DB_VERSION = 2

        private fun createMilestoneTables(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS milestones (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    member_id INTEGER NOT NULL,
                    event_date TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(member_id) REFERENCES members(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_milestones_date ON milestones(event_date DESC, created_at DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_milestones_member_date ON milestones(member_id, event_date DESC)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS member_tags (
                    name TEXT PRIMARY KEY NOT NULL
                )
                """.trimIndent()
            )
        }
    }
}

private fun Member.values(includeId: Boolean) = ContentValues().apply {
    if (includeId) put("id", id)
    put("name", name.trim())
    put("major", major.trim())
    put("grade", grade.trim())
    put("joined_date", joinedDate)
    put("rank", rank.trim())
    put("tag", tag.trim())
    put("created_at", createdAt)
}

private fun DailyEntry.values(includeId: Boolean) = ContentValues().apply {
    if (includeId) put("id", id)
    put("member_id", memberId)
    put("entry_date", date)
    put("entry_time", time)
    put("content", content.trim())
    put("created_at", createdAt)
}

private fun Milestone.values(includeId: Boolean) = ContentValues().apply {
    if (includeId) put("id", id)
    put("member_id", memberId)
    put("event_date", date)
    put("content", content.trim())
    put("created_at", createdAt)
}

private fun Cursor.member() = Member(
    id = long("id"),
    name = string("name"),
    major = string("major"),
    grade = string("grade"),
    joinedDate = string("joined_date"),
    rank = string("rank"),
    tag = string("tag"),
    createdAt = long("created_at"),
)

private fun Cursor.entry() = DailyEntry(
    id = long("id"),
    memberId = long("member_id"),
    memberName = string("member_name"),
    date = string("entry_date"),
    time = string("entry_time"),
    content = string("content"),
    createdAt = long("created_at"),
)

private fun Cursor.milestone() = Milestone(
    id = long("id"),
    memberId = long("member_id"),
    memberName = string("member_name"),
    date = string("event_date"),
    content = string("content"),
    createdAt = long("created_at"),
)

private fun Cursor.index(name: String) = getColumnIndexOrThrow(name)
private fun Cursor.string(name: String) = getString(index(name)) ?: ""
private fun Cursor.long(name: String) = getLong(index(name))
private fun Cursor.int(name: String) = getInt(index(name))
