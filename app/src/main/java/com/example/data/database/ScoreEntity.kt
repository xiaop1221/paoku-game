package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey val levelId: Int,
    val highScore: Int,
    val maxCoins: Int,
    val timestamp: Long = System.currentTimeMillis()
)
