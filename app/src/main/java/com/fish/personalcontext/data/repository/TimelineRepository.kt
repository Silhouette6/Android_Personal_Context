package com.fish.personalcontext.data.repository

import androidx.room.withTransaction
import com.fish.personalcontext.data.appinfo.AppInfoCache
import com.fish.personalcontext.data.db.AppDatabase
import com.fish.personalcontext.data.db.NotificationCountRow
import com.fish.personalcontext.data.db.SyncKeys
import com.fish.personalcontext.data.db.SyncStateEntity
import com.fish.personalcontext.data.db.TimelineEventEntity
import com.fish.personalcontext.domain.EventType
import com.fish.personalcontext.domain.TimelineEvent
import com.fish.personalcontext.domain.TimelineItem
import com.fish.personalcontext.domain.TimelineReducer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * 时间轴仓库：UI 与「未来 Agent」共用的唯一接入面。
 * MVP 只暴露 Repository API，后续可在此之上加 MCP / REST，而无需改动采集与存储。
 */
class TimelineRepository(
    private val db: AppDatabase,
    private val appInfoCache: AppInfoCache,
) {
    private val eventDao = db.timelineEventDao()
    private val stateDao = db.syncStateDao()

    /** 原始事件流（含全部类型，未去噪） */
    fun getTimeline(start: Long, end: Long): Flow<List<TimelineEvent>> =
        eventDao.observeBetween(start, end).map { rows -> rows.map { it.toDomain() } }

    /** 每日展示时间轴：App 事件去噪折叠 + 通知（默认只展示 POSTED） */
    fun getDailyTimeline(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<TimelineItem>> {
        val (start, end) = dayBounds(date, zone)
        return eventDao.observeBetween(start, end).map { rows ->
            buildTimeline(rows.map { it.toDomain() })
        }
    }

    data class AppDuration(val packageName: String, val appName: String, val millis: Long)

    data class DailyStats(
        val totalEvents: Int,
        val appDurations: List<AppDuration>,
        val notificationCounts: List<NotificationCountRow>,
    )

    fun getDailyStats(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<DailyStats> {
        val (start, end) = dayBounds(date, zone)
        return combine(
            eventDao.observeCountBetween(start, end),
            eventDao.observeBetween(start, end),
            eventDao.observeNotificationCounts(start, end),
        ) { total, rows, notificationCounts ->
            val sessions = TimelineReducer.reduce(
                rows.map { it.toDomain() }.filterNot { appInfoCache.isDisplayNoise(it.packageName) }
            )
            // 仍在前台的开放会话只统计到当前时刻，不把"使用中"夸大到日末
            val effectiveEnd = minOf(end, System.currentTimeMillis())
            val durations = sessions
                .groupBy { it.packageName }
                .map { (pkg, list) ->
                    AppDuration(
                        packageName = pkg,
                        appName = list.first().appName ?: pkg,
                        millis = list.sumOf { (it.endTime ?: effectiveEnd) - it.startTime }.coerceAtLeast(0),
                    )
                }
                .sortedByDescending { it.millis }
            DailyStats(
                totalEvents = total,
                appDurations = durations,
                notificationCounts = notificationCounts.filterNot { appInfoCache.isDisplayNoise(it.packageName) },
            )
        }
    }

    private fun buildTimeline(events: List<TimelineEvent>): List<TimelineItem> {
        val (appEvents, notificationEvents) = events.partition {
            it.type == EventType.APP_OPEN || it.type == EventType.APP_CLOSE
        }
        val sessions = TimelineReducer.reduce(
            appEvents.filterNot { appInfoCache.isDisplayNoise(it.packageName) }
        )
        val items = mutableListOf<TimelineItem>()
        for (session in sessions) {
            items += TimelineItem(
                timestamp = session.startTime,
                kind = TimelineItem.Kind.APP_OPEN,
                packageName = session.packageName,
                appName = session.appName,
                title = null,
                text = null,
            )
            session.endTime?.let { end ->
                items += TimelineItem(
                    timestamp = end,
                    kind = TimelineItem.Kind.APP_CLOSE,
                    packageName = session.packageName,
                    appName = session.appName,
                    title = null,
                    text = null,
                )
            }
        }
        for (notification in notificationEvents) {
            if (notification.type != EventType.NOTIFICATION_POSTED) continue
            if (appInfoCache.isDisplayNoise(notification.packageName)) continue
            items += TimelineItem(
                timestamp = notification.timestamp,
                kind = TimelineItem.Kind.NOTIFICATION,
                packageName = notification.packageName,
                appName = notification.appName,
                title = notification.title,
                text = notification.text,
            )
        }
        return items.sortedBy { it.timestamp }
    }

    fun observeTotalCount(): Flow<Long> = eventDao.observeTotalCount()

    fun observeSyncState(key: String): Flow<String?> = stateDao.observe(key)

    suspend fun setRetentionDays(days: Int) {
        stateDao.put(SyncStateEntity(SyncKeys.RETENTION_DAYS, days.toString()))
    }

    suspend fun allEvents(): List<TimelineEventEntity> = eventDao.allEvents()

    suspend fun deleteAllData() {
        val now = System.currentTimeMillis()
        db.withTransaction {
            eventDao.deleteAll()
            // 游标重置为现在，避免把系统 7 天历史一次性灌回空库
            stateDao.put(SyncStateEntity(SyncKeys.LAST_USAGE_EVENT_TS, now.toString()))
        }
    }

    private fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }
}
