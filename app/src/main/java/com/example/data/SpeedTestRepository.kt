package com.example.data

import kotlinx.coroutines.flow.Flow

class SpeedTestRepository(private val speedTestDao: SpeedTestDao) {
    val allResults: Flow<List<SpeedTestResult>> = speedTestDao.getAllResults()

    suspend fun insert(result: SpeedTestResult) {
        speedTestDao.insertResult(result)
    }

    suspend fun clearHistory() {
        speedTestDao.clearAll()
    }

    suspend fun delete(id: Int) {
        speedTestDao.deleteResultById(id)
    }
}
