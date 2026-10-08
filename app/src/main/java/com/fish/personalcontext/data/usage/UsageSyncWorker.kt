package com.fish.personalcontext.data.usage

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fish.personalcontext.App
import com.fish.personalcontext.util.Permissions
import java.util.concurrent.TimeUnit

/**
 * 定期增量同步（默认 30 分钟）。WorkManager 会把任务并入系统维护窗口批量执行，
 * 周期越长越容易与其他任务合并唤醒，实际增量功耗低于标称；Doze 推迟不影响正确性
 * ——游标不动，下次运行一次性补齐（系统 usage 日志保留约 7 天，余量充足）。
 */
class UsageSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!Permissions.hasUsageAccess(applicationContext)) return Result.success() // 未授权：静默跳过
        return try {
            (applicationContext as App).container.usageStatsCollector.syncOnce()
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "usage sync failed, will retry", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UsageSyncWorker"
        private const val PERIODIC_NAME = "usage_sync_periodic"
        private const val ONESHOT_NAME = "usage_sync_now"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UsageSyncWorker>(30, TimeUnit.MINUTES).build(),
            )
        }

        /** 只在用户打开 App 时调用（前台时刻电量不敏感），让 Today 页即时刷新 */
        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<UsageSyncWorker>()
                    // minSdk 31：expedited 不会退化成 Foreground Service
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }
    }
}
