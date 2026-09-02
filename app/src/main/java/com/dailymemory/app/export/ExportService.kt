package com.dailymemory.app.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.dailymemory.app.data.DailyEntry
import com.dailymemory.app.data.ExportFormat
import com.dailymemory.app.data.Member
import com.dailymemory.app.data.Milestone
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

object ExportService {
    fun write(
        context: Context,
        uri: Uri,
        format: ExportFormat,
        member: Member,
        entries: List<DailyEntry>,
        milestones: List<Milestone>,
    ) {
        context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
            write(output, format, member, entries, milestones)
        }
    }

    fun write(
        output: OutputStream,
        format: ExportFormat,
        member: Member,
        entries: List<DailyEntry>,
        milestones: List<Milestone>,
    ) {
        when (format) {
            ExportFormat.DOCX -> writeDocx(output, member, entries, milestones)
            ExportFormat.PDF -> writePdf(output, member, entries, milestones)
            ExportFormat.MARKDOWN -> output.write(markdown(member, entries, milestones).toByteArray(Charsets.UTF_8))
            ExportFormat.TEXT -> output.write(plainText(member, entries, milestones).toByteArray(Charsets.UTF_8))
        }
    }

    fun suggestedName(member: Member, format: ExportFormat): String {
        val safeName = member.name.replace(Regex("[\\/:*?\"<>|]"), "_")
        return "${safeName}_日报纪念册_${LocalDate.now()}.${format.extension}"
    }

    private fun markdown(member: Member, entries: List<DailyEntry>, milestones: List<Milestone>): String = buildString {
        appendLine("# ${member.name} 的日报纪念册")
        appendLine()
        appendLine("> 共记录 ${entries.map { it.date }.distinct().size} 天、${entries.size} 篇日报")
        appendLine()
        appendLine("## 个人档案")
        appendLine()
        appendLine("- **姓名**：${member.name}")
        appendLine("- **专业**：${member.major.ifBlank { "未填写" }}")
        appendLine("- **年级**：${member.grade.ifBlank { "未填写" }}")
        appendLine("- **加入时间**：${member.joinedDate.ifBlank { "未填写" }}")
        appendLine("- **职级**：${member.rank.ifBlank { "未设置" }}")
        appendLine("- **标签**：${member.tag.ifBlank { "未设置" }}")
        appendLine()
        appendLine("## 个人大事记")
        if (milestones.isEmpty()) appendLine("\n暂无大事记")
        milestones.forEach { milestone ->
            appendLine()
            appendLine("### ${milestone.date}")
            appendLine()
            appendLine(milestone.content)
        }
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## 日报时间轴")
        entries.forEach { entry ->
            appendLine()
            appendLine("### ${entry.date} ${entry.time}")
            appendLine()
            appendLine(entry.content)
        }
    }

    private fun plainText(member: Member, entries: List<DailyEntry>, milestones: List<Milestone>): String = buildString {
        appendLine("${member.name} 的日报纪念册")
        appendLine("=".repeat(28))
        appendLine("记录天数：${entries.map { it.date }.distinct().size} 天")
        appendLine("日报数量：${entries.size} 篇")
        appendLine("专业：${member.major.ifBlank { "未填写" }}")
        appendLine("年级：${member.grade.ifBlank { "未填写" }}")
        appendLine("加入时间：${member.joinedDate.ifBlank { "未填写" }}")
        appendLine("职级：${member.rank.ifBlank { "未设置" }}")
        appendLine("标签：${member.tag.ifBlank { "未设置" }}")
        appendLine()
        appendLine("个人大事记")
        appendLine("-".repeat(28))
        if (milestones.isEmpty()) appendLine("暂无大事记")
        milestones.forEach { milestone ->
            appendLine(milestone.date)
            appendLine(milestone.content)
            appendLine()
        }
        appendLine()
        appendLine("日报时间轴")
        appendLine("-".repeat(28))
        entries.forEach { entry ->
            appendLine("${entry.date} ${entry.time}")
            appendLine(entry.content)
            appendLine()
        }
    }

    private fun writeDocx(output: OutputStream, member: Member, entries: List<DailyEntry>, milestones: List<Milestone>) {
        ZipOutputStream(output).use { zip ->
            zip.textEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>""".trimIndent()
            )
            zip.textEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""".trimIndent()
            )
            val paragraphs = buildList {
                add(DocParagraph("${member.name} 的日报纪念册", 36, true, true))
                add(DocParagraph("共记录 ${entries.map { it.date }.distinct().size} 天、${entries.size} 篇日报", 22, false, true))
                add(DocParagraph("个人档案", 28, true))
                add(DocParagraph("姓名：${member.name}"))
                add(DocParagraph("专业：${member.major.ifBlank { "未填写" }}"))
                add(DocParagraph("年级：${member.grade.ifBlank { "未填写" }}"))
                add(DocParagraph("加入时间：${member.joinedDate.ifBlank { "未填写" }}"))
                add(DocParagraph("职级：${member.rank.ifBlank { "未设置" }}"))
                add(DocParagraph("标签：${member.tag.ifBlank { "未设置" }}"))
                add(DocParagraph("个人大事记", 28, true))
                if (milestones.isEmpty()) add(DocParagraph("暂无大事记"))
                milestones.forEach {
                    add(DocParagraph(it.date, 24, true))
                    it.content.lines().forEach { line -> add(DocParagraph(line.ifEmpty { " " })) }
                }
                add(DocParagraph("日报时间轴", 28, true))
                entries.forEach {
                    add(DocParagraph("${it.date} ${it.time}", 24, true))
                    it.content.lines().forEach { line -> add(DocParagraph(line.ifEmpty { " " })) }
                }
            }
            val body = paragraphs.joinToString("") { it.xml() }
            zip.textEntry(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body>
                </w:document>""".trimIndent()
            )
        }
    }

    private fun writePdf(output: OutputStream, member: Member, entries: List<DailyEntry>, milestones: List<Milestone>) {
        val pdf = PdfDocument()
        val renderer = PdfRenderer(pdf)
        renderer.title("${member.name} 的日报纪念册")
        renderer.muted("共记录 ${entries.map { it.date }.distinct().size} 天、${entries.size} 篇日报")
        renderer.heading("个人档案")
        renderer.text("姓名：${member.name}")
        renderer.text("专业：${member.major.ifBlank { "未填写" }}")
        renderer.text("年级：${member.grade.ifBlank { "未填写" }}")
        renderer.text("加入时间：${member.joinedDate.ifBlank { "未填写" }}")
        renderer.text("职级：${member.rank.ifBlank { "未设置" }}")
        renderer.text("标签：${member.tag.ifBlank { "未设置" }}")
        renderer.heading("个人大事记")
        if (milestones.isEmpty()) renderer.text("暂无大事记")
        milestones.forEach {
            renderer.subheading(it.date)
            renderer.text(it.content)
            renderer.space(8f)
        }
        renderer.heading("日报时间轴")
        entries.forEach {
            renderer.subheading("${it.date}  ${it.time}")
            renderer.text(it.content)
            renderer.space(8f)
        }
        renderer.finish()
        pdf.writeTo(output)
        pdf.close()
    }
}

private data class DocParagraph(
    val text: String,
    val halfPoints: Int = 22,
    val bold: Boolean = false,
    val centered: Boolean = false,
) {
    fun xml(): String {
        val pPr = if (centered) "<w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"240\"/></w:pPr>" else "<w:pPr><w:spacing w:after=\"160\"/></w:pPr>"
        val boldTag = if (bold) "<w:b/>" else ""
        return "<w:p>$pPr<w:r><w:rPr>$boldTag<w:sz w:val=\"$halfPoints\"/><w:szCs w:val=\"$halfPoints\"/></w:rPr><w:t xml:space=\"preserve\">${text.xmlEscape()}</w:t></w:r></w:p>"
    }
}

private fun String.xmlEscape() = replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

private fun ZipOutputStream.textEntry(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private class PdfRenderer(private val document: PdfDocument) {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 52f
    private val contentWidth = pageWidth - margin * 2
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var y = margin

    fun title(value: String) = draw(value, 24f, true, 1.45f)
    fun heading(value: String) { space(14f); draw(value, 18f, true, 1.45f) }
    fun subheading(value: String) { space(8f); draw(value, 14f, true, 1.45f) }
    fun muted(value: String) = draw(value, 11f, false, 1.55f, 0xff666666.toInt())
    fun text(value: String) = draw(value, 11f, false, 1.65f)
    fun space(points: Float) { ensurePage(); y += points; if (y > pageHeight - margin) newPage() }

    private fun draw(value: String, size: Float, bold: Boolean, spacing: Float, color: Int = 0xff222222.toInt()) {
        ensurePage()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        value.lines().ifEmpty { listOf("") }.forEach { sourceLine ->
            val remaining = sourceLine.ifEmpty { " " }
            wrap(remaining, paint, contentWidth).forEach { line ->
                val lineHeight = size * spacing
                if (y + lineHeight > pageHeight - margin) newPage()
                page!!.canvas.drawText(line, margin, y + size, paint)
                y += lineHeight
            }
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null).coerceAtLeast(1)
            var end = min(text.length, start + count)
            if (end < text.length) {
                val breakAt = text.lastIndexOf(' ', end - 1).takeIf { it >= start + count / 2 }
                if (breakAt != null) end = breakAt + 1
            }
            result += text.substring(start, end).trimEnd()
            start = end
            while (start < text.length && text[start] == ' ') start++
        }
        return result.ifEmpty { listOf(" ") }
    }

    private fun ensurePage() { if (page == null) newPage() }

    private fun newPage() {
        page?.let(document::finishPage)
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        y = margin
    }

    fun finish() { page?.let(document::finishPage); page = null }
}
