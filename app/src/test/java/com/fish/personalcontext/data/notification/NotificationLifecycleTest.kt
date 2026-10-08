package com.fish.personalcontext.data.notification

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fish.personalcontext.TestApp
import com.fish.personalcontext.data.appinfo.AppInfoCache
import com.fish.personalcontext.data.db.AppDatabase
import com.fish.personalcontext.data.db.TimelineEventEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 通知生命周期场景（spec §8）：
 * POST → POST(update) → POST(update) → REMOVE 只应产生 1 条 POSTED + 1 条 REMOVED，
 * 且 POSTED 保留首次 postTime、内容为最新。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class NotificationLifecycleTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var ingestor: NotificationIngestor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ingestor = NotificationIngestor(db, AppInfoCache(context.packageManager), Json)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sbn(id: Int, title: String, text: String, postTime: Long, pkg: String = "com.tencent.mm"): StatusBarNotification {
        val notification = Notification.Builder(context, "chat")
            .setSmallIcon(1)
            .setContentTitle(title)
            .setContentText(text)
            .build()
        return StatusBarNotification(pkg, pkg, id, null, 1000, 2000, 0, notification, Process.myUserHandle(), postTime)
    }

    @Test
    fun `post 后多次 update 只保留首时间并刷新内容`() = runBlocking {
        ingestor.onPosted(sbn(id = 1, title = "张三", text = "在吗", postTime = 1_000), null)
        ingestor.onPosted(sbn(id = 1, title = "张三", text = "晚上一起吃饭吗？", postTime = 2_000), null)
        ingestor.onPosted(sbn(id = 1, title = "张三", text = "晚上一起吃饭吗？地址发你", postTime = 3_000), null)

        val posted = db.timelineEventDao().allEvents().filter { it.type == "NOTIFICATION_POSTED" }
        assertEquals(1, posted.size)
        assertEquals(1_000L, posted[0].timestamp) // 首次 postTime
        assertEquals("晚上一起吃饭吗？地址发你", posted[0].text) // 最新内容
        assertEquals("张三", posted[0].title)
    }

    @Test
    fun `remove 记录移除事件且不产生重复`() = runBlocking {
        ingestor.onPosted(sbn(id = 1, title = "张三", text = "在吗", postTime = 1_000), null)
        val beforeRemove = System.currentTimeMillis()
        ingestor.onRemoved(sbn(id = 1, title = "张三", text = "在吗", postTime = 1_000))
        ingestor.onRemoved(sbn(id = 1, title = "张三", text = "在吗", postTime = 1_000)) // 重复 remove 幂等

        val events = db.timelineEventDao().allEvents()
        assertEquals(2, events.size)
        val removed = events.filter { it.type == "NOTIFICATION_REMOVED" }
        assertEquals(1, removed.size)
        assertTrue(removed[0].timestamp >= beforeRemove) // remove 时刻，非 postTime
    }

    @Test
    fun `移除后同 key 再发新消息仍只有一条 POSTED`() = runBlocking {
        ingestor.onPosted(sbn(id = 1, title = "张三", text = "在吗", postTime = 1_000), null)
        ingestor.onRemoved(sbn(id = 1, title = "张三", text = "在吗", postTime = 1_000))
        ingestor.onPosted(sbn(id = 1, title = "张三", text = "新消息", postTime = 5_000), null)

        val posted = db.timelineEventDao().allEvents().filter { it.type == "NOTIFICATION_POSTED" }
        assertEquals(1, posted.size)
        assertEquals("新消息", posted[0].text)
    }

    @Test
    fun `不同会话（不同通知）各自独立成行`() = runBlocking {
        ingestor.onPosted(sbn(id = 1, title = "张三", text = "吃饭", postTime = 1_000), null)
        ingestor.onPosted(sbn(id = 2, title = "李四", text = "文件发你了", postTime = 1_500), null)

        val posted = db.timelineEventDao().allEvents().filter { it.type == "NOTIFICATION_POSTED" }
        assertEquals(2, posted.size)
        assertEquals(setOf(1_000L, 1_500L), posted.map { it.timestamp }.toSet())
    }

    @Test
    fun `metadata 记录渠道与更新时间`() = runBlocking {
        val before = System.currentTimeMillis()
        ingestor.onPosted(sbn(id = 1, title = "张三", text = "在吗", postTime = 1_000), null)

        val posted: TimelineEventEntity = db.timelineEventDao().allEvents().first()
        val meta = posted.metadataJson.orEmpty()
        assertTrue(meta.contains("\"channelId\":\"chat\""))
        assertTrue(meta.contains("\"lastUpdateTimeMs\":"))
        assertTrue(meta.contains("\"isOngoing\":false") || !meta.contains("isOngoing"))
    }
}
