package com.fish.personalcontext.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fish.personalcontext.TestApp
import com.fish.personalcontext.data.appinfo.AppInfoCache
import com.fish.personalcontext.data.db.AppDatabase
import com.fish.personalcontext.data.db.TimelineEventEntity
import com.fish.personalcontext.domain.EventHash
import com.fish.personalcontext.domain.EventType
import com.fish.personalcontext.domain.TimelineItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * 端到端读侧场景（spec §24 验收流程）：
 * 打开微信 → 收到微信通知 → 打开 Chrome → 打开腾讯会议 → 退出会议，
 * 混入桌面噪音、页内切换、突发 RESUMED 与一条 REMOVED 通知，
 * 断言时间轴输出、统计聚合与展示过滤全部正确。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class RepositoryScenarioTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TimelineRepository

    private val zone: ZoneId = ZoneId.systemDefault()
    private val date: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val appInfoCache = AppInfoCache(context.packageManager)
        appInfoCache.setHomePackagesForTest(setOf("com.android.launcher"))
        repository = TimelineRepository(db, appInfoCache)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun at(hour: Int, minute: Int, second: Int = 0): Long =
        date.atTime(hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    private fun usageEvent(type: EventType, pkg: String, app: String, ts: Long) =
        TimelineEventEntity(
            eventHash = EventHash.forUsageEvent(type, ts, pkg),
            timestamp = ts,
            type = type.storageKey,
            packageName = pkg,
            appName = app,
            title = null,
            text = null,
            notificationKey = null,
            metadataJson = null,
            createdAt = 0L,
        )

    private fun notificationEvent(type: EventType, key: String, pkg: String, app: String, ts: Long, title: String?, text: String?) =
        TimelineEventEntity(
            eventHash = EventHash.forNotification(type, key),
            timestamp = ts,
            type = type.storageKey,
            packageName = pkg,
            appName = app,
            title = title,
            text = text,
            notificationKey = key,
            metadataJson = null,
            createdAt = 0L,
        )

    private fun seedAcceptanceDay(): List<TimelineEventEntity> = listOf(
        // 09:12 打开微信（含一次突发重复 RESUMED）
        usageEvent(EventType.APP_OPEN, "com.tencent.mm", "微信", at(9, 12, 0)),
        usageEvent(EventType.APP_OPEN, "com.tencent.mm", "微信", at(9, 12, 2)),
        // 09:13 微信通知（张三：晚上一起吃饭吗？）
        notificationEvent(
            EventType.NOTIFICATION_POSTED, "0|com.tencent.mm|1|null",
            "com.tencent.mm", "微信", at(9, 13, 0), "张三", "晚上一起吃饭吗？",
        ),
        // 09:17:55 离开微信回桌面（桌面事件是展示噪音）
        usageEvent(EventType.APP_CLOSE, "com.tencent.mm", "微信", at(9, 17, 55)),
        usageEvent(EventType.APP_OPEN, "com.android.launcher", "桌面", at(9, 17, 56)),
        usageEvent(EventType.APP_CLOSE, "com.android.launcher", "桌面", at(9, 17, 58)),
        // 09:18 打开 Chrome（含一次页内切换：PAUSED 后 5s 同包 RESUMED）
        usageEvent(EventType.APP_OPEN, "com.android.chrome", "Chrome", at(9, 18, 0)),
        usageEvent(EventType.APP_CLOSE, "com.android.chrome", "Chrome", at(9, 18, 40)),
        usageEvent(EventType.APP_OPEN, "com.android.chrome", "Chrome", at(9, 18, 45)),
        usageEvent(EventType.APP_CLOSE, "com.android.chrome", "Chrome", at(9, 25, 59)),
        // 09:26 打开腾讯会议，10:31 退出
        usageEvent(EventType.APP_OPEN, "com.tencent.wemeet.app", "腾讯会议", at(9, 26, 0)),
        usageEvent(EventType.APP_CLOSE, "com.tencent.wemeet.app", "腾讯会议", at(10, 31, 0)),
        // 一条 09:20 的 REMOVED：不应出现在展示时间轴
        notificationEvent(
            EventType.NOTIFICATION_REMOVED, "0|com.tencent.mm|1|null",
            "com.tencent.mm", "微信", at(9, 20, 0), null, null,
        ),
    )

    @Test
    fun `验收场景生成预期时间轴`() = runBlocking {
        db.timelineEventDao().insertAllIgnoring(seedAcceptanceDay())

        val items = repository.getDailyTimeline(date).first()

        val expected = listOf(
            Triple(at(9, 12, 0), TimelineItem.Kind.APP_OPEN, "微信"),
            Triple(at(9, 13, 0), TimelineItem.Kind.NOTIFICATION, "微信"),
            Triple(at(9, 17, 55), TimelineItem.Kind.APP_CLOSE, "微信"),
            Triple(at(9, 18, 0), TimelineItem.Kind.APP_OPEN, "Chrome"),
            Triple(at(9, 25, 59), TimelineItem.Kind.APP_CLOSE, "Chrome"),
            Triple(at(9, 26, 0), TimelineItem.Kind.APP_OPEN, "腾讯会议"),
            Triple(at(10, 31, 0), TimelineItem.Kind.APP_CLOSE, "腾讯会议"),
        )
        assertEquals(expected.size, items.size)
        expected.zip(items).forEach { (exp, actual) ->
            assertEquals(exp.first, actual.timestamp)
            assertEquals(exp.second, actual.kind)
            assertEquals(exp.third, actual.appName)
        }
    }

    @Test
    fun `通知条目携带标题与正文`() = runBlocking {
        db.timelineEventDao().insertAllIgnoring(seedAcceptanceDay())

        val notification = repository.getDailyTimeline(date).first()
            .first { it.kind == TimelineItem.Kind.NOTIFICATION }

        assertEquals("张三", notification.title)
        assertEquals("晚上一起吃饭吗？", notification.text)
    }

    @Test
    fun `统计页数据按场景聚合`() = runBlocking {
        db.timelineEventDao().insertAllIgnoring(seedAcceptanceDay())

        val stats = repository.getDailyStats(date).first()

        // 事件总数 = 全部原始事件（含桌面噪音与 REMOVED）
        assertEquals(13, stats.totalEvents)

        // App 时长（毫秒）：会议 65m > Chrome 7m59s > 微信 5m55s
        val byApp = stats.appDurations.associate { it.packageName to it.millis }
        assertEquals(3_900_000L, byApp["com.tencent.wemeet.app"])
        assertEquals(479_000L, byApp["com.android.chrome"])
        assertEquals(355_000L, byApp["com.tencent.mm"])
        assertEquals(
            listOf("腾讯会议", "Chrome", "微信"),
            stats.appDurations.map { it.appName },
        )

        // 通知计数：微信 1 条 POSTED
        assertEquals(1, stats.notificationCounts.size)
        assertEquals("微信", stats.notificationCounts[0].appName)
        assertEquals(1, stats.notificationCounts[0].count)
    }

    @Test
    fun `无数据的一天返回空时间轴`() = runBlocking {
        assertEquals(0, repository.getDailyTimeline(date).first().size)
    }

    @Test
    fun `开放会话时长只统计到当前时刻而非日末`() = runBlocking {
        val openTs = System.currentTimeMillis() - 90_000
        db.timelineEventDao().insertAllIgnoring(
            listOf(usageEvent(EventType.APP_OPEN, "com.longrunning", "长开应用", openTs))
        )

        val stats = repository.getDailyStats(date).first()
        val duration = stats.appDurations.single().millis

        // 应封顶到 now（≈90s+），绝不能是"到当天结束"的十几个小时
        val sinceOpen = System.currentTimeMillis() - openTs
        assertTrue(duration in (sinceOpen - 5_000)..(sinceOpen + 60_000))
    }
}
