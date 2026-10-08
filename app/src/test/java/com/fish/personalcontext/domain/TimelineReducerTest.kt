package com.fish.personalcontext.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineReducerTest {

    private fun open(pkg: String, ts: Long) =
        TimelineEvent(timestamp = ts, type = EventType.APP_OPEN, packageName = pkg, appName = pkg)

    private fun close(pkg: String, ts: Long) =
        TimelineEvent(timestamp = ts, type = EventType.APP_CLOSE, packageName = pkg, appName = pkg)

    @Test
    fun `同包突发 RESUMED 合并为一个会话`() {
        val sessions = TimelineReducer.reduce(
            listOf(open("a", 1_000), open("a", 2_000), open("a", 4_000))
        )
        assertEquals(1, sessions.size)
        assertEquals(1_000L, sessions[0].startTime)
        assertNull(sessions[0].endTime)
    }

    @Test
    fun `页内切换的 PAUSED 被 RESUMED 抵消`() {
        val sessions = TimelineReducer.reduce(
            listOf(open("a", 1_000), close("a", 10_000), open("a", 12_000))
        )
        assertEquals(1, sessions.size)
        assertEquals(1_000L, sessions[0].startTime)
        assertNull(sessions[0].endTime) // 会话继续
    }

    @Test
    fun `页内切换超过突发窗口则确认关闭`() {
        val sessions = TimelineReducer.reduce(
            listOf(open("a", 1_000), close("a", 10_000), open("a", 200_000))
        )
        // close 后 190s 才重新 RESUMED：会话在 10_000 结束，12 秒后新会话开始
        assertEquals(2, sessions.size)
        assertEquals(10_000L, sessions[0].endTime)
        assertEquals(200_000L, sessions[1].startTime)
    }

    @Test
    fun `切换 App 生成上一会话的结束`() {
        val sessions = TimelineReducer.reduce(
            listOf(open("a", 1_000), close("a", 10_000), open("b", 10_500), close("b", 20_000))
        )
        assertEquals(2, sessions.size)
        assertEquals("a", sessions[0].packageName)
        assertEquals(10_000L, sessions[0].endTime)
        assertEquals("b", sessions[1].packageName)
        assertEquals(20_000L, sessions[1].endTime)
    }

    @Test
    fun `漏收 PAUSED 的切换按新 OPEN 时刻近似结束`() {
        val sessions = TimelineReducer.reduce(listOf(open("a", 1_000), open("b", 60_000)))
        assertEquals(2, sessions.size)
        assertEquals(60_000L, sessions[0].endTime)
    }

    @Test
    fun `长间隔同包重开切成两个会话`() {
        val sessions = TimelineReducer.reduce(listOf(open("a", 1_000), open("a", 120_000)))
        assertEquals(2, sessions.size)
        assertEquals(1_000L, sessions[0].startTime)
        assertEquals(120_000L, sessions[0].endTime)
    }

    @Test
    fun `尾部未关闭会话 endTime 为空`() {
        val sessions = TimelineReducer.reduce(listOf(open("a", 1_000), open("b", 2_000)))
        assertEquals(2, sessions.size)
        assertNull(sessions[1].endTime)
    }

    @Test
    fun `PAUSED 后 STOPPED 只记一次关闭`() {
        val sessions = TimelineReducer.reduce(
            listOf(open("a", 1_000), close("a", 5_000), close("a", 6_000))
        )
        assertEquals(1, sessions.size)
        assertEquals(5_000L, sessions[0].endTime)
    }

    @Test
    fun `迟到 close（非当前前台包）被忽略`() {
        val sessions = TimelineReducer.reduce(
            listOf(open("a", 1_000), close("b", 2_000), close("a", 9_000))
        )
        assertEquals(1, sessions.size)
        assertEquals(9_000L, sessions[0].endTime)
    }

    @Test
    fun `重叠重扫的重复事件不改变折叠结果`() {
        val base = listOf(open("a", 1_000), close("a", 10_000), open("b", 11_000))
        assertEquals(TimelineReducer.reduce(base), TimelineReducer.reduce(base + base))
    }

    @Test
    fun `乱序输入按时间排序后折叠`() {
        val sorted = listOf(open("a", 1_000), close("a", 10_000), open("b", 11_000))
        val shuffled = sorted.reversed()
        assertEquals(TimelineReducer.reduce(sorted), TimelineReducer.reduce(shuffled))
    }

    @Test
    fun `空输入返回空会话`() {
        assertEquals(0, TimelineReducer.reduce(emptyList()).size)
    }

    @Test
    fun `孤立 CLOSE 不产生会话`() {
        assertEquals(0, TimelineReducer.reduce(listOf(close("a", 5_000))).size)
    }
}
