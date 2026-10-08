package com.fish.personalcontext

import android.app.Application

/** Robolectric 测试专用 Application：避免触发真实 App 里的 WorkManager 调度 */
class TestApp : Application()
