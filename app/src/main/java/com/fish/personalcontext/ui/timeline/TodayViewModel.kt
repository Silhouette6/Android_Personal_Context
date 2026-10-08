package com.fish.personalcontext.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fish.personalcontext.data.repository.TimelineRepository
import com.fish.personalcontext.domain.TimelineItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repository: TimelineRepository) : ViewModel() {

    private val dateFlow = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = dateFlow.asStateFlow()

    val items: StateFlow<List<TimelineItem>> = dateFlow
        .flatMapLatest { repository.getDailyTimeline(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun previousDay() {
        dateFlow.value = dateFlow.value.minusDays(1)
    }

    fun nextDay() {
        dateFlow.value = dateFlow.value.plusDays(1)
    }

    fun today() {
        dateFlow.value = LocalDate.now()
    }
}
