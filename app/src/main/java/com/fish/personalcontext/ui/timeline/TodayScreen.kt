package com.fish.personalcontext.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fish.personalcontext.domain.TimelineItem
import com.fish.personalcontext.ui.VmFactory
import com.fish.personalcontext.util.TimeFmt
import java.time.LocalDate

@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    vm: TodayViewModel = viewModel(factory = VmFactory.Today),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    Column(modifier.fillMaxWidth()) {
        DateHeader(
            date = date,
            canGoNext = date.isBefore(today),
            onPrev = vm::previousDay,
            onNext = vm::nextDay,
            onToday = vm::today,
        )
        if (items.isEmpty()) {
            EmptyTimeline(isToday = date == today)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            ) {
                itemsIndexed(items) { index, item ->
                    TimelineRow(
                        item = item,
                        isFirst = index == 0,
                        isLast = index == items.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(
    date: LocalDate,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一天")
        }
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(TimeFmt.dateHeader(date), style = MaterialTheme.typography.titleLarge)
            if (canGoNext) {
                TextButton(onClick = onToday) { Text("回到今天") }
            }
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "后一天")
        }
    }
}

/** 时间轴 rail：左列时间，2dp 竖线 + 8dp 节点，右侧两行文字 */
@Composable
private fun TimelineRow(item: TimelineItem, isFirst: Boolean, isLast: Boolean) {
    val colors = MaterialTheme.colorScheme
    val dotColor = when (item.kind) {
        TimelineItem.Kind.APP_OPEN -> colors.primary
        TimelineItem.Kind.APP_CLOSE -> colors.outline
        TimelineItem.Kind.NOTIFICATION -> colors.secondary
    }
    val railColor = colors.outlineVariant

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            text = TimeFmt.time(item.timestamp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .width(46.dp)
                .padding(top = 2.dp),
        )
        Box(
            Modifier
                .width(22.dp)
                .fillMaxHeight()
                .drawBehind {
                    val centerX = size.width / 2f
                    val dotY = 11.dp.toPx()
                    val topY = if (isFirst) dotY else 0f
                    val bottomY = if (isLast) dotY else size.height
                    drawLine(
                        color = railColor,
                        start = Offset(centerX, topY),
                        end = Offset(centerX, bottomY),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawCircle(
                        color = dotColor,
                        radius = 4.dp.toPx(),
                        center = Offset(centerX, dotY),
                    )
                },
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            val name = item.appName ?: item.packageName.orEmpty()
            when (item.kind) {
                TimelineItem.Kind.APP_OPEN ->
                    Text("打开 $name", style = MaterialTheme.typography.titleMedium)
                TimelineItem.Kind.APP_CLOSE ->
                    Text(
                        "离开 $name",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                    )
                TimelineItem.Kind.NOTIFICATION -> {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    val detail = listOfNotNull(item.title, item.text).joinToString("：")
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTimeline(isToday: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (isToday) "今天还没有事件" else "这一天没有记录",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "授权「通知访问」与「使用情况访问」后，事件会自动出现在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
