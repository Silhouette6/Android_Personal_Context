package com.fish.personalcontext.ui.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fish.personalcontext.data.repository.TimelineRepository
import com.fish.personalcontext.ui.VmFactory
import com.fish.personalcontext.util.TimeFmt

@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    vm: StatisticsViewModel = viewModel(factory = VmFactory.Statistics),
) {
    val stats by vm.stats.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "今日统计",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        val current = stats
        if (current == null || (current.totalEvents == 0 && current.appDurations.isEmpty())) {
            Text(
                "暂无数据，授权后事件会自动积累",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            TotalEventsCard(current.totalEvents)
            SectionHeader("App 使用")
            AppUsageList(current)
            SectionHeader("通知")
            NotificationList(current)
        }
    }
}

@Composable
private fun TotalEventsCard(total: Int) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            "事件总数",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            total.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppUsageList(stats: TimelineRepository.DailyStats) {
    if (stats.appDurations.isEmpty()) {
        EmptySection("暂无 App 使用记录")
        return
    }
    val maxMillis = stats.appDurations.maxOf { it.millis }.coerceAtLeast(1)
    stats.appDurations.take(10).forEach { duration ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    duration.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    TimeFmt.duration(duration.millis),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { duration.millis / maxMillis.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(4.dp),
            )
        }
    }
}

@Composable
private fun NotificationList(stats: TimelineRepository.DailyStats) {
    if (stats.notificationCounts.isEmpty()) {
        EmptySection("暂无通知")
        return
    }
    stats.notificationCounts.take(10).forEach { row ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.appName ?: row.packageName ?: "未知",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                row.count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptySection(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
