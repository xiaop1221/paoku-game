package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {
    @Query("SELECT * FROM scores WHERE levelId = :levelId")
    suspend fun getScoreForLevel(levelId: Int): ScoreEntity?

    @Query("SELECT * FROM scores")
    fun getAllScoresFlow(): Flow<List<ScoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveScore(score: ScoreEntity)
}
