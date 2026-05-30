package com.example.ui.game

import androidx.compose.ui.graphics.Color

enum class ObstacleType {
    HURDLE,      // Low barrier: must jump
    LASER_WALL,  // Standard block: must change lane
    HIGH_ARCH,   // High barrier: must slide underneath
    MAGMA_SPIKE  // Spikes: must jump or shift lanes
}

data class LevelConfig(
    val id: Int,
    val name: String,
    val description: String,
    val title: String, // e.g. "Easy", "Medium", "Hard", "Endless"
    val trackLength: Float, // 0f for endless
    val baseSpeed: Float, // Speed in track units per millisecond
    val grassColor: Color,
    val skyColorStart: Color,
    val skyColorEnd: Color,
    val obstacleDensity: Float, // Higher means more frequent obstacles
    val levelTheme: LevelTheme
)

enum class LevelTheme {
    GRASSLAND,
    CYBER_NEON,
    VOLCANIC_CAVERN,
    INFINITE_MARATHON
}

data class Obstacle3D(
    val id: String,
    val lane: Int, // -1 (left), 0 (center), 1 (right)
    var trackY: Float, // Position on the track
    val height: Float = 2.0f,
    val width: Float = 1.2f,
    val type: ObstacleType,
    var isHit: Boolean = false
) {
    fun getCollisionZRange(): ClosedRange<Float> {
        return when (type) {
            ObstacleType.HIGH_ARCH -> 1.5f..4.0f // Player must slide (height -> 0.8f) to go underneath (0f..0.8f range is safe)
            ObstacleType.HURDLE -> 0.0f..1.6f // Player must jump to clear
            ObstacleType.LASER_WALL -> 0.0f..2.5f // Impassable height, player must lane switch
            ObstacleType.MAGMA_SPIKE -> 0.0f..1.8f // Must jump
        }
    }
}

data class Coin3D(
    val id: String,
    val lane: Int,
    val trackY: Float,
    val z: Float = 0.5f,
    var isCollected: Boolean = false
)

data class Decoration3D(
    val id: String,
    val side: Int, // -1 for left side of road, 1 for right side
    val offsetDistance: Float, // Distance outward from track boundary
    val trackY: Float,
    val type: DecorationType,
    val scale: Float = 1.0f,
    val height: Float = 4.0f
)

enum class DecorationType {
    TREE,
    CYBER_BUILDING,
    LAMP_POST,
    CACTUS,
    LAVA_ROCK,
    NEON_SIGN
}

data class HighScoreInfo(
    val levelId: Int,
    val levelName: String,
    val difficulty: String,
    val highScore: Int,
    val maxCoins: Int
)
