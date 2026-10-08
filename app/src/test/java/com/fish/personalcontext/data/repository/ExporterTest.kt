package com.fish.personalcontext.data.repository

import com.fish.personalcontext.data.db.TimelineEventEntity
import com.fish.personalcontext.domain.EventType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ExporterTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun event(
        id: Long,
        ts: Long,
        type: EventType,
        title: String? = null,
        text: String? = null,
    ) = TimelineEventEntity(
        id = id,
        eventHash = "hash-$id",
        timestamp = ts,
        type = type.storageKey,
        packageName = "com.tencent.mm",
        appName = "微信",
        title = title,
        text = text,
        notificationKey = if (type == EventType.NOTIFICATION_POSTED) "key-1" else null,
        metadataJson = null,
        createdAt = 1_800_000_000_000,
    )

    @Test
    fun `CSV 表头与转义规则`() {
        val csv = Exporter.buildCsv(
            listOf(event(1, 1_791_423_780_000, EventType.NOTIFICATION_POSTED, title = "张三", text = "他说：\"hi, there\"\n换行也危险")),
            zone,
        )
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("id,timestamp,iso_time,type,package_name,app_name,title,text"))
        val row = lines[1]
        assertTrue(row.contains("\"张三\""))
        // 引号翻倍、换行替换为空格
        assertTrue(row.contains("\"他说：\"\"hi, there\"\" 换行也危险\""))
        assertTrue(row.contains("NOTIFICATION_POSTED"))
    }

    @Test
    fun `空数据导出只有表头`() {
        val csv = Exporter.buildCsv(emptyList(), zone)
        assertEquals("id,timestamp,iso_time,type,package_name,app_name,title,text,notification_key,created_at", csv.trim())
    }

    @Test
    fun `JSON 可解析且字段齐全`() {
        val json = Exporter.buildJson(
            listOf(event(7, 1_791_423_780_000, EventType.APP_OPEN)),
            zone,
        )
        val obj = Json.parseToJsonElement(json).jsonArray[0].jsonObject
        assertEquals(7, obj.getValue("id").jsonPrimitive.content.toLong())
        assertEquals("APP_OPEN", obj.getValue("type").jsonPrimitive.content)
        assertEquals("com.tencent.mm", obj.getValue("packageName").jsonPrimitive.content)
        assertEquals("微信", obj.getValue("appName").jsonPrimitive.content)
        assertEquals("2026-10-08T09:43", obj.getValue("isoTime").jsonPrimitive.content.take(16))
    }
}
