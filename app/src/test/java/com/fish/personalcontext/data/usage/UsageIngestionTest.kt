package com.fish.personalcontext.data.usage

import android.app.usage.UsageEvents
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fish.personalcontext.TestApp
import com.fish.personalcontext.data.appinfo.AppInfoCache
import com.fish.personalcontext.data.db.AppDatabase
import com.fish.personalcontext.data.db.SyncKeys
import com.fish.personalcontext.data.db.SyncStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UsageStats 增量采集场景（spec §6/§19/§20）：
 *  - 采集时间 ≠ 事件时间：事件按系统原始时间戳入库
 *  - safety window 重扫幂等：重叠窗口不产生重复行
 *  - 游标只进不退；首次运行不回溯历史
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class UsageIngestionTest {

    private lateinit var db: AppDatabase
    private lateinit var collector: UsageStatsCollector

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        collector = UsageStatsCollector(context, db, AppInfoCache(context.packageManager))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun resume(pkg: String, ts: Long) =
        UsageStatsCollector.RawUsageEvent(UsageEvents.Event.ACTIVITY_RESUMED, ts, pkg)

    private fun pause(pkg: String, ts: Long) =
        UsageStatsCollector.RawUsageEvent(UsageEvents.Event.ACTIVITY_PAUSED, ts, pkg)

    private suspend fun setCursor(ts: Long) {
        db.syncStateDao().put(SyncStateEntity(SyncKeys.LAST_USAGE_EVENT_TS, ts.toString()))
    }

    private suspend fun cursor(): Long =
        db.syncStateDao().get(SyncKeys.LAST_USAGE_EVENT_TS)!!.toLong()

    @Test
    fun `首次运行只初始化游标不回溯历史`() = runBlocking {
        val result = collector.syncOnce { _, _ -> throw AssertionError("首次运行不应查询") }
        assertTrue(result is UsageStatsCollector.Result.FirstRun)
        assertTrue(cursor() > 0)
        assertEquals(0, db.timelineEventDao().allEvents().size)
    }

    @Test
    fun `事件按原始时间戳入库而非采集时间`() = runBlocking {
        setCursor(1_000_000)
        val result = collector.syncOnce { _, _ ->
            listOf(
                resume("com.tencent.mm", 1_010_000),
                pause("com.tencent.mm", 1_020_000),
                resume("com.android.chrome", 1_030_000),
            )
        }
        assertEquals(UsageStatsCollector.Result.Synced(3), result)

        val rows = db.timelineEventDao().allEvents()
        assertEquals(listOf(1_010_000L, 1_020_000L, 1_030_000L), rows.map { it.timestamp })
        assertEquals(
            listOf("APP_OPEN", "APP_CLOSE", "APP_OPEN"),
            rows.map { it.type },
        )
        // 采集时间（createdAt）远大于事件时间，二者不得混淆
        assertTrue(rows.all { it.createdAt > it.timestamp })
    }

    @Test
    fun `safety window 重扫与迟到事件不产生重复`() = runBlocking {
        val c = 1_000_000L
        setCursor(c)
        // X 是迟到的旧事件（落在游标前 30s，但仍在 60s 安全窗口内），Y 是新事件
        val events = listOf(resume("com.a", c - 30_000), resume("com.b", c + 30_000))

        collector.syncOnce { _, _ -> events }
        assertEquals(2, db.timelineEventDao().allEvents().size)

        // 第二次运行：窗口从新游标回退 60s，X 会被再次读到，但必须被去重
        var capturedStart = Long.MIN_VALUE
        collector.syncOnce { start, _ ->
            capturedStart = start
            events
        }
        assertEquals(c + 30_000 - UsageStatsCollector.SAFETY_WINDOW_MS, capturedStart)
        assertEquals(2, db.timelineEventDao().allEvents().size)
    }

    @Test
    fun `Doze 延迟后一次性补齐累积事件`() = runBlocking {
        setCursor(1_000_000)
        val result = collector.syncOnce { _, _ ->
            listOf(
                resume("com.tencent.mm", 1_062_000),
                pause("com.tencent.mm", 1_100_000),
                resume("com.android.chrome", 1_460_000),
            )
        }
        assertEquals(UsageStatsCollector.Result.Synced(3), result)
        // 游标推进到最大事件时间
        assertEquals(1_460_000L, cursor())
    }

    @Test
    fun `非 Activity 事件被忽略`() = runBlocking {
        setCursor(1_000_000)
        val result = collector.syncOnce { _, _ ->
            listOf(
                UsageStatsCollector.RawUsageEvent(999, 1_010_000, "com.a"), // 未知类型
                UsageStatsCollector.RawUsageEvent(UsageEvents.Event.ACTIVITY_RESUMED, 1_020_000, null), // 无包名
                resume("com.b", 1_030_000),
            )
        }
        assertEquals(UsageStatsCollector.Result.Synced(1), result)
        assertEquals(1, db.timelineEventDao().allEvents().size)
    }

    @Test
    fun `STOPPED 同样映射为 APP_CLOSE`() = runBlocking {
        setCursor(1_000_000)
        collector.syncOnce { _, _ ->
            listOf(UsageStatsCollector.RawUsageEvent(UsageEvents.Event.ACTIVITY_STOPPED, 1_010_000, "com.a"))
        }
        assertEquals(listOf("APP_CLOSE"), db.timelineEventDao().allEvents().map { it.type })
    }
}
