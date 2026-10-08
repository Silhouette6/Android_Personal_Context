package com.fish.personalcontext.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineEventDao {

    /** 批量幂等插入：event_hash 冲突时忽略（usage 重扫/重叠窗口） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoring(events: List<TimelineEventEntity>)

    /** 返回 rowId；IGNORE 命中冲突时返回 -1 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(event: TimelineEventEntity): Long

    /** 通知更新：只刷新内容与 metadata（含 lastUpdateTime），保留首次 post 时间 */
    @Query(
        "UPDATE timeline_events SET title = :title, text = :text, metadataJson = :metadataJson " +
            "WHERE eventHash = :eventHash"
    )
    suspend fun refreshNotification(
        eventHash: String,
        title: String?,
        text: String?,
        metadataJson: String,
    )

    @Query("SELECT * FROM timeline_events WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<TimelineEventEntity>>

    @Query("SELECT COUNT(*) FROM timeline_events WHERE timestamp >= :start AND timestamp < :end")
    fun observeCountBetween(start: Long, end: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM timeline_events")
    fun observeTotalCount(): Flow<Long>

    @Query(
        "SELECT packageName AS packageName, appName AS appName, COUNT(*) AS count " +
            "FROM timeline_events " +
            "WHERE type = 'NOTIFICATION_POSTED' AND timestamp >= :start AND timestamp < :end " +
            "GROUP BY packageName ORDER BY count DESC"
    )
    fun observeNotificationCounts(start: Long, end: Long): Flow<List<NotificationCountRow>>

    @Query("SELECT * FROM timeline_events ORDER BY timestamp ASC")
    suspend fun allEvents(): List<TimelineEventEntity>

    @Query("DELETE FROM timeline_events")
    suspend fun deleteAll()

    @Query("DELETE FROM timeline_events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
