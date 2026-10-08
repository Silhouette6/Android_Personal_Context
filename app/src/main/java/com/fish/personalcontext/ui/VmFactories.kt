package com.fish.personalcontext.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fish.personalcontext.App
import com.fish.personalcontext.ui.settings.SettingsViewModel
import com.fish.personalcontext.ui.statistics.StatisticsViewModel
import com.fish.personalcontext.ui.timeline.TodayViewModel

object VmFactory {

    private fun CreationExtras.app(): App =
        this[AndroidViewModelFactory.APPLICATION_KEY] as App

    val Today = viewModelFactory {
        initializer { TodayViewModel(app().container.repository) }
    }

    val Statistics = viewModelFactory {
        initializer { StatisticsViewModel(app().container.repository) }
    }

    val Settings = viewModelFactory {
        initializer { SettingsViewModel(app()) }
    }
}
