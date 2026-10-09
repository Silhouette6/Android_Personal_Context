package com.fish.personalcontext.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fish.personalcontext.ui.VmFactory
import com.fish.personalcontext.util.TimeFmt
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(factory = VmFactory.Settings),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    val dateTag = remember { LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::exportJson) }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(vm::exportCsv) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )

        Section("权限")
        PermissionRow("通知访问", ui.notificationAccess) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        PermissionRow("使用情况访问", ui.usageAccess) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }

        Section("采集器")
        InfoRow("通知监听", ui.listenerStatus)
        InfoRow(
            "使用统计",
            ui.lastUsageSyncAt?.let { "上次同步 ${TimeFmt.dateTime(it)}" } ?: "尚未同步",
        )

        Section("存储")
        InfoRow("事件总数", ui.totalEvents.toString())
        InfoRow("数据库大小", TimeFmt.fileSize(ui.dbSizeBytes))
        InfoRow("数据保留", retentionLabel(ui.retentionDays), onClick = { showRetentionDialog = true })

        Section("数据")
        ActionRow("导出 JSON") { jsonLauncher.launch("personal-context-$dateTag.json") }
        ActionRow("导出 CSV") { csvLauncher.launch("personal-context-$dateTag.csv") }
        ActionRow("删除全部数据", destructive = true) { showDeleteDialog = true }

        Text(
            "所有数据仅保存在本机 · 应用无网络权限",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除全部数据？") },
            text = { Text("将删除 ${ui.totalEvents} 条事件，且不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        vm.deleteAll()
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }

    if (showRetentionDialog) {
        val options = listOf(0, 7, 30, 90, 365)
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = { Text("数据保留策略") },
            text = {
                Column {
                    options.forEach { days ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setRetentionDays(days)
                                    showRetentionDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = ui.retentionDays == days,
                                onClick = {
                                    vm.setRetentionDays(days)
                                    showRetentionDialog = false
                                },
                            )
                            Text(retentionLabel(days), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRetentionDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        AssistChip(
            onClick = onOpen,
            label = { Text(if (granted) "ON" else "OFF") },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (granted) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionRow(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

private fun retentionLabel(days: Int): String = when (days) {
    0 -> "永久保留"
    365 -> "1 年"
    else -> "$days 天"
}
