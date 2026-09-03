package com.dailymemory.app.importer

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object ChatTextImport {
    private val header = Regex("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(?::\\d{2})?)\\][ \\t]*(.+)$", RegexOption.MULTILINE)
    fun parse(group: String, text: String, range: ImportRange): List<ChatCandidate> {
        val normalized = text.replace("\r\n", "\n")
        val matches = header.findAll(normalized).toList()
        return matches.mapIndexedNotNull { index, match ->
            val stamp = match.groupValues[1]
            val pattern = if (stamp.length == 16) "uuuu-MM-dd HH:mm" else "uuuu-MM-dd HH:mm:ss"
            val time = runCatching { LocalDateTime.parse(stamp, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)) }.getOrNull() ?: return@mapIndexedNotNull null
            val contentStart = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
            val body = normalized.substring(contentStart, end).removePrefix("\n").trimEnd('\n')
            val nick = match.groupValues[2].trim()
            if (nick.isEmpty() || !ReportRule.matches(body) || !range.contains(time)) null
            else ChatCandidate(group, nick, body, stamp, time)
        }.distinctBy { it.key }
    }
}
