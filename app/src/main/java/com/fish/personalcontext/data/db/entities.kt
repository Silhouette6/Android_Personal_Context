package com.fish.personalcontext.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fish.personalcontext.domain.EventType
import com.fish.personalcontext.domain.TimelineEvent

@Entity(
    tableName = "timeline_events",
    indices = [
        Index("timestamp"),
        Index("packageName", "timestamp"),
        Index("type", "timestamp"),
        Index(value = ["eventHash"], unique = true),
    ],
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventHash: String,
    val timestamp: Long,
    val type: String,
    val packageName: String?,
    val appName: String?,
    val title: String?,
    val text: String?,
    val notificationKey: String?,
    val metadataJson: String?,
    val createdAt: Long,
) {
    fun toDomain(): TimelineEvent = TimelineEvent(
        id = id,
        timestamp = timestamp,
        type = EventType.fromStorage(type),
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        notificationKey = notificationKey,
        metadataJson = metadataJson,
    )
}

/** 采集游标与运行状态的 key-value 存储（与事件同库，事务一致） */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** 通知按 App 聚合计数（Statistics 用） */
data class NotificationCountRow(
    val packageName: String?,
    val appName: String?,
    val count: Int,
)
