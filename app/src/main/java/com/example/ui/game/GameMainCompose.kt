package com.example.ui.game

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import java.util.*
import kotlin.math.sin

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GameMainCompose(
    viewModel: GameViewModel = viewModel()
) {
    val gameState by viewModel.gameState.collectAsState()
    val currentScore by viewModel.currentScore.collectAsState()
    val currentCoins by viewModel.currentCoins.collectAsState()
    val distance by viewModel.distanceTraveled.collectAsState()
    val selectedLevel by viewModel.selectedLevel.collectAsState()
    val highScores by viewModel.highScores.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            // 1. Render gameplay active backgrounds or levels if playing or paused
            if (gameState is GameState.Playing || gameState is GameState.Paused) {
                GameRenderer(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
                
                // HUD displays
                GameHUDOverlay(
                    score = currentScore,
                    coins = currentCoins,
                    distance = distance,
                    config = viewModel.currentLevelConfig,
                    onPause = { viewModel.pauseGame() }
                )
                
                // On-screen Virtual controller overlay suited for phones
                GameVirtualButtons(
                    onLeft = { viewModel.moveLeft() },
                    onRight = { viewModel.moveRight() },
                    onJump = { viewModel.jump() },
                    onSlide = { viewModel.slide() }
                )
            }

            // 2. State-driven full screen overlay dialogues
            AnimatedContent(
                targetState = gameState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(280)) with fadeOut(animationSpec = tween(280))
                },
                label = "gamestate_overlay"
            ) { state ->
                when (state) {
                    is GameState.Menu -> {
                        GameMainMenuScreen(
                            selectedLevel = selectedLevel,
                            highScores = highScores,
                            onLevelSelect = { viewModel.selectLevel(it) },
                            onStartGame = { viewModel.startGame() }
                        )
                    }
                    is GameState.Paused -> {
                        GamePauseOverlay(
                            onCancel = { viewModel.resumeGame() },
                            onQuit = { viewModel.quitToMenu() }
                        )
                    }
                    is GameState.GameOver -> {
                        GameOverOverlay(
                            score = state.score,
                            coins = state.coins,
                            isNewHighScore = state.isNewHighScore,
                            onResume = { viewModel.startGame() },
                            onQuit = { viewModel.quitToMenu() }
                        )
                    }
                    is GameState.Victory -> {
                        GameVictoryOverlay(
                            score = state.score,
                            coins = state.coins,
                            isNewHighScore = state.isNewHighScore,
                            onResume = { viewModel.startGame() },
                            onQuit = { viewModel.quitToMenu() }
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

// ----------------------------------------------------
// UI Sub-components
// ----------------------------------------------------

@Composable
fun GameMainMenuScreen(
    selectedLevel: Int,
    highScores: Map<Int, HighScoreInfo>,
    onLevelSelect: (Int) -> Unit,
    onStartGame: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Glowing Title
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "极速狂飙 3D",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF00FFCC),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp).testTag("game_title_text")
            )
            Text(
                text = "SPEED PARKOUR 3D RUNNER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF2D85),
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Player Avatar Card displaying user likeness
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .shadow(12.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(75.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, Color(0xFF00FFCC), CircleShape)
                            .background(Color(0xFF130F2C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_player_avatar),
                            contentDescription = "Player Likeness",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "极速挑战者",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "级别：传奇大师跑酷官",
                            fontSize = 12.sp,
                            color = Color(0xFFC7BCE6)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val totalCoins = highScores.values.sumOf { it.maxCoins }
                        Text(
                            text = "累计收集金币：★ $totalCoins",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFF176)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "第一步：选择关卡难度",
                color = Color(0xFFAAA5C9),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start).padding(start = 12.dp, bottom = 4.dp)
            )

            // Scrollable Level selection
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    LevelSelectionCard(
                        levelId = 1,
                        name = "【第一关】 极绿草场冲刺 (简单)",
                        desc = "清新的河畔森林，避开简单路障金币成排，新手必玩！",
                        highScore = highScores[1]?.highScore ?: 0,
                        maxCoins = highScores[1]?.maxCoins ?: 0,
                        isSelected = selectedLevel == 1,
                        badgeColor = Color(0xFF388E3C),
                        badgeText = "EASY",
                        onClick = { onLevelSelect(1) }
                    )
                }
                item {
                    LevelSelectionCard(
                        levelId = 2,
                        name = "【第二关】 赛博霓虹都市 (普通)",
                        desc = "废土夜幕下，粒子极速激光屏障逼近，极速切换跑道！",
                        highScore = highScores[2]?.highScore ?: 0,
                        maxCoins = highScores[2]?.maxCoins ?: 0,
                        isSelected = selectedLevel == 2,
                        badgeColor = Color(0xFF0288D1),
                        badgeText = "MEDIUM",
                        onClick = { onLevelSelect(2) }
                    )
                }
                item {
                    LevelSelectionCard(
                        levelId = 3,
                        name = "【第三关】 熔岩禁区深渊 (极难)",
                        desc = "炽热的核心熔炉，刺骨尖刺熔岩爆发，挑战反应极限！",
                        highScore = highScores[3]?.highScore ?: 0,
                        maxCoins = highScores[3]?.maxCoins ?: 0,
                        isSelected = selectedLevel == 3,
                        badgeColor = Color(0xFFD32F2F),
                        badgeText = "HARD",
                        onClick = { onLevelSelect(3) }
                    )
                }
                item {
                    LevelSelectionCard(
                        levelId = 4,
                        name = "【测试狂热】 极限多维空间 (无尽)",
                        desc = "极限大速度，三大场景无缝滚动切换，无尽漫游挑战最高分！",
                        highScore = highScores[4]?.highScore ?: 0,
                        maxCoins = highScores[4]?.maxCoins ?: 0,
                        isSelected = selectedLevel == 4,
                        badgeColor = Color(0xFF7B1FA2),
                        badgeText = "INFINITE",
                        onClick = { onLevelSelect(4) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Start Game Trigger Button
            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(16.dp, RoundedCornerShape(14.dp))
                    .testTag("start_game_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FFCC),
                    contentColor = Color(0xFF0A0D14)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play Icon")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "开始挑战 (进入3D跑道)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun LevelSelectionCard(
    levelId: Int,
    name: String,
    desc: String,
    highScore: Int,
    maxCoins: Int,
    isSelected: Boolean,
    badgeColor: Color,
    badgeText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clickable { onClick() }
            .testTag("level_card_$levelId"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E1B4B) else Color(0xFF0F172A)
        ),
        border = BorderStroke(
            1.5.dp,
            if (isSelected) Color(0xFF4F46E5) else Color(0xFF334155)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Style Banner tag badge
                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color(0xFF00FFCC) else Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = desc,
                fontSize = 11.sp,
                color = Color(0xFFB4ACCE),
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "最高得分：🏆 $highScore",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "最大金币：★ $maxCoins",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFCC00)
                )
            }
        }
    }
}

@Composable
fun GameHUDOverlay(
    score: Int,
    coins: Int,
    distance: Float,
    config: LevelConfig,
    onPause: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Stats Board Block
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score Block
                Column(horizontalAlignment = Alignment.Start) {
                    Row(
                        modifier = Modifier
                            .background(Color(0x66000000), RoundedCornerShape(24.dp))
                            .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(24.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFBBF24))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format("%,d", score),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SCORE",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Coins Block
                Column(horizontalAlignment = Alignment.Start) {
                    Row(
                        modifier = Modifier
                            .background(Color(0x66000000), RoundedCornerShape(24.dp))
                            .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(24.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "★",
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$coins",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "COINS",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Distance Block
                Column(horizontalAlignment = Alignment.Start) {
                    Row(
                        modifier = Modifier
                            .background(Color(0x66000000), RoundedCornerShape(24.dp))
                            .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(24.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🏃",
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${distance.toInt()}m",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "DIST",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Right side indicators (Level details + Pause)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Dot indicators representing Level ID (1-3)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .size(width = 24.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (index < config.id) Color(0xFF10B981) else Color(0xFF334155))
                            )
                        }
                    }

                    // Pause toggle Key
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0x66000000), CircleShape)
                            .border(1.dp, Color(0x1BFFFFFF), CircleShape)
                            .testTag("pause_game_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Pause Grid",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Level Badge
                Box(
                    modifier = Modifier
                        .background(Color(0xE64F46E5), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF6366F1), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LVL 0${config.id}: ${config.title.uppercase()}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Top level progress bar if level has targeted trackLength!
        if (config.trackLength > 0f) {
            val progress = (distance / config.trackLength).coerceIn(0f, 1f)
            Row(
                modifier = Modifier
                    .fillOffsetXScaleCenter(0.6f)
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "START",
                    fontSize = 9.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                LinearProgressIndicator(
                    progress = progress,
                    color = Color(0xFF00FFCC),
                    trackColor = Color(0x40000000),
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FINISH",
                    fontSize = 9.sp,
                    color = Color(0xFFFF2D85),
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Endless Marathon mode dynamic warning badge
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
                    .background(Color(0xFFFF2D85), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "SPEEDING RUNNER - NO CAPS",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Custom layout spacer function
fun Modifier.fillOffsetXScaleCenter(percentage: Float): Modifier = this.fillMaxWidth(percentage)

@Composable
fun GameVirtualButtons(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onJump: () -> Unit,
    onSlide: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top fade shadow (20.dp height) from transparent to deep slate-950
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF020617))
                        )
                    )
            )
            
            // Core buttons dock mimicking HTML
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020617))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left group: Lane shifts (Slate styles)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Shift Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier
                                .size(70.dp)
                                .clickable { onLeft() }
                                .testTag("move_left_button"),
                            shape = CircleShape,
                            color = Color(0xFF1E293B), // Slate 800
                            border = BorderStroke(3.dp, Color(0xFF334155)) // Slate 700
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "←",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1) // Slate 300
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "LEFT",
                            color = Color(0xFF64748B), // Slate 500
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                    }

                    // Right Shift Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier
                                .size(70.dp)
                                .clickable { onRight() }
                                .testTag("move_right_button"),
                            shape = CircleShape,
                            color = Color(0xFF1E293B),
                            border = BorderStroke(3.dp, Color(0xFF334155))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "→",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "RIGHT",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                // Middle area: Difficulty/Level dots indicator as seen in HTML
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DIFFICULTY",
                        color = Color(0xFF64748B),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(width = 3.dp, height = 10.dp).clip(CircleShape).background(Color(0xFF6366F1)))
                        Box(modifier = Modifier.size(width = 3.dp, height = 10.dp).clip(CircleShape).background(Color(0xFF6366F1)))
                        Box(modifier = Modifier.size(width = 3.dp, height = 10.dp).clip(CircleShape).background(Color(0xFF6366F1)))
                        Box(modifier = Modifier.size(width = 3.dp, height = 10.dp).clip(CircleShape).background(Color(0xFFF43F5E)))
                        Box(modifier = Modifier.size(width = 3.dp, height = 10.dp).clip(CircleShape).background(Color(0xFF334155)))
                    }
                }

                // Right group: Action Triggers (Jump and Slide)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Slide Action Button (Low style slate-800)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier
                                .size(70.dp)
                                .clickable { onSlide() }
                                .testTag("slide_button"),
                            shape = CircleShape,
                            color = Color(0xFF1E293B),
                            border = BorderStroke(3.dp, Color(0xFF334155))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Horizontal pill
                                Box(
                                    modifier = Modifier
                                        .size(width = 24.dp, height = 8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF94A3B8))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SLIDE",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                    }

                    // Jump Action Button (High style vibrant indigo-600)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier
                                .size(70.dp)
                                .clickable { onJump() }
                                .testTag("jump_button"),
                            shape = CircleShape,
                            color = Color(0xFF4F46E5), // Indigo 600
                            border = BorderStroke(3.dp, Color(0xFF6366F1)) // Indigo 500
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Vertical unfilled pill with white/85 border
                                Box(
                                    modifier = Modifier
                                        .size(width = 14.dp, height = 24.dp)
                                        .border(2.5.dp, Color(0xD9FFFFFF), CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "JUMP",
                            color = Color(0xFF818CF8), // Indigo 400
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GamePauseOverlay(
    onCancel: () -> Unit,
    onQuit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xC407060A)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(290.dp)
                .border(1.5.dp, Color(0xFF00FFCC), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13101E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "游戏已暂停",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFF00FFCC), CircleShape)
                        .background(Color(0xFF130F2C)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_player_avatar),
                        contentDescription = "Player Likeness",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "是否继续挑战？避开前方的障碍并累积金币，不断创造你的新纪录！",
                    fontSize = 13.sp,
                    color = Color(0xFFC7BCE6),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().testTag("resume_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black)
                ) {
                    Text("继续狂飙", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onQuit,
                    modifier = Modifier.fillMaxWidth().testTag("quit_button"),
                    border = BorderStroke(1.dp, Color(0xFFFFF176)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFF176))
                ) {
                    Text("返回菜单")
                }
            }
        }
    }
}

@Composable
fun GameOverOverlay(
    score: Int,
    coins: Int,
    isNewHighScore: Boolean,
    onResume: () -> Unit,
    onQuit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD90E0B19)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(310.dp)
                .border(2.dp, Color(0xFFFF2D85), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A132C)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "核心机能碰撞 C R A S H",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF2D85)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "挑战未完成",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, Color(0xFFFF2D85), CircleShape)
                        .background(Color(0xFF1E152F)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_player_avatar),
                        contentDescription = "Player Likeness",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (isNewHighScore) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFD54F), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "👑 新纪录诞生！",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("本次成绩", fontSize = 11.sp, color = Color(0xFFC7BCE6))
                        Text("$score", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("收集金币", fontSize = 11.sp, color = Color(0xFFC7BCE6))
                        Text("$coins", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth().testTag("retry_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D85), contentColor = Color.White)
                ) {
                    Text("重新挑战 (再来一次)", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onQuit,
                    modifier = Modifier.fillMaxWidth().testTag("back_to_menu_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3146), contentColor = Color.White)
                ) {
                    Text("返回关卡大厅", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GameVictoryOverlay(
    score: Int,
    coins: Int,
    isNewHighScore: Boolean,
    onResume: () -> Unit,
    onQuit: () -> Unit
) {
    // Dynamic shine scaling
    val infiniteTransition = rememberInfiniteTransition(label = "victory_anim")
    val scaleShine by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEA0B1411)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(310.dp)
                .border(2.dp, Color(0xFF00FFCC), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF091C17)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Shiny Crown Icon over custom avatar!
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(68.dp)
                            .shadow(12.dp * scaleShine, CircleShape)
                            .border(2.5.dp, Color(0xFF00FFCC), CircleShape),
                        color = Color(0xFF091C17),
                        shape = CircleShape
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_player_avatar),
                            contentDescription = "Player Likeness",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = "👑",
                        fontSize = 24.sp,
                        modifier = Modifier.offset(y = (-18).dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "V I C T O R Y",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00FFCC)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "关卡完美突破！",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (isNewHighScore) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFD54F), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "👑 刷新全服纪录！",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("总分数", fontSize = 11.sp, color = Color(0xFF81C784))
                        Text("$score", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("通关金币", fontSize = 11.sp, color = Color(0xFF81C784))
                        Text("$coins", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black)
                ) {
                    Text("再刷一遍 (争取最高奖励)", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onQuit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3146), contentColor = Color.White)
                ) {
                    Text("返回关卡大厅", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
