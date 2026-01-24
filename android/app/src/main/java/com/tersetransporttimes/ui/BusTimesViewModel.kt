package com.tersetransporttimes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tersetransporttimes.data.BusTimesResponse
import com.tersetransporttimes.repository.BusTimesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class BusTimesState {
    object Loading : BusTimesState()
    data class Success(val data: BusTimesResponse) : BusTimesState()
    data class Error(val message: String) : BusTimesState()
}

class BusTimesViewModel : ViewModel() {
    private val repository = BusTimesRepository()

    private val _state = MutableStateFlow<BusTimesState>(BusTimesState.Loading)
    val state: StateFlow<BusTimesState> = _state.asStateFlow()

    private var autoRefreshJob: Job? = null
    private val AUTO_REFRESH_INTERVAL = 30_000L // 30 seconds

    init {
        loadBusTimes()
        startAutoRefresh()
    }

    fun loadBusTimes() {
        viewModelScope.launch {
            _state.value = BusTimesState.Loading

            repository.getBusTimes()
                .onSuccess { response ->
                    _state.value = BusTimesState.Success(response)
                }
                .onFailure { error ->
                    _state.value = BusTimesState.Error(
                        error.message ?: "Failed to load bus times"
                    )
                }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL)
                // Refresh silently without showing loading state
                repository.getBusTimes()
                    .onSuccess { response ->
                        _state.value = BusTimesState.Success(response)
                    }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
