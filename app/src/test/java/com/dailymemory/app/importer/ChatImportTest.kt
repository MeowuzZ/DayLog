package com.dailymemory.app.importer

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ChatImportTest {
    private val day = LocalDate.of(2026, 9, 3)
    private val range = ImportRange(day.atStartOfDay(), day.atTime(23, 59, 59))
    @Test fun reportPrefixAcceptsOnlyTheRequestedNumericRanges() {
        listOf("9.3 日报", "09、03\n工作内容", "12/31", "1，1日报", "９．３ 今日完成", "2.31 日报").forEach {
            assertTrue(it, ReportRule.matches(it))
        }
        listOf("13.1 日报", "0.3", "9.0", "9.32", "123.1", "1.311", " 9.3", "\n9.3", "日报9.3", "-9.3", "1..3", "9月3", "9 3", "９.３２").forEach {
            assertFalse(it, ReportRule.matches(it))
        }
    }
    @Test fun timeRangeIncludesBothBoundaries() {
        assertTrue(range.contains(range.start)); assertTrue(range.contains(range.end))
        assertFalse(range.contains(range.start.minusSeconds(1))); assertFalse(range.contains(range.end.plusSeconds(1)))
    }
    @Test(expected = IllegalArgumentException::class) fun reverseRangeIsRejected() {
        ImportRange(range.end, range.start)
    }
    @Test fun displayedTimesUseRealDatesAndHandlePeriods() {
        assertEquals(day.atTime(18, 30), ChatTimeParser.parse("2026年9月3日 下午6:30", day, range))
        assertEquals(day.atTime(9, 7), ChatTimeParser.parse("9月3日 09:07", day, range))
        assertEquals(day.minusDays(1).atTime(21, 5), ChatTimeParser.parse("昨天 晚上9:05", day, range))
        assertEquals(day.atTime(12, 1), ChatTimeParser.parse("中午12:01", day, range))
        assertNull(ChatTimeParser.parse("2026年2月31日 12:00", day, range))
        assertNull(ChatTimeParser.parse("25:80", day, range))
        assertNull(ChatTimeParser.parse("not a timestamp", day, range))
    }
    @Test fun yearlessAmbiguityIsNotSilentlyResolved() {
        val years = ImportRange(LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59))
        assertNull(ChatTimeParser.parse("9月3日 09:07", day, years))
    }
    @Test fun sameYearMonthDayOutsideRangeCanStillBeIdentified() {
        assertEquals(day.minusDays(1).atTime(18, 30), ChatTimeParser.parse("9月2日 18:30", day, range))
    }
    @Test fun clockOnlyLabelsInheritTheLastResolvedHistoricalDate() {
        val historicalDay = day.minusDays(1)
        val historicalRange = ImportRange(historicalDay.atStartOfDay(), historicalDay.atTime(23, 59, 59))
        val parsed = ChatPageParser.parse(
            "团队群",
            listOf(
                ChatRow(listOf(ChatText("9月2日 18:30"))),
                ChatRow(listOf(ChatText("", "小明的头像"), ChatText("9.2 第一条日报"))),
                ChatRow(listOf(ChatText("18:45"))),
                ChatRow(listOf(ChatText("", "小红的头像"), ChatText("9.2 第二条日报"))),
            ),
            day,
            historicalRange,
        )
        assertEquals(2, parsed.candidates.size)
        assertEquals(historicalDay.atTime(18, 30), parsed.candidates[0].referenceTime)
        assertEquals(historicalDay.atTime(18, 45), parsed.candidates[1].referenceTime)
    }
    @Test fun relativeLabelsIgnoreTheInheritedHistoricalDate() {
        val wideRange = ImportRange(day.minusDays(10).atStartOfDay(), day.atTime(23, 59, 59))
        val parsed = ChatPageParser.parse(
            "团队群",
            listOf(
                ChatRow(listOf(ChatText("2026年8月25日 18:30"))),
                ChatRow(listOf(ChatText("昨天 19:00"))),
                ChatRow(listOf(ChatText("20:15"))),
                ChatRow(listOf(ChatText("", "小明的头像"), ChatText("9.2 相对日期日报"))),
            ),
            day,
            wideRange,
        )
        assertEquals(day.minusDays(1).atTime(20, 15), parsed.candidates.single().referenceTime)
    }
    @Test fun screenParserStillDropsCandidatesFromClearlyDifferentDates() {
        val parsed = ChatPageParser.parse(
            "团队群",
            listOf(
                ChatRow(listOf(ChatText("9月2日 18:30"))),
                ChatRow(listOf(ChatText("", "小明的头像"), ChatText("9.2 范围外日报"))),
            ),
            day,
            range,
        )
        assertTrue(parsed.candidates.isEmpty())
        assertEquals(day.minusDays(1).atTime(18, 30), parsed.latestObservedTime)
    }
    @Test fun sparseTimeLabelBeforeStartDoesNotDropSameDayCandidate() {
        val narrow = ImportRange(day.atTime(18, 32), day.atTime(19, 0))
        val parsed = ChatPageParser.parse(
            "团队群",
            listOf(
                ChatRow(listOf(ChatText("2026年9月3日 18:30"))),
                ChatRow(listOf(ChatText("", "小明的头像"), ChatText("9.3 实际于18:33发送"))),
            ),
            day,
            narrow,
        )
        assertEquals(1, parsed.candidates.size)
        assertEquals(day.atTime(18, 30), parsed.candidates.single().referenceTime)
        assertEquals(day.atTime(18, 30), parsed.latestObservedTime)
        assertTrue(parsed.latestObservedTime!!.isBefore(narrow.start))
    }
    @Test fun screenParserKeepsSenderAndBodyTogetherAndDoesNotGuessMissingTime() {
        val rows = listOf(
            ChatRow(listOf(ChatText("", "小明的头像"), ChatText("9.3 第一条日报"))),
            ChatRow(listOf(ChatText("2026年9月3日 18:30", top = 100), ChatText("昵称乙", top = 130), ChatText("9.3 第二条\n完成任务", top = 160))),
            ChatRow(listOf(ChatText("昵称丙", top = 200), ChatText("晚上一起吃饭", top = 230))),
            ChatRow(listOf(ChatText("2026年9月2日 18:30", top = 300), ChatText("9.2 超出范围", top = 330))),
        )
        val result = ChatPageParser.parse("团队群", rows, day, range).candidates
        assertEquals(2, result.size)
        assertEquals("小明", result[0].nickname); assertNull(result[0].referenceTime)
        assertEquals("昵称乙", result[1].nickname); assertEquals(day.atTime(18, 30), result[1].referenceTime)
        assertEquals("9.3 第二条\n完成任务", result[1].content)
    }
    @Test fun unknownSenderDoesNotInheritAnotherRowSender() {
        val rows = listOf(ChatRow(listOf(ChatText("张三"), ChatText("9.3 甲"))), ChatRow(listOf(ChatText("9.3 乙"))))
        assertEquals("", ChatPageParser.parse("群", rows, day, range).candidates[1].nickname)
    }
    @Test fun textImportFiltersMessagesWithoutDroppingMultilineBodies() {
        val text = "[2026-09-03 00:00:00] 小明\n9.3 第一行\n第二行\n\n[2026-09-03 12:05] 小红\n不是日报\n[2026-09-03 23:59:59] 小红\n09、03 日报\n[2026-09-04 00:00:00] 小王\n9.4 超范围"
        val result = ChatTextImport.parse("日报群", text, range)
        assertEquals(2, result.size)
        assertEquals("9.3 第一行\n第二行", result[0].content)
        assertEquals("小红", result[1].nickname)
        assertEquals(range.end, result[1].referenceTime)
    }
    @Test fun textImportDoesNotNormalizeLeadingWhitespaceOrInvalidDates() {
        assertTrue(ChatTextImport.parse("群", "[2026-09-03 18:30] 甲\n 9.3 内容", range).isEmpty())
        assertTrue(ChatTextImport.parse("群", "[2026-02-31 18:30] 甲\n2.31 内容", range).isEmpty())
    }
    @Test fun queueDeduplicatesPagesAndClearsAllPendingContent() {
        val candidate = ChatCandidate("群", "昵称", "9.3 内容", "18:30", day.atTime(18, 30))
        val queue = CandidateQueue(); queue.add(listOf(candidate, candidate))
        assertEquals(1, queue.pending.size)
        queue.remove(candidate.key); assertTrue(queue.pending.isEmpty())
        queue.add(listOf(candidate)); assertTrue(queue.pending.isEmpty())
        queue.clear(); queue.add(listOf(candidate)); assertEquals(1, queue.pending.size)
        queue.clear(); assertTrue(queue.pending.isEmpty())
    }
    @Test fun fingerprintSeparatesGroupsAndUnambiguousParts() {
        assertNotEquals(fingerprint("ab", "c"), fingerprint("a", "bc"))
        assertNotEquals(fingerprint("群一", "小明"), fingerprint("群二", "小明"))
    }
    @Test fun overlappingPartialRowsMergeWithoutCollapsingDifferentKnownTimes() {
        val full = ChatCandidate("群", "昵称", "9.3 内容", "18:30", day.atTime(18, 30))
        val queue = CandidateQueue()
        queue.add(listOf(full.copy(nickname = "", displayedTime = "", referenceTime = null)))
        queue.add(listOf(full)); assertEquals(listOf(full), queue.pending)
        queue.add(listOf(full.copy(referenceTime = day.atTime(19, 30))))
        assertEquals(2, queue.pending.size)
    }
    @Test fun numericNicknameIsNotAMessageAndClockLikeBodyStillMatchesRule() {
        val rows = listOf(
            ChatRow(listOf(ChatText("9:30"))),
            ChatRow(listOf(ChatText("9.3", description = "9.3的头像"), ChatText("普通聊天"))),
            ChatRow(listOf(ChatText("昵称", description = "昵称的头像"), ChatText("9:30"))),
        )
        val result = ChatPageParser.parse("群", rows, day, range).candidates
        assertEquals(1, result.size); assertEquals("昵称", result[0].nickname)
    }
}
