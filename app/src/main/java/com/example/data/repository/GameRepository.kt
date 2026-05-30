package com.example.data.repository

import com.example.data.database.ScoreDao
import com.example.data.database.ScoreEntity
import kotlinx.coroutines.flow.Flow

class GameRepository(private val scoreDao: ScoreDao) {

    val allScores: Flow<List<ScoreEntity>> = scoreDao.getAllScoresFlow()

    suspend fun getHighScoreForLevel(levelId: Int): Int {
        return scoreDao.getScoreForLevel(levelId)?.highScore ?: 0
    }

    suspend fun getMaxCoinsForLevel(levelId: Int): Int {
        return scoreDao.getScoreForLevel(levelId)?.maxCoins ?: 0
    }

    suspend fun updateHighScore(levelId: Int, score: Int, coins: Int) {
        val existing = scoreDao.getScoreForLevel(levelId)
        val newHighScore = if (existing != null) maxOf(existing.highScore, score) else score
        val newMaxCoins = if (existing != null) maxOf(existing.maxCoins, coins) else coins
        
        scoreDao.saveScore(
            ScoreEntity(
                levelId = levelId,
                highScore = newHighScore,
                maxCoins = newMaxCoins,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
