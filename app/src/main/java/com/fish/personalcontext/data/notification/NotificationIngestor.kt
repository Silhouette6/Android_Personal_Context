package com.fish.personalcontext.data.notification

import android.app.Notification
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import androidx.room.withTransaction
import com.fish.personalcontext.data.appinfo.AppInfoCache
import com.fish.personalcontext.data.db.AppDatabase
import com.fish.personalcontext.data.db.SyncKeys
import com.fish.personalcontext.data.db.SyncStateEntity
import com.fish.personalcontext.data.db.TimelineEventEntity
import com.fish.personalcontext.domain.EventHash
import com.fish.personalcontext.domain.EventType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NotificationMeta(
    val channelId: String? = null,
    val category: String? = null,
    val subText: String? = null,
    val isOngoing: Boolean = false,
    val importance: Int? = null,
    val postTimeMs: Long = 0,
    val lastUpdateTimeMs: Long = 0,
)

/**
 * 通知事件落库：
 *  - POSTED 以 notificationKey 为锚点 upsert——首次 post 建事件（保留 postTime），
 *    后续 update 只刷新 title/text/lastUpdateTime，不产生新的时间轴事件
 *  - REMOVED 幂等插入（removeTime 即事件时间）
 */
class NotificationIngestor(
    private val db: AppDatabase,
    private val appInfoCache: AppInfoCache,
    private val json: Json,
) {

    suspend fun onPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        // 优先 EXTRA_TEXT（最新一条），无则退回 EXTRA_BIG_TEXT（展开全文）
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val now = System.currentTimeMillis()
        val meta = NotificationMeta(
            channelId = n.channelId,
            category = n.category,
            subText = subText,
            isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            importance = readImportance(sbn.key, rankingMap),
            postTimeMs = sbn.postTime,
            lastUpdateTimeMs = now,
        )
        val metaJson = json.encodeToString(meta)
        val hash = EventHash.forNotification(EventType.NOTIFICATION_POSTED, sbn.key)

        db.withTransaction {
            val inserted = db.timelineEventDao().insertIgnoring(
                TimelineEventEntity(
                    eventHash = hash,
                    timestamp = sbn.postTime,
                    type = EventType.NOTIFICATION_POSTED.storageKey,
                    packageName = sbn.packageName,
                    appName = appInfoCache.appName(sbn.packageName),
                    title = title,
                    text = text,
                    notificationKey = sbn.key,
                    metadataJson = metaJson,
                    createdAt = now,
                )
            )
            if (inserted == -1L) {
                db.timelineEventDao().refreshNotification(hash, title, text, metaJson)
            }
        }
    }

    suspend fun onRemoved(sbn: StatusBarNotification) {
        val now = System.currentTimeMillis()
        val meta = NotificationMeta(
            channelId = sbn.notification?.channelId,
            postTimeMs = sbn.postTime,
            lastUpdateTimeMs = now,
        )
        db.timelineEventDao().insertIgnoring(
            TimelineEventEntity(
                eventHash = EventHash.forNotification(EventType.NOTIFICATION_REMOVED, sbn.key),
                timestamp = now,
                type = EventType.NOTIFICATION_REMOVED.storageKey,
                packageName = sbn.packageName,
                appName = appInfoCache.appName(sbn.packageName),
                title = null,
                text = null,
                notificationKey = sbn.key,
                metadataJson = json.encodeToString(meta),
                createdAt = now,
            )
        )
    }

    suspend fun onListenerConnected() {
        db.syncStateDao().put(SyncStateEntity(SyncKeys.LISTENER_CONNECTED_AT, nowString()))
    }

    suspend fun onListenerDisconnected() {
        db.syncStateDao().put(SyncStateEntity(SyncKeys.LISTENER_DISCONNECTED_AT, nowString()))
    }

    private fun readImportance(key: String, rankingMap: RankingMap?): Int? {
        if (rankingMap == null) return null
        return try {
            val ranking = Ranking()
            if (rankingMap.getRanking(key, ranking)) ranking.importance else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun nowString() = System.currentTimeMillis().toString()
}
