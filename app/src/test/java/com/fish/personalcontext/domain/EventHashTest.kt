package com.fish.personalcontext.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EventHashTest {

    @Test
    fun `usage 事件哈希对相同输入稳定`() {
        val first = EventHash.forUsageEvent(EventType.APP_OPEN, 1_790_000_000_000, "com.tencent.mm")
        val second = EventHash.forUsageEvent(EventType.APP_OPEN, 1_790_000_000_000, "com.tencent.mm")
        assertEquals(first, second)
    }

    @Test
    fun `usage 事件哈希对时间戳与包名敏感`() {
        val base = EventHash.forUsageEvent(EventType.APP_OPEN, 1_000, "com.a")
        assertNotEquals(base, EventHash.forUsageEvent(EventType.APP_OPEN, 1_001, "com.a"))
        assertNotEquals(base, EventHash.forUsageEvent(EventType.APP_OPEN, 1_000, "com.b"))
        assertNotEquals(base, EventHash.forUsageEvent(EventType.APP_CLOSE, 1_000, "com.a"))
    }

    @Test
    fun `通知哈希基于 notificationKey 稳定且区分生命周期`() {
        val key = "0|com.tencent.mm|12345|null"
        assertEquals(
            EventHash.forNotification(EventType.NOTIFICATION_POSTED, key),
            EventHash.forNotification(EventType.NOTIFICATION_POSTED, key),
        )
        assertNotEquals(
            EventHash.forNotification(EventType.NOTIFICATION_POSTED, key),
            EventHash.forNotification(EventType.NOTIFICATION_REMOVED, key),
        )
    }
}
