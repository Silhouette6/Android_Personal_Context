package com.fish.personalcontext.domain

import java.security.MessageDigest

/**
 * 事件唯一性指纹，配合 timeline_events.event_hash 唯一索引实现幂等写入：
 * 重叠窗口重扫（safety window）不会产生重复事件。
 */
object EventHash {

    /** usage 事件：类型 + 原始毫秒时间戳 + 包名（UsageEvent 时间戳毫秒级，足以区分） */
    fun forUsageEvent(type: EventType, timestamp: Long, packageName: String): String =
        sha256("${type.storageKey}|$timestamp|$packageName")

    /** 通知：以系统 notificationKey 为生命周期锚点（post/update/remove 共享同一 key） */
    fun forNotification(type: EventType, notificationKey: String): String =
        sha256("${type.storageKey}|$notificationKey")

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
