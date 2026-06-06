package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SpeedTestRepository
import com.example.data.SpeedTestResult
import com.example.network.NetworkInfo
import com.example.network.NetworkMonitor
import com.example.network.NetworkType
import com.example.network.SpeedTestState
import com.example.network.SpeedTester
import com.example.network.TestPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpeedTestViewModel(
    application: Application,
    private val repository: SpeedTestRepository
) : AndroidViewModel(application) {

    private val networkMonitor = NetworkMonitor(application)
    private val speedTesterCheck = SpeedTester()

    val networkInfo: StateFlow<NetworkInfo> = networkMonitor.networkInfo

    private val _speedTestState = MutableStateFlow(SpeedTestState())
    val speedTestState: StateFlow<SpeedTestState> = _speedTestState.asStateFlow()

    val historyResults: StateFlow<List<SpeedTestResult>> = repository.allResults
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun refreshNetworkInfo() {
        viewModelScope.launch {
            networkMonitor.updateNetworkInfo()
        }
    }

    fun startSpeedTest() {
        if (_speedTestState.value.phase == TestPhase.PING ||
            _speedTestState.value.phase == TestPhase.DOWNLOAD ||
            _speedTestState.value.phase == TestPhase.UPLOAD
        ) {
            // Already testing
            return
        }

        viewModelScope.launch {
            _speedTestState.value = SpeedTestState(phase = TestPhase.PING)
            // Ensure connection info is up-to-date
            networkMonitor.updateNetworkInfo()
            val netInfo = networkInfo.value

            speedTesterCheck.runSpeedTest().collect { state ->
                _speedTestState.value = state

                if (state.phase == TestPhase.COMPLETED) {
                    // Test completed successfully, insert into Room Database!
                    val finalResult = SpeedTestResult(
                        downloadSpeedMbps = state.finalDownloadMbps,
                        uploadSpeedMbps = state.finalUploadMbps,
                        pingMs = state.pingMs,
                        jitterMs = state.jitterMs,
                        networkType = when (netInfo.type) {
                            NetworkType.WIFI -> "Wi-Fi"
                            NetworkType.MOBILE -> "Mobilní"
                            else -> "Neznámá"
                        },
                        networkName = netInfo.name
                    )
                    repository.insert(finalResult)
                }
            }
        }
    }

    fun deleteTestResult(id: Int) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    class Factory(
        private val application: Application,
        private val repository: SpeedTestRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SpeedTestViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SpeedTestViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
