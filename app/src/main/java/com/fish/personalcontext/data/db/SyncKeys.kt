package com.fish.personalcontext.data.db

object SyncKeys {
    /** usage 游标：最后已处理事件的原始时间戳 */
    const val LAST_USAGE_EVENT_TS = "last_usage_event_timestamp"

    const val LAST_USAGE_SYNC_AT = "last_usage_sync_at"

    const val LISTENER_CONNECTED_AT = "listener_connected_at"

    const val LISTENER_DISCONNECTED_AT = "listener_disconnected_at"

    /** 数据保留天数；0 = 永久保留 */
    const val RETENTION_DAYS = "retention_days"
}
