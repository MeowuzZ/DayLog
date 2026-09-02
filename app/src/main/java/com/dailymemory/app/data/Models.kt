package com.dailymemory.app.data

data class Member(
    val id: Long = 0,
    val name: String,
    val major: String = "",
    val grade: String = "",
    val joinedDate: String = "",
    val rank: String = "",
    val tag: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class Milestone(
    val id: Long = 0,
    val memberId: Long,
    val memberName: String = "",
    val date: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class DailyEntry(
    val id: Long = 0,
    val memberId: Long,
    val memberName: String = "",
    val date: String,
    val time: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class MemberStats(
    val member: Member,
    val reportDays: Int,
    val reportCount: Int,
) {
    val rewardReached: Boolean get() = reportDays >= 90
}

data class BackupSummary(val members: Int, val entries: Int, val milestones: Int = 0)

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    DOCX("Word", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("PDF", "pdf", "application/pdf"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    TEXT("纯文本", "txt", "text/plain"),
}
