package com.example.ui.game

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.GameDatabase
import com.example.data.repository.GameRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

sealed interface GameState {
    object Menu : GameState
    object Playing : GameState
    object Paused : GameState
    data class GameOver(val score: Int, val coins: Int, val isNewHighScore: Boolean) : GameState
    data class Victory(val score: Int, val coins: Int, val isNewHighScore: Boolean) : GameState
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GameRepository
    
    // UI Game Screen Status
    private val _gameState = MutableStateFlow<GameState>(GameState.Menu)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _currentScore = MutableStateFlow(0)
    val currentScore: StateFlow<Int> = _currentScore.asStateFlow()

    private val _currentCoins = MutableStateFlow(0)
    val currentCoins: StateFlow<Int> = _currentCoins.asStateFlow()

    private val _distanceTraveled = MutableStateFlow(0f)
    val distanceTraveled: StateFlow<Float> = _distanceTraveled.asStateFlow()

    private val _highScores = MutableStateFlow<Map<Int, HighScoreInfo>>(emptyMap())
    val highScores: StateFlow<Map<Int, HighScoreInfo>> = _highScores.asStateFlow()

    // Active Level Properties
    private val _selectedLevel = MutableStateFlow<Int>(1) // 1 = Easy, 2 = Medium, 3 = Hard, 4 = Endless
    val selectedLevel: StateFlow<Int> = _selectedLevel.asStateFlow()

    // 3D Player State
    var playerLane = 0 // Target lane: -1 (Left), 0 (Center), 1 (Right)
    var playerVisualLaneOffset = 0f // Interpolates smoothly to playerLane
    var playerZ = 0f // Height above track (for jumps)
    var isSliding = false
    var slideTimer = 0f // In ticks
    var jumpTimer = 0f // In ticks
    
    var playerInvincibilityTimer = 0 // In ticks, for visual flickering after a crash
    var isShieldActive = false // Optional nice mechanic

    // Active Track Entities (In viewport range)
    val activeObstacles = mutableStateListOf<Obstacle3D>()
    val activeCoins = mutableStateListOf<Coin3D>()
    val activeDecorations = mutableStateListOf<Decoration3D>()

    // Current Level Config cached
    var currentLevelConfig = getLevelPreset(1)

    // Peak spawned level track Y coordinate in endless generation
    private var maxSpawnedY = 0f

    // Game loop control
    private var gameLoopJob: Job? = null
    private var lastFrameTime = 0L

    init {
        val database = GameDatabase.getDatabase(application)
        repository = GameRepository(database.scoreDao())
        
        // Load Highscores from database
        viewModelScope.launch {
            repository.allScores.collectLatest { scores ->
                val scoreMap = mutableMapOf<Int, HighScoreInfo>()
                // Populate default placeholders first
                for (id in 1..4) {
                    val preset = getLevelPreset(id)
                    scoreMap[id] = HighScoreInfo(
                        levelId = id,
                        levelName = preset.name,
                        difficulty = preset.title,
                        highScore = 0,
                        maxCoins = 0
                    )
                }
                // Override with database records
                scores.forEach { scoreEntity ->
                    val preset = getLevelPreset(scoreEntity.levelId)
                    scoreMap[scoreEntity.levelId] = HighScoreInfo(
                        levelId = scoreEntity.levelId,
                        levelName = preset.name,
                        difficulty = preset.title,
                        highScore = scoreEntity.highScore,
                        maxCoins = scoreEntity.maxCoins
                    )
                }
                _highScores.value = scoreMap
            }
        }
    }

    fun selectLevel(levelId: Int) {
        _selectedLevel.value = levelId
        currentLevelConfig = getLevelPreset(levelId)
    }

    fun startGame() {
        // Reset state
        playerLane = 0
        playerVisualLaneOffset = 0f
        playerZ = 0f
        isSliding = false
        slideTimer = 0f
        jumpTimer = 0f
        playerInvincibilityTimer = 0
        isShieldActive = false
        
        _currentScore.value = 0
        _currentCoins.value = 0
        _distanceTraveled.value = 0f
        
        // Load level settings
        currentLevelConfig = getLevelPreset(_selectedLevel.value)
        
        // Populate static scenery & items
        generateTrackData()

        _gameState.value = GameState.Playing
        lastFrameTime = System.currentTimeMillis()
        
        // Restart Loop
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            runGameLoop()
        }
    }

    fun pauseGame() {
        if (_gameState.value is GameState.Playing) {
            _gameState.value = GameState.Paused
        }
    }

    fun resumeGame() {
        if (_gameState.value is GameState.Paused) {
            _gameState.value = GameState.Playing
            lastFrameTime = System.currentTimeMillis()
            gameLoopJob?.cancel()
            gameLoopJob = viewModelScope.launch {
                runGameLoop()
            }
        }
    }

    fun quitToMenu() {
        gameLoopJob?.cancel()
        _gameState.value = GameState.Menu
    }

    // Lane, Jump and Slide Controls
    fun moveLeft() {
        if (_gameState.value is GameState.Playing) {
            if (playerLane > -1) {
                playerLane--
            }
        }
    }

    fun moveRight() {
        if (_gameState.value is GameState.Playing) {
            if (playerLane < 1) {
                playerLane++
            }
        }
    }

    fun jump() {
        if (_gameState.value is GameState.Playing) {
            if (playerZ <= 0.05f && !isSliding) {
                playerZ = 0.1f
                jumpTimer = 25f // 25 ticks jump length
            }
        }
    }

    fun slide() {
        if (_gameState.value is GameState.Playing) {
            if (playerZ <= 0.05f) {
                isSliding = true
                slideTimer = 22f // 22 ticks sliding length
            } else {
                // Dive jump (quick drop down + slide)
                playerZ = 0f
                jumpTimer = 0f
                isSliding = true
                slideTimer = 22f
            }
        }
    }

    // Generate tracks based on Level Config
    private fun generateTrackData() {
        activeObstacles.clear()
        activeCoins.clear()
        activeDecorations.clear()

        val length = currentLevelConfig.trackLength
        val theme = currentLevelConfig.levelTheme

        // Spawning decorations on the side of the 3D track
        var decY = 20f
        while (decY < (if (length == 0f) 5000f else length - 100f)) {
            val treeLeft = Decoration3D(
                id = "dec_l_$decY",
                side = -1,
                offsetDistance = 2.5f + Random.nextFloat() * 1.5f,
                trackY = decY,
                type = getThemeDecType(theme)
            )
            val treeRight = Decoration3D(
                id = "dec_r_$decY",
                side = 1,
                offsetDistance = 2.5f + Random.nextFloat() * 1.5f,
                trackY = decY + 15f + Random.nextFloat() * 10f,
                type = getThemeDecType(theme)
            )
            activeDecorations.add(treeLeft)
            activeDecorations.add(treeRight)
            decY += 40f - (currentLevelConfig.obstacleDensity * 10f)
        }

        // Spawning obstacles and coins
        var entityY = 60f
        val obsTypes = getThemeObstacleTypes(theme)
        var idCounter = 0

        while (entityY < (if (length == 0f) 5000f else length - 200f)) {
            val laneCount = Random.nextInt(1, 3) // spawn 1 or 2 obstacles in front
            val usedLanes = mutableSetOf<Int>()

            // Spawning Obstacle
            if (Random.nextFloat() < currentLevelConfig.obstacleDensity) {
                repeat(laneCount) {
                    val lane = (-1..1).filter { it !in usedLanes }.random()
                    usedLanes.add(lane)
                    
                    val selectedType = obsTypes.random()
                    activeObstacles.add(
                        Obstacle3D(
                            id = "obs_${idCounter++}",
                            lane = lane,
                            trackY = entityY,
                            type = selectedType
                        )
                    )
                }
            }

            // Spawning Coins in empty lanes, or lined up before/after obstacles
            for (l in -1..1) {
                if (l !in usedLanes && Random.nextFloat() < 0.45f) {
                    // Spawn a small line of coins
                    repeat(3) { index ->
                        activeCoins.add(
                            Coin3D(
                                id = "coin_${idCounter++}",
                                lane = l,
                                trackY = entityY + (index * 4f)
                            )
                        )
                    }
                }
            }

            entityY += 65f - (currentLevelConfig.obstacleDensity * 20f)
        }
        maxSpawnedY = if (length == 0f) 5000f else length
    }

    private fun getThemeDecType(theme: LevelTheme): DecorationType {
        return when (theme) {
            LevelTheme.GRASSLAND -> DecorationType.TREE
            LevelTheme.CYBER_NEON -> if (Random.nextBoolean()) DecorationType.CYBER_BUILDING else DecorationType.LAMP_POST
            LevelTheme.VOLCANIC_CAVERN -> DecorationType.LAVA_ROCK
            LevelTheme.INFINITE_MARATHON -> DecorationType.TREE
        }
    }

    private fun getThemeObstacleTypes(theme: LevelTheme): List<ObstacleType> {
        return when (theme) {
            LevelTheme.GRASSLAND -> listOf(ObstacleType.HURDLE, ObstacleType.HIGH_ARCH)
            LevelTheme.CYBER_NEON -> listOf(ObstacleType.LASER_WALL, ObstacleType.HIGH_ARCH, ObstacleType.HURDLE)
            LevelTheme.VOLCANIC_CAVERN -> listOf(ObstacleType.MAGMA_SPIKE, ObstacleType.LASER_WALL, ObstacleType.HIGH_ARCH)
            LevelTheme.INFINITE_MARATHON -> listOf(ObstacleType.HURDLE, ObstacleType.LASER_WALL, ObstacleType.HIGH_ARCH)
        }
    }

    // Core running loop
    private suspend fun runGameLoop() {
        while (_gameState.value == GameState.Playing) {
            val now = System.currentTimeMillis()
            var deltaTime = (now - lastFrameTime).coerceIn(5, 50).toFloat() // Cap to avoid sudden teleport jumps
            lastFrameTime = now

            tickPhysics(deltaTime)
            delay(16) // Target 60 FPS
        }
    }

    private fun tickPhysics(deltaTime: Float) {
        // Increment distance
        val speedModifier = if (currentLevelConfig.levelTheme == LevelTheme.INFINITE_MARATHON) {
            // Gradually speed up in endless mode
            1.0f + (_distanceTraveled.value / 1500f).coerceAtMost(1.0f)
        } else {
            1.0f
        }

        val speed = (currentLevelConfig.baseSpeed * speedModifier) / 1.5f
        val distanceInc = speed * deltaTime
        val newDistance = _distanceTraveled.value + distanceInc
        _distanceTraveled.value = newDistance
        
        // In endless mode, dynamically generate obstacles in front of the player
        if (currentLevelConfig.trackLength == 0f) {
            // Keep obstacles, coins and decorations populated ahead of us
            val targetSpawnDistance = newDistance + 1000f
            if (maxSpawnedY < targetSpawnDistance) {
                appendProceduralTrack(maxSpawnedY, targetSpawnDistance)
            }
        }

        // Add score based on distance run
        _currentScore.value = (newDistance * 0.5f).toInt() + (_currentCoins.value * 25)

        // Interpolate player horizontal visual offset towards the targeted lane
        val laneTargetX = playerLane.toFloat()
        val laneDiff = laneTargetX - playerVisualLaneOffset
        if (Math.abs(laneDiff) > 0.01f) {
            playerVisualLaneOffset += laneDiff * 0.22f // Dampened slide shift
        } else {
            playerVisualLaneOffset = laneTargetX
        }

        // Ticking Jump Parabola physics
        if (jumpTimer > 0) {
            jumpTimer--
            // Map jumpTimer from 25..0 down to 0..1 progress
            val progress = (25f - jumpTimer) / 25f
            // parabolic formula: Z rises to maximum 2.8 units and drops of
            playerZ = 2.8f * (4f * progress * (1f - progress))
            if (playerZ < 0.01f) {
                playerZ = 0f
            }
        }

        // Ticking Slide state
        if (isSliding) {
            slideTimer--
            if (slideTimer <= 0) {
                isSliding = false
            }
        }

        // Decrement hit/invincibility visuals
        if (playerInvincibilityTimer > 0) {
            playerInvincibilityTimer--
        }

        // Detect collisions
        checkCollisions()

        // Check level completion
        val length = currentLevelConfig.trackLength
        if (length > 0f && newDistance >= length) {
            handleVictory()
        }
    }

    private fun appendProceduralTrack(startY: Float, endY: Float) {
        var entityY = startY
        val obsTypes = getThemeObstacleTypes(LevelTheme.CYBER_NEON) // Mixing types for infinite runner
        var idCounter = Random.nextInt(100000)

        // Backdrop/Environment cycle logic
        val themeStage = (((_distanceTraveled.value / 1000).toInt()) % 3)
        val currentStageTheme = when (themeStage) {
            0 -> LevelTheme.GRASSLAND
            1 -> LevelTheme.CYBER_NEON
            else -> LevelTheme.VOLCANIC_CAVERN
        }

        while (entityY < endY) {
            // Spawning scenery
            val sideDec = Decoration3D(
                id = "dec_${idCounter++}",
                side = if (Random.nextBoolean()) -1 else 1,
                offsetDistance = 2.5f + Random.nextFloat() * 1.5f,
                trackY = entityY,
                type = getThemeDecType(currentStageTheme)
            )
            activeDecorations.add(sideDec)

            val isDoubleObstacle = Random.nextFloat() < 0.35f
            val usedLanes = mutableSetOf<Int>()

            if (Random.nextFloat() < 0.4f) {
                val lane = (-1..1).random()
                usedLanes.add(lane)
                activeObstacles.add(
                    Obstacle3D(
                        id = "obs_${idCounter++}",
                        lane = lane,
                        trackY = entityY,
                        type = listOf(ObstacleType.HURDLE, ObstacleType.LASER_WALL, ObstacleType.HIGH_ARCH).random()
                    )
                )

                if (isDoubleObstacle) {
                    val lane2 = (-1..1).filter { it != lane }.random()
                    usedLanes.add(lane2)
                    activeObstacles.add(
                        Obstacle3D(
                            id = "obs_${idCounter++}",
                            lane = lane2,
                            trackY = entityY,
                            type = listOf(ObstacleType.HURDLE, ObstacleType.LASER_WALL, ObstacleType.HIGH_ARCH).random()
                        )
                    )
                }
            }

            // Spawn coins
            for (l in -1..1) {
                if (l !in usedLanes && Random.nextFloat() < 0.45f) {
                    repeat(3) { idx ->
                        activeCoins.add(
                            Coin3D(
                                id = "coin_${idCounter++}",
                                lane = l,
                                trackY = entityY + (idx * 4f)
                            )
                        )
                    }
                }
            }

            entityY += 60f
        }

        // Clean up entities lagging far behind (e.g. > 150 units behind player)
        val limit = _distanceTraveled.value - 150f
        activeObstacles.removeAll { it.trackY < limit }
        activeCoins.removeAll { it.trackY < limit }
        activeDecorations.removeAll { it.trackY < limit }
        
        maxSpawnedY = entityY
    }

    private fun checkCollisions() {
        val playerY = _distanceTraveled.value
        val detectionWindow = 1.8f // Width of collision triggers in depth units

        // Check coin collection
        activeCoins.forEach { coin ->
            if (!coin.isCollected && coin.lane == playerLane) {
                // If coin matches player lane and is physically inline with the player
                if (Math.abs(coin.trackY - playerY) < detectionWindow) {
                    coin.isCollected = true
                    _currentCoins.value += 1
                }
            }
        }

        // Check obstacle collision
        if (playerInvincibilityTimer <= 0) {
            activeObstacles.forEach { obs ->
                if (!obs.isHit && obs.lane == playerLane) {
                    if (Math.abs(obs.trackY - playerY) < detectionWindow) {
                        // Check vertical matching
                        val collisionRange = obs.getCollisionZRange()
                        
                        // If player is sliding, their collision size squashes down from 2.0 to 0.8.
                        // If jumping, player's height is playerZ.
                        val playerHeightRange = if (isSliding) {
                            0.0f..0.8f
                        } else {
                            playerZ..(playerZ + 2.0f)
                        }

                        // Check if height profiles overlap
                        val overlaps = playerHeightRange.start <= collisionRange.endInclusive && 
                                       collisionRange.start <= playerHeightRange.endInclusive

                        if (overlaps) {
                            obs.isHit = true
                            handleCrash()
                        }
                    }
                }
            }
        }
    }

    private fun handleCrash() {
        if (isShieldActive) {
            isShieldActive = false
            playerInvincibilityTimer = 60 // 1 second flash
            return
        }

        gameLoopJob?.cancel()
        
        // Update highscore
        viewModelScope.launch {
            val isNewHigh = _currentScore.value > repository.getHighScoreForLevel(_selectedLevel.value)
            repository.updateHighScore(_selectedLevel.value, _currentScore.value, _currentCoins.value)
            
            _gameState.value = GameState.GameOver(
                score = _currentScore.value,
                coins = _currentCoins.value,
                isNewHighScore = isNewHigh
            )
        }
    }

    private fun handleVictory() {
        gameLoopJob?.cancel()
        
        viewModelScope.launch {
            val isNewHigh = _currentScore.value > repository.getHighScoreForLevel(_selectedLevel.value)
            repository.updateHighScore(_selectedLevel.value, _currentScore.value, _currentCoins.value)
            
            _gameState.value = GameState.Victory(
                score = _currentScore.value,
                coins = _currentCoins.value,
                isNewHighScore = isNewHigh
            )
        }
    }

    companion object {
        fun getLevelPreset(levelId: Int): LevelConfig {
            return when (levelId) {
                1 -> LevelConfig(
                    id = 1,
                    name = "草地疾风狂飙",
                    description = "轻松穿梭在森林草场，学习如何躲避基础栏杆，收集亮闪闪的金币吧！",
                    title = "初学者推荐 - 简易级",
                    trackLength = 1500f,
                    baseSpeed = 0.22f, // slower
                    grassColor = Color(0xFFA1E35A),
                    skyColorStart = Color(0xFF8CE3FF),
                    skyColorEnd = Color(0xFFFFF6D6),
                    obstacleDensity = 0.20f,
                    levelTheme = LevelTheme.GRASSLAND
                )
                2 -> LevelConfig(
                    id = 2,
                    name = "赛博霓虹都市",
                    description = "加速冲刺至赛博废土夜色！左右急速位移躲开致命的粒子激光墙！",
                    title = "玩家必备 - 普通级",
                    trackLength = 2500f,
                    baseSpeed = 0.35f,
                    grassColor = Color(0xFF1E0E3F),
                    skyColorStart = Color(0xFF1A1A4E),
                    skyColorEnd = Color(0xFFFF2D85),
                    obstacleDensity = 0.35f,
                    levelTheme = LevelTheme.CYBER_NEON
                )
                3 -> LevelConfig(
                    id = 3,
                    name = "熔火岩浆禁区",
                    description = "地心深处，翻滚的橘红岩浆！超级岩浆刺，手速与肌肉记忆的极致挑战！",
                    title = "高手专属 - 困难级",
                    trackLength = 4000f,
                    baseSpeed = 0.48f, // blazing speed
                    grassColor = Color(0xFF2C1919),
                    skyColorStart = Color(0xFF180A0A),
                    skyColorEnd = Color(0xFFFF4D00),
                    obstacleDensity = 0.50f,
                    levelTheme = LevelTheme.VOLCANIC_CAVERN
                )
                else -> LevelConfig(
                    id = 4,
                    name = "无限超限马拉松",
                    description = "跑酷大师的终极荣誉之路。速度无上限，环境在跑动间无缝循环质变！",
                    title = "无尽狂热 - 极限级",
                    trackLength = 0f, // 0 means endless
                    baseSpeed = 0.26f, // Starts easy, gets harder!
                    grassColor = Color(0xFF17171A),
                    skyColorStart = Color(0xFF0F0B1E),
                    skyColorEnd = Color(0xFF03FFC3),
                    obstacleDensity = 0.38f,
                    levelTheme = LevelTheme.INFINITE_MARATHON
                )
            }
        }
    }
}
