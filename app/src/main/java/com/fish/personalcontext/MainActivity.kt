package com.fish.personalcontext

import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fish.personalcontext.data.notification.NotificationCollector
import com.fish.personalcontext.data.usage.UsageSyncWorker
import com.fish.personalcontext.ui.AppRoot
import com.fish.personalcontext.ui.theme.PersonalContextTheme
import com.fish.personalcontext.util.Permissions

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PersonalContextTheme {
                AppRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 用户在前台时补一次同步，让 Today 页即时刷新；后台绝不主动刷
        UsageSyncWorker.syncNow(this)
        // 自愈：若监听绑定被 ROM 或进程回收悄悄断开（真机实测最常见故障），
        // 主动请求系统重绑；未授权时系统会忽略此调用，无副作用
        if (Permissions.hasNotificationAccess(this)) {
            NotificationListenerService.requestRebind(
                ComponentName(this, NotificationCollector::class.java)
            )
        }
    }
}
