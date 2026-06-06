package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestDao {
    @Query("SELECT * FROM speed_test_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<SpeedTestResult>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: SpeedTestResult)

    @Query("DELETE FROM speed_test_results")
    suspend fun clearAll()

    @Query("DELETE FROM speed_test_results WHERE id = :id")
    suspend fun deleteResultById(id: Int)
}
