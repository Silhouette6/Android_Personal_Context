package com.fish.personalcontext.domain

/** UI 渲染用的展示项：App 会话边界 + 通知（已去噪） */
data class TimelineItem(
    val timestamp: Long,
    val kind: Kind,
    val packageName: String?,
    val appName: String?,
    val title: String?,
    val text: String?,
) {
    enum class Kind { APP_OPEN, APP_CLOSE, NOTIFICATION }
}
