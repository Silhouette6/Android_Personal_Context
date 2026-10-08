package com.fish.personalcontext

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.fish.personalcontext.data.appinfo.AppInfoCache
import com.fish.personalcontext.data.db.AppDatabase
import com.fish.personalcontext.data.maintenance.CleanupWorker
import com.fish.personalcontext.data.notification.NotificationIngestor
import com.fish.personalcontext.data.repository.Exporter
import com.fish.personalcontext.data.repository.TimelineRepository
import com.fish.personalcontext.data.usage.UsageStatsCollector
import com.fish.personalcontext.data.usage.UsageSyncWorker
import kotlinx.serialization.json.Json

class App : Application() {
    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        container = Container(this)
        // 两个 Worker 都是 KEEP 语义：重复启动安全，重启后 WorkManager 自动恢复调度
        UsageSyncWorker.schedule(this)
        CleanupWorker.schedule(this)
    }
}

/** 手动依赖容器：个人项目不引入 DI 框架 */
class Container(context: Context) {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
        .fallbackToDestructiveMigration() // MVP：schema 变更直接重建
        .build()
    val appInfoCache = AppInfoCache(context.packageManager)
    val notificationIngestor = NotificationIngestor(db, appInfoCache, json)
    val usageStatsCollector = UsageStatsCollector(context, db, appInfoCache)
    val repository = TimelineRepository(db, appInfoCache)
    val exporter = Exporter(context, repository)
}
