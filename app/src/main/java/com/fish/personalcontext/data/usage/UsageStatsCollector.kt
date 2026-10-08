package com.fish.personalcontext.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.fish.personalcontext.data.appinfo.AppInfoCache
import com.fish.personalcontext.data.db.AppDatabase
import com.fish.personalcontext.data.db.SyncKeys
import com.fish.personalcontext.data.db.SyncStateEntity
import com.fish.personalcontext.data.db.TimelineEventEntity
import com.fish.personalcontext.domain.EventHash
import com.fish.personalcontext.domain.EventType

/**
 * 增量拉取系统 usage 日志：
 * queryEvents(游标 - safetyWindow, now) → 一次性遍历 → 批量幂等插入 → 游标前移。
 * 采集时间与事件时间解耦：即使 Worker 延迟执行，事件仍按系统记录的原始时间戳落库。
 */
class UsageStatsCollector(
    context: Context,
    private val db: AppDatabase,
    private val appInfoCache: AppInfoCache,
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    sealed interface Result {
        data object FirstRun : Result
        data object NoPermission : Result
        data class Synced(val scanned: Int) : Result
    }

    fun interface EventQuery {
        fun query(startMs: Long, endMs: Long): List<RawUsageEvent>
    }

    data class RawUsageEvent(val eventType: Int, val timestamp: Long, val packageName: String?)

    private val systemQuery = EventQuery { startMs, endMs -> readSystemEvents(startMs, endMs) }

    suspend fun syncOnce(query: EventQuery = systemQuery): Result {
        val now = System.currentTimeMillis()
        val eventDao = db.timelineEventDao()
        val stateDao = db.syncStateDao()

        // 首次运行：游标从现在开始，不回溯系统历史
        val cursor = stateDao.get(SyncKeys.LAST_USAGE_EVENT_TS)?.toLongOrNull() ?: run {
            stateDao.put(SyncStateEntity(SyncKeys.LAST_USAGE_EVENT_TS, now.toString()))
            stateDao.put(SyncStateEntity(SyncKeys.LAST_USAGE_SYNC_AT, now.toString()))
            return Result.FirstRun
        }

        // safety window：重叠窗口内的重复事件由 event_hash 唯一索引吞掉
        val queryStart = cursor - SAFETY_WINDOW_MS
        val rawEvents = try {
            query.query(queryStart, now)
        } catch (_: SecurityException) {
            return Result.NoPermission
        }

        var maxTimestamp = cursor
        val batch = ArrayList<TimelineEventEntity>(rawEvents.size.coerceAtLeast(16))
        for (event in rawEvents) {
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> EventType.APP_OPEN
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> EventType.APP_CLOSE
                else -> null
            } ?: continue
            val pkg = event.packageName ?: continue
            batch += TimelineEventEntity(
                eventHash = EventHash.forUsageEvent(type, event.timestamp, pkg),
                timestamp = event.timestamp, // 事件原始时间，非采集时间
                type = type.storageKey,
                packageName = pkg,
                appName = appInfoCache.appName(pkg),
                title = null,
                text = null,
                notificationKey = null,
                metadataJson = null,
                createdAt = now,
            )
            maxTimestamp = maxOf(maxTimestamp, event.timestamp)
        }

        if (batch.isNotEmpty()) eventDao.insertAllIgnoring(batch)
        stateDao.put(SyncStateEntity(SyncKeys.LAST_USAGE_EVENT_TS, maxTimestamp.toString()))
        stateDao.put(SyncStateEntity(SyncKeys.LAST_USAGE_SYNC_AT, now.toString()))
        Log.d(TAG, "usage sync: scanned=${batch.size} windowSec=${(now - queryStart) / 1000}")
        return Result.Synced(batch.size)
    }

    private fun readSystemEvents(startMs: Long, endMs: Long): List<RawUsageEvent> {
        val usageEvents = usageStatsManager.queryEvents(startMs, endMs)
        val out = ArrayList<RawUsageEvent>(64)
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            out += RawUsageEvent(event.eventType, event.timeStamp, event.packageName)
        }
        return out
    }

    companion object {
        private const val TAG = "UsageStatsCollector"

        /** 重扫重叠窗口：覆盖边界事件与乱序写入 */
        const val SAFETY_WINDOW_MS = 60_000L
    }
}
