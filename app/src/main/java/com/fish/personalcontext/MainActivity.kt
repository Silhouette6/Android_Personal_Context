package com.fish.personalcontext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fish.personalcontext.data.usage.UsageSyncWorker
import com.fish.personalcontext.ui.AppRoot
import com.fish.personalcontext.ui.theme.PersonalContextTheme

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
    }
}
