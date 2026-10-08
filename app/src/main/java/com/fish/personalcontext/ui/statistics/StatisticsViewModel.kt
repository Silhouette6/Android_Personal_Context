package com.fish.personalcontext.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fish.personalcontext.data.repository.TimelineRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class StatisticsViewModel(repository: TimelineRepository) : ViewModel() {

    val stats: StateFlow<TimelineRepository.DailyStats?> = repository
        .getDailyStats(LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
