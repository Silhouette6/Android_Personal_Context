package com.fish.personalcontext.data.repository

import android.content.Context
import android.net.Uri
import com.fish.personalcontext.data.db.TimelineEventEntity
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

/** JSON / CSV 导出：全部数据只在本机内存中处理，经 SAF 写入用户选择的文件 */
class Exporter(
    private val context: Context,
    private val repository: TimelineRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun exportJson(uri: Uri): Int {
        val events = repository.allEvents()
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(buildJson(events, zone))
        } ?: return 0
        return events.size
    }

    suspend fun exportCsv(uri: Uri): Int {
        val events = repository.allEvents()
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(buildCsv(events, zone))
        } ?: return 0
        return events.size
    }

    companion object {
        internal fun buildJson(events: List<TimelineEventEntity>, zone: ZoneId): String {
            val array = buildJsonArray { events.forEach { add(eventToJson(it, zone)) } }
            return array.toString()
        }

        internal fun buildCsv(events: List<TimelineEventEntity>, zone: ZoneId): String =
            buildString {
                appendLine(CSV_HEADER)
                for (event in events) {
                    appendLine(
                        listOf(
                            event.id.toString(),
                            event.timestamp.toString(),
                            isoTime(event.timestamp, zone),
                            event.type,
                            event.packageName.orEmpty(),
                            event.appName.orEmpty(),
                            event.title.orEmpty(),
                            event.text.orEmpty(),
                            event.notificationKey.orEmpty(),
                            event.createdAt.toString(),
                        ).joinToString(",") { csvEscape(it) }
                    )
                }
            }

        private fun eventToJson(event: TimelineEventEntity, zone: ZoneId) = buildJsonObject {
            put("id", event.id)
            put("timestamp", event.timestamp)
            put("isoTime", isoTime(event.timestamp, zone))
            put("type", event.type)
            put("packageName", event.packageName)
            put("appName", event.appName)
            put("title", event.title)
            put("text", event.text)
            put("notificationKey", event.notificationKey)
            put("createdAt", event.createdAt)
        }

        private fun isoTime(epochMs: Long, zone: ZoneId): String =
            Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime().toString()

        private fun csvEscape(value: String): String {
            val cleaned = value.replace('\n', ' ').replace('\r', ' ')
            return "\"" + cleaned.replace("\"", "\"\"") + "\""
        }

        private const val CSV_HEADER =
            "id,timestamp,iso_time,type,package_name,app_name,title,text,notification_key,created_at"
    }
}
