package com.fish.personalcontext.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fish.personalcontext.App
import com.fish.personalcontext.data.db.AppDatabase
import com.fish.personalcontext.data.db.SyncKeys
import com.fish.personalcontext.util.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: App) : ViewModel() {

    data class Ui(
        val notificationAccess: Boolean = false,
        val usageAccess: Boolean = false,
        val listenerConnectedAt: Long? = null,
        val lastUsageSyncAt: Long? = null,
        val retentionDays: Int = 0,
        val totalEvents: Long = 0,
        val dbSizeBytes: Long = 0,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val repository = app.container.repository
            combine(
                repository.observeTotalCount(),
                repository.observeSyncState(SyncKeys.LISTENER_CONNECTED_AT),
                repository.observeSyncState(SyncKeys.LAST_USAGE_SYNC_AT),
                repository.observeSyncState(SyncKeys.RETENTION_DAYS),
            ) { total, connectedAt, syncAt, retention ->
                _ui.update {
                    it.copy(
                        totalEvents = total,
                        listenerConnectedAt = connectedAt?.toLongOrNull(),
                        lastUsageSyncAt = syncAt?.toLongOrNull(),
                        retentionDays = retention?.toIntOrNull() ?: 0,
                        dbSizeBytes = dbSizeBytes(),
                    )
                }
            }.collect { }
        }
    }

    /** 权限状态只能主动查询（非 Flow），在 ON_RESUME 时刷新 */
    fun refresh() {
        _ui.update {
            it.copy(
                notificationAccess = Permissions.hasNotificationAccess(app),
                usageAccess = Permissions.hasUsageAccess(app),
                dbSizeBytes = dbSizeBytes(),
            )
        }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { app.container.repository.setRetentionDays(days) }
    }

    fun deleteAll() {
        viewModelScope.launch { app.container.repository.deleteAllData() }
    }

    fun exportJson(uri: Uri) {
        viewModelScope.launch { app.container.exporter.exportJson(uri) }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch { app.container.exporter.exportCsv(uri) }
    }

    private fun dbSizeBytes(): Long =
        listOf(AppDatabase.NAME, AppDatabase.NAME + "-wal", AppDatabase.NAME + "-shm")
            .sumOf { name -> app.getDatabasePath(name).length() }
}
