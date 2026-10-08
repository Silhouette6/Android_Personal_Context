package com.fish.personalcontext.domain

/**
 * 统一事件模型：所有数据源先归一化为 TimelineEvent 再落库。
 * timestamp 永远是事件发生的原始时间，不是采集时间。
 */
data class TimelineEvent(
    val id: Long = 0,
    val timestamp: Long,
    val type: EventType,
    val packageName: String? = null,
    val appName: String? = null,
    val title: String? = null,
    val text: String? = null,
    val notificationKey: String? = null,
    val metadataJson: String? = null,
)
