package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_test_results")
data class SpeedTestResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val downloadSpeedMbps: Double,
    val uploadSpeedMbps: Double,
    val pingMs: Long,
    val jitterMs: Long,
    val networkType: String,
    val networkName: String
)
