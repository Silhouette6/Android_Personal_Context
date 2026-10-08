package com.fish.personalcontext.data.maintenance

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fish.personalcontext.App
import com.fish.personalcontext.data.db.SyncKeys
import java.util.concurrent.TimeUnit

/** 每日按保留策略清理旧事件（retention_days = 0 表示永久保留） */
class CleanupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as App).container
        val days = container.db.syncStateDao().get(SyncKeys.RETENTION_DAYS)?.toIntOrNull() ?: 0
        if (days > 0) {
            val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY
            val deleted = container.db.timelineEventDao().deleteOlderThan(cutoff)
            Log.d(TAG, "retention cleanup: deleted=$deleted retentionDays=$days")
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "CleanupWorker"
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "cleanup_daily",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
