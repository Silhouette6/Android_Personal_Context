package com.fish.personalcontext.data.notification

import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import com.fish.personalcontext.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 系统绑定服务：授权后由 system_server 在通知到达/移除时主动回调。
 * 不创建 Foreground Service、不轮询、不持有 wakelock；进程空闲时可被系统冻结。
 * 崩溃安全：回调里第一件事就是把事件持久化（毫秒级 DB 写入，
 * 系统绑定在回调返回后依然保持，写入可可靠完成）。
 */
class NotificationCollector : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val ingestor: NotificationIngestor by lazy {
        (applicationContext as App).container.notificationIngestor
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return // 不记录自身
        scope.launch {
            try {
                ingestor.onPosted(notification, rankingMap)
            } catch (t: Throwable) {
                Log.w(TAG, "persist posted failed: ${notification.key}", t)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        scope.launch {
            try {
                ingestor.onRemoved(notification)
            } catch (t: Throwable) {
                Log.w(TAG, "persist removed failed: ${notification.key}", t)
            }
        }
    }

    override fun onListenerConnected() {
        scope.launch { ingestor.onListenerConnected() }
    }

    override fun onListenerDisconnected() {
        // 系统稍后会自动重绑（权限仍在时），这里只记录状态
        scope.launch { ingestor.onListenerDisconnected() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "NotificationCollector"
    }
}
