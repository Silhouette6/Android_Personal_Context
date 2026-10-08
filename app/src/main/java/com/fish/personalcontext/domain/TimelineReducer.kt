package com.fish.personalcontext.domain

/** APP_OPEN/APP_CLOSE 原始流折叠出的 App 会话；endTime=null 表示截断查询窗口时仍在前台 */
data class AppSession(
    val packageName: String,
    val appName: String?,
    val startTime: Long,
    val endTime: Long?,
)

/**
 * 读时去噪（原始事件在库里不可变）：
 *  - 同包连续 RESUMED 突发（< BURST_WINDOW）合并为一次打开
 *  - PAUSED 后短时间内同包 RESUMED = 页内切换，丢弃该次关闭
 *  - 切换到其他包 = 上一会话在 PAUSED 时刻结束
 *  - 漏收 PAUSED 的切换：上一会话近似结束于新 App 打开时刻
 *  - 尾部未关闭的会话保持 endTime=null，交给查询窗口决定展示口径
 */
object TimelineReducer {

    const val BURST_WINDOW_MS: Long = 45_000

    fun reduce(appEvents: List<TimelineEvent>): List<AppSession> {
        val sorted = appEvents
            .filter { it.type == EventType.APP_OPEN || it.type == EventType.APP_CLOSE }
            .sortedBy { it.timestamp }

        val sessions = mutableListOf<AppSession>()
        var open: TimelineEvent? = null
        var pendingClose: TimelineEvent? = null

        fun emitSession(start: TimelineEvent, end: Long?) {
            sessions += AppSession(
                packageName = start.packageName ?: "unknown",
                appName = start.appName,
                startTime = start.timestamp,
                endTime = end,
            )
        }

        for (event in sorted) {
            val pkg = event.packageName ?: continue
            when (event.type) {
                EventType.APP_OPEN -> {
                    val close = pendingClose
                    if (close != null) {
                        if (close.packageName == pkg &&
                            event.timestamp - close.timestamp < BURST_WINDOW_MS
                        ) {
                            // 页内切换：PAUSED 后同包很快 RESUMED，会话未中断
                            pendingClose = null
                            continue
                        }
                        // 切到别的包：上一会话在 PAUSED 时刻结束
                        open?.let { emitSession(it, close.timestamp) }
                        open = null
                        pendingClose = null
                    }
                    val current = open
                    when {
                        current == null -> open = event
                        current.packageName == pkg -> {
                            if (event.timestamp - current.timestamp >= BURST_WINDOW_MS) {
                                // 无 PAUSED 的长间隙重开（罕见）：切成两个会话
                                emitSession(current, event.timestamp)
                                open = event
                            }
                            // 否则：同包突发重复 RESUMED，忽略
                        }
                        else -> {
                            // 漏收 PAUSED 的切换：上一会话近似结束于新 App 打开时刻
                            emitSession(current, event.timestamp)
                            open = event
                        }
                    }
                }
                EventType.APP_CLOSE -> {
                    if (pendingClose != null) continue // PAUSED 后又来 STOPPED：保留第一个
                    val current = open ?: continue // 迟到的 close：忽略
                    if (current.packageName == pkg) {
                        // 暂挂，等下一个 OPEN 判定是页内切换还是真关闭
                        pendingClose = event
                    }
                }
                else -> Unit
            }
        }

        pendingClose?.let { close ->
            open?.let { emitSession(it, close.timestamp) }
            open = null
        }
        open?.let { emitSession(it, null) }
        return sessions
    }
}
