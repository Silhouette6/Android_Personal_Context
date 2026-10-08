package com.fish.personalcontext.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.fish.personalcontext.ui.settings.SettingsScreen
import com.fish.personalcontext.ui.statistics.StatisticsScreen
import com.fish.personalcontext.ui.timeline.TodayScreen

private class Tab(val label: String, val icon: ImageVector)

@Composable
fun AppRoot() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        Tab("时间轴", Icons.Outlined.Timeline),
        Tab("统计", Icons.Outlined.BarChart),
        Tab("设置", Icons.Outlined.Settings),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Crossfade(targetState = selected, label = "pages") { page ->
            when (page) {
                0 -> TodayScreen(Modifier.padding(padding))
                1 -> StatisticsScreen(Modifier.padding(padding))
                else -> SettingsScreen(Modifier.padding(padding))
            }
        }
    }
}
