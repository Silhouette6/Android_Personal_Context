package com.fish.personalcontext.domain

enum class EventType(val storageKey: String) {
    APP_OPEN("APP_OPEN"),
    APP_CLOSE("APP_CLOSE"),
    NOTIFICATION_POSTED("NOTIFICATION_POSTED"),
    NOTIFICATION_REMOVED("NOTIFICATION_REMOVED");

    companion object {
        fun fromStorage(key: String): EventType = entries.first { it.storageKey == key }
    }
}
