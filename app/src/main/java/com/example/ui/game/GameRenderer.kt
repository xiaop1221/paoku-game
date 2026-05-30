package com.example.ui.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.*
import kotlin.random.Random

@Composable
fun GameRenderer(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Load character avatar as ImageBitmap safely
    val avatarBitmap = remember {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_player_avatar)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    val distance by viewModel.distanceTraveled.collectAsState()
    val obstacles = viewModel.activeObstacles
    val coins = viewModel.activeCoins
    val decorations = viewModel.activeDecorations
    val config = viewModel.currentLevelConfig
    
    val infiniteThemeStage = if (config.levelTheme == LevelTheme.INFINITE_MARATHON) {
        ((distance / 1000).toInt() % 3)
    } else {
        0
    }

    // Dynamic Level Theme Colors
    val activeTheme = if (config.levelTheme == LevelTheme.INFINITE_MARATHON) {
        when (infiniteThemeStage) {
            0 -> LevelTheme.GRASSLAND
            1 -> LevelTheme.CYBER_NEON
            else -> LevelTheme.VOLCANIC_CAVERN
        }
    } else {
        config.levelTheme
    }

    val skyGradientColors = when (activeTheme) {
        LevelTheme.GRASSLAND -> listOf(Color(0xFF4BACF1), Color(0xFFCBEBFF))
        LevelTheme.CYBER_NEON -> listOf(Color(0xFF0F0B24), Color(0xFF38155A), Color(0xFF7c1356))
        LevelTheme.VOLCANIC_CAVERN -> listOf(Color(0xFF140707), Color(0xFF330B0B), Color(0xFF901C08))
        LevelTheme.INFINITE_MARATHON -> listOf(Color(0xFF06141F), Color(0xFF1A5072))
    }

    val groundBaseColor = when (activeTheme) {
        LevelTheme.GRASSLAND -> Color(0xFF8ED241)
        LevelTheme.CYBER_NEON -> Color(0xFF090412)
        LevelTheme.VOLCANIC_CAVERN -> Color(0xFF130909)
        LevelTheme.INFINITE_MARATHON -> Color(0xFF0E131E)
    }

    val roadColor = when (activeTheme) {
        LevelTheme.GRASSLAND -> Color(0xFFD2B48C) // Dusty dirt trail
        LevelTheme.CYBER_NEON -> Color(0xFF120A24) // Deep neon black asphalt
        LevelTheme.VOLCANIC_CAVERN -> Color(0xFF2B2020) // Obsidian dark stone
        LevelTheme.INFINITE_MARATHON -> Color(0xFF192233)
    }

    // Gesture detection variables
    var dragAccumulatedX by remember { mutableStateOf(0f) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }
    val gestureThreshold = 75f // minimum pixels to trigger swipe action

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragAccumulatedX = 0f
                        dragAccumulatedY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulatedX += dragAmount.x
                        dragAccumulatedY += dragAmount.y
                    },
                    onDragEnd = {
                        val absX = abs(dragAccumulatedX)
                        val absY = abs(dragAccumulatedY)
                        
                        if (absX > absY && absX > gestureThreshold) {
                            if (dragAccumulatedX > 0) {
                                viewModel.moveRight()
                            } else {
                                viewModel.moveLeft()
                            }
                        } else if (absY > absX && absY > gestureThreshold) {
                            if (dragAccumulatedY > 0) {
                                viewModel.slide()
                            } else {
                                viewModel.jump()
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 3D Vanishing horizon
            val vanishingX = width / 2f
            val vanishingY = height * 0.38f
            val focalLength = 12f
            val maxVisibleY = 140f // Visually converges to horizon, highly optimized to prevent ANRs!

            // Helper for 3D Perspective Projection
            // Projects coordinate (X, Y, Z) into relative pixel offset on Canvas
            fun project(x: Float, y: Float, z: Float): Offset? {
                if (y <= 0.2f) return null // behind camera or too close
                val scale = focalLength / (y + focalLength)
                
                val laneHorizontalWidth = width * 0.26f
                val screenX = vanishingX + (x * laneHorizontalWidth) * scale
                
                val roadGroundOffsetHeight = height * 0.50f
                val screenY = vanishingY + roadGroundOffsetHeight * scale - (z * (height * 0.16f)) * scale
                return Offset(screenX, screenY)
            }

            // 1. Draw Beautiful Sky Backdrop Gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = skyGradientColors,
                    startY = 0f,
                    endY = vanishingY
                ),
                size = Size(width, vanishingY)
            )

            // Dynamic distant moon/cyber emblem
            when (activeTheme) {
                LevelTheme.CYBER_NEON -> {
                    // Giant glowing synthwave sun
                    val sunRadius = width * 0.18f
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFF2D85), Color(0xFFFF9E00)),
                            startY = vanishingY - sunRadius,
                            endY = vanishingY + sunRadius
                        ),
                        radius = sunRadius,
                        center = Offset(vanishingX, vanishingY + 30f),
                        alpha = 0.85f
                    )
                    // Sun horizontal scanline cuts
                    for (i in 0..6) {
                        val cutY = vanishingY - sunRadius + (sunRadius * 2f * (i / 7f))
                        if (cutY < vanishingY) {
                            drawRect(
                                color = skyGradientColors[0],
                                topLeft = Offset(vanishingX - sunRadius, cutY),
                                size = Size(sunRadius * 2f, 10f)
                            )
                        }
                    }
                }
                LevelTheme.VOLCANIC_CAVERN -> {
                    // Lava flame flares rising
                    val lavaPath = Path().apply {
                        moveTo(vanishingX - 100f, vanishingY)
                        quadraticTo(vanishingX - 50f, vanishingY - 80f, vanishingX, vanishingY)
                        quadraticTo(vanishingX + 50f, vanishingY - 100f, vanishingX + 120f, vanishingY)
                    }
                    drawPath(
                        path = lavaPath,
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF5500), Color(0x00FF3300)),
                            center = Offset(vanishingX, vanishingY),
                            radius = 150f
                        )
                    )
                }
                else -> {
                    // Soft fluffy white clouds
                    drawCircle(Color(0x3BFFFFFF), 40f, Offset(vanishingX - 160f, vanishingY - 80f))
                    drawCircle(Color(0x3BFFFFFF), 60f, Offset(vanishingX - 200f, vanishingY - 100f))
                    drawCircle(Color(0x3BFFFFFF), 50f, Offset(vanishingX - 240f, vanishingY - 90f))
                    
                    drawCircle(Color(0x27FFFFFF), 55f, Offset(vanishingX + 220f, vanishingY - 110f))
                    drawCircle(Color(0x27FFFFFF), 40f, Offset(vanishingX + 260f, vanishingY - 90f))
                }
            }

            // 2. Draw Ground Planes
            drawRect(
                color = groundBaseColor,
                topLeft = Offset(0f, vanishingY),
                size = Size(width, height - vanishingY)
            )

            // Side scrolling speed ground textures (Grassland hills, magma channels, cybergrids)
            when (activeTheme) {
                LevelTheme.GRASSLAND -> {
                    // Draw nice dynamic scrolling green fields
                    val terrainY = distance % 60f
                    for (i in 0..3) {
                        val py1 = terrainY + i * 20f
                        val scale = focalLength / (py1 + focalLength)
                        val screenY = vanishingY + (height * 0.50f) * scale
                        drawLine(
                            color = Color(0xFF7EBF35),
                            start = Offset(0f, screenY),
                            end = Offset(width, screenY),
                            strokeWidth = (4f * scale).coerceAtLeast(1f)
                        )
                    }
                }
                LevelTheme.CYBER_NEON -> {
                    // Perspective cyber grid floor
                    val gridSpacing = 12f
                    val scrollY = distance % gridSpacing
                    // Draw vertical rays shooting into the horizon
                    for (xRay in -8..8) {
                        val pNear = project(xRay * 0.8f, 1f, 0f)
                        val pFar = project(xRay * 0.8f, maxVisibleY, 0f)
                        if (pNear != null && pFar != null) {
                            drawLine(
                                color = Color(0x3CFF00E5),
                                start = Offset(pNear.x, pNear.y),
                                end = Offset(pFar.x, pFar.y),
                                strokeWidth = 1f
                            )
                        }
                    }
                    // Horizontal scrolling bands
                    var dyGrid = scrollY
                    while (dyGrid < maxVisibleY) {
                        val pLeft = project(-10f, dyGrid, 0f)
                        val pRight = project(10f, dyGrid, 0f)
                        if (pLeft != null && pRight != null) {
                            drawLine(
                                color = Color(0x33FF00E5),
                                start = pLeft,
                                end = pRight,
                                strokeWidth = (2f * (focalLength / (dyGrid + focalLength))).coerceAtLeast(0.5f)
                            )
                        }
                        dyGrid += gridSpacing
                    }
                }
                LevelTheme.VOLCANIC_CAVERN -> {
                    // Bright glowing lava streams on the side
                    val lavaScroll = distance % 16f
                    val pNearLeftLava = project(-3f, 1f, -0.2f)
                    val pFarLeftLava = project(-1.6f, maxVisibleY, -0.2f)
                    val pNearRightLava = project(3f, 1f, -0.2f)
                    val pFarRightLava = project(1.6f, maxVisibleY, -0.2f)

                    if (pNearLeftLava != null && pFarLeftLava != null) {
                        val lavaPathLeft = Path().apply {
                            moveTo(0f, pNearLeftLava.y)
                            lineTo(pNearLeftLava.x, pNearLeftLava.y)
                            lineTo(pFarLeftLava.x, pFarLeftLava.y)
                            lineTo(0f, pFarLeftLava.y)
                        }
                        drawPath(lavaPathLeft, Color(0xFFFF5C00))
                    }
                    if (pNearRightLava != null && pFarRightLava != null) {
                        val lavaPathRight = Path().apply {
                            moveTo(width, pNearRightLava.y)
                            lineTo(pNearRightLava.x, pNearRightLava.y)
                            lineTo(pFarRightLava.x, pFarRightLava.y)
                            lineTo(width, pFarRightLava.y)
                        }
                        drawPath(lavaPathRight, Color(0xFFFF5C00))
                    }
                }
                else -> {}
            }

            // 3. Draw The 3D Road
            val roadOutlinePath = Path()
            val nearLeft = project(-1.5f, 0.5f, 0f)
            val nearRight = project(1.5f, 0.5f, 0f)
            val farRight = project(1.5f, maxVisibleY, 0f)
            val farLeft = project(-1.5f, maxVisibleY, 0f)

            if (nearLeft != null && nearRight != null && farRight != null && farLeft != null) {
                roadOutlinePath.moveTo(nearLeft.x, nearLeft.y)
                roadOutlinePath.lineTo(nearRight.x, nearRight.y)
                roadOutlinePath.lineTo(farRight.x, farRight.y)
                roadOutlinePath.lineTo(farLeft.x, farLeft.y)
                roadOutlinePath.close()

                drawPath(path = roadOutlinePath, color = roadColor)
                
                // Road side rails/borders in 3D
                val leftBorderPath = Path().apply {
                    val sideLeftNear = project(-1.55f, 0.5f, 0f) ?: nearLeft
                    val sideLeftFar = project(-1.55f, maxVisibleY, 0f) ?: farLeft
                    moveTo(nearLeft.x, nearLeft.y)
                    lineTo(sideLeftNear.x, sideLeftNear.y)
                    lineTo(sideLeftFar.x, sideLeftFar.y)
                    lineTo(farLeft.x, farLeft.y)
                    close()
                }
                val rightBorderPath = Path().apply {
                    val sideRightNear = project(1.55f, 0.5f, 0f) ?: nearRight
                    val sideRightFar = project(1.55f, maxVisibleY, 0f) ?: farRight
                    moveTo(nearRight.x, nearRight.y)
                    lineTo(sideRightNear.x, sideRightNear.y)
                    lineTo(sideRightFar.x, sideRightFar.y)
                    lineTo(farRight.x, farRight.y)
                    close()
                }

                val borderHue = when (activeTheme) {
                    LevelTheme.GRASSLAND -> Color(0xFFC29B63)
                    LevelTheme.CYBER_NEON -> Color(0xFF00FFCC)
                    LevelTheme.VOLCANIC_CAVERN -> Color(0xFFFFCC00)
                    LevelTheme.INFINITE_MARATHON -> Color(0xFF00D2FF)
                }
                drawPath(leftBorderPath, borderHue)
                drawPath(rightBorderPath, borderHue)
            }

            // Lane Divider Dashes
            val dashPeriod = 8f
            val scrollOffset = distance % dashPeriod
            val laneWidth = 1.0f

            for (l in listOf(-0.5f, 0.5f)) {
                var dy = scrollOffset
                while (dy < maxVisibleY) {
                    // Dash segment from dy to dy + 3
                    val pNearDash = project(l, dy, 0f)
                    val pFarDash = project(l, dy + 2.8f, 0f)
                    if (pNearDash != null && pFarDash != null) {
                        drawLine(
                            color = when (activeTheme) {
                                LevelTheme.GRASSLAND -> Color(0xCAFFFFFF)
                                LevelTheme.CYBER_NEON -> Color(0xE8FF00AE)
                                LevelTheme.VOLCANIC_CAVERN -> Color(0xBBFF5500)
                                LevelTheme.INFINITE_MARATHON -> Color(0xCA00DEFF)
                            },
                            start = pNearDash,
                            end = pFarDash,
                            strokeWidth = (4f * (focalLength / (dy + focalLength))).coerceAtLeast(1f)
                        )
                    }
                    dy += dashPeriod
                }
            }

            // Finish banner line if level has specific trackLength
            if (config.trackLength > 0f) {
                val relVictoryY = config.trackLength - distance
                if (relVictoryY in 0f..maxVisibleY) {
                    val bannerL = project(-1.5f, relVictoryY, 0f)
                    val bannerR = project(1.5f, relVictoryY, 0f)
                    val bannerTL = project(-1.5f, relVictoryY, 3.2f)
                    val bannerTR = project(1.5f, relVictoryY, 3.2f)

                    if (bannerL != null && bannerR != null && bannerTL != null && bannerTR != null) {
                        // Drawing the dual poles on left/right side
                        drawLine(Color(0xFF888888), bannerL, bannerTL, strokeWidth = 5f)
                        drawLine(Color(0xFF888888), bannerR, bannerTR, strokeWidth = 5f)

                        // Draw Ribbon
                        val ribbonPath = Path().apply {
                            moveTo(bannerTL.x, bannerTL.y)
                            lineTo(bannerTR.x, bannerTR.y)
                            val bannerTR_bottom = project(1.5f, relVictoryY, 2.4f) ?: bannerR
                            val bannerTL_bottom = project(-1.5f, relVictoryY, 2.4f) ?: bannerL
                            lineTo(bannerTR_bottom.x, bannerTR_bottom.y)
                            lineTo(bannerTL_bottom.x, bannerTL_bottom.y)
                            close()
                        }
                        drawPath(ribbonPath, Color(0xFFD32F2F))

                        // Chevron stripes on victory ribbon
                        val scale = focalLength / (relVictoryY + focalLength)
                        val textPos = project(0.0f, relVictoryY, 2.8f)
                        if (textPos != null) {
                            drawCircle(Color.White, 15f * scale, textPos)
                            // Draw an inner small checker
                        }
                    }
                }
            }

            // 4. Draw Scenic Decorations (Trees, buildings, rocks...) sorted by depth farthest to nearest
            decorations.sortedByDescending { it.trackY }.forEach { dec ->
                val relY = dec.trackY - distance
                if (relY in 0.1f..maxVisibleY) {
                    val roadEdgeX = 1.5f
                    val posX = if (dec.side < 0) {
                        -roadEdgeX - dec.offsetDistance
                    } else {
                        roadEdgeX + dec.offsetDistance
                    }

                    val pBase = project(posX, relY, 0f)
                    val pTop = project(posX, relY, dec.height)
                    
                    if (pBase != null && pTop != null) {
                        val scale = focalLength / (relY + focalLength)
                        val widthPx = (width * 0.12f) * scale * dec.scale

                        when (dec.type) {
                            DecorationType.TREE -> {
                                // Draw scenic brown trunk and full green leaves
                                drawLine(
                                    color = Color(0xFF5D4037),
                                    start = pBase,
                                    end = pTop,
                                    strokeWidth = 6f * scale
                                )
                                drawCircle(
                                    color = Color(0xFF2E7D32),
                                    radius = widthPx * 0.7f,
                                    center = pTop
                                )
                                drawCircle(
                                    color = Color(0xFF388E3C),
                                    radius = widthPx * 0.5f,
                                    center = Offset(pTop.x - widthPx * 0.2f, pTop.y - widthPx * 0.2f)
                                )
                            }
                            DecorationType.CYBER_BUILDING -> {
                                // Monolithic cyber glass skyscraper
                                val buildPath = Path().apply {
                                    val leftX = pBase.x - widthPx
                                    val rightX = pBase.x + widthPx
                                    moveTo(leftX, pBase.y)
                                    lineTo(leftX, pTop.y)
                                    lineTo(rightX, pTop.y - widthPx * 0.4f)
                                    lineTo(rightX, pBase.y)
                                    close()
                                }
                                drawPath(
                                    buildPath,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF1E1035), Color(0xFF3A1F5D) , Color(0xFF4A2A77))
                                    )
                                )
                                // Glowing neon outline window rows
                                val steps = 4
                                for (row in 0..steps) {
                                    val ratio = row.toFloat() / steps
                                    val lineY = pBase.y + (pTop.y - pBase.y) * ratio
                                    drawLine(
                                        color = Color(0x8D00FFFF),
                                        start = Offset(pBase.x - widthPx * 0.6f, lineY),
                                        end = Offset(pBase.x + widthPx * 0.6f, lineY),
                                        strokeWidth = 1f
                                    )
                                }
                            }
                            DecorationType.LAMP_POST -> {
                                // Minimal cyber stick with light top
                                drawLine(Color(0xFF6B7280), pBase, pTop, strokeWidth = 3f * scale)
                                val lightCenter = Offset(pTop.x + (widthPx * 0.3f) * dec.side, pTop.y)
                                drawLine(Color(0xFF6B7280), pTop, lightCenter, strokeWidth = 3f * scale)
                                drawCircle(Color(0xFF00FFCC), 6f * scale, lightCenter)
                                
                                // Glowing light cone downwards
                                val conePath = Path().apply {
                                    moveTo(lightCenter.x, lightCenter.y)
                                    lineTo(lightCenter.x - widthPx * 0.8f, pBase.y)
                                    lineTo(lightCenter.x + widthPx * 0.8f, pBase.y)
                                    close()
                                }
                                drawPath(
                                    path = conePath,
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0x7300FFCC), Color(0x0000FFCC))
                                    )
                                )
                            }
                            DecorationType.LAVA_ROCK -> {
                                // Spikes jagged obsidian volcanic monolith
                                val rockPath = Path().apply {
                                    moveTo(pBase.x - widthPx * 0.7f, pBase.y)
                                    lineTo(pBase.x, pTop.y)
                                    lineTo(pBase.x + widthPx * 0.7f, pBase.y)
                                    close()
                                }
                                drawPath(rockPath, Color(0xFF2C2222))
                                drawPath(
                                    rockPath,
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFFFF3300), Color(0x00FF3300)),
                                        center = pBase,
                                        radius = widthPx
                                    )
                                )
                            }
                            else -> {
                                drawCircle(Color.LightGray, widthPx * 0.5f, pTop)
                            }
                        }
                    }
                }
            }

            // 5. Draw Golden Coins
            coins.filter { !it.isCollected }.sortedByDescending { it.trackY }.forEach { coin ->
                val relY = coin.trackY - distance
                if (relY in 0.1f..maxVisibleY) {
                    val pCoin = project(coin.lane.toFloat(), relY, coin.z)
                    if (pCoin != null) {
                        val scale = focalLength / (relY + focalLength)
                        val radius = (width * 0.032f) * scale

                        // Calculate spinning rotation angle based on running clocks
                        val spinSpeed = 3f
                        val currentMs = System.currentTimeMillis()
                        val rotRad = ((currentMs / spinSpeed) % 360f) * (PI / 180f)
                        val cosRot = abs(cos(rotRad).toFloat()).coerceIn(0.08f, 1.0f) // maintain a strip thickness

                        // Drop shadowed ellipse on the ground
                        val shadowY = project(coin.lane.toFloat(), relY, 0f)
                        if (shadowY != null) {
                            drawOval(
                                color = Color(0x36000000),
                                topLeft = Offset(shadowY.x - radius, shadowY.y - radius * 0.3f),
                                size = Size(radius * 2f, radius * 0.6f)
                            )
                        }

                        // Drawing Gold Metallic 3D spinning coin
                        // An outer gold ellipse with variable width
                        drawOval(
                            color = Color(0xFFFFB300),
                            topLeft = Offset(pCoin.x - radius * cosRot, pCoin.y - radius),
                            size = Size(radius * 2f * cosRot, radius * 2f)
                        )
                        // Inner circle shiny rim
                        drawOval(
                            color = Color(0xFFFFE082),
                            topLeft = Offset(pCoin.x - radius * 0.65f * cosRot, pCoin.y - radius * 0.65f),
                            size = Size(radius * 1.3f * cosRot, radius * 1.3f)
                        )
                        // Center dark star logo
                        drawOval(
                            color = Color(0xFFE65100),
                            topLeft = Offset(pCoin.x - radius * 0.25f * cosRot, pCoin.y - radius * 0.25f),
                            size = Size(radius * 0.5f * cosRot, radius * 0.5f)
                        )
                        
                        // Glowing golden halo for cyberpunk vibes
                        if (activeTheme == LevelTheme.CYBER_NEON) {
                            drawCircle(
                                color = Color(0x2EFFD214),
                                radius = radius * 1.8f,
                                center = pCoin
                            )
                        }
                    }
                }
            }

            // 6. Draw 3D Obstacles
            obstacles.filter { !it.isHit }.sortedByDescending { it.trackY }.forEach { obs ->
                val relY = obs.trackY - distance
                val obsThicknessY = 1.3f

                if (relY in (-obsThicknessY)..maxVisibleY) {
                    val scaleNear = focalLength / ((relY).coerceAtLeast(0.1f) + focalLength)
                    val w = obs.width
                    val h = obs.height
                    
                    // Front Face Boundaries (at relY)
                    val pBottomLeftNear = project(obs.lane - w/2f, relY, 0f)
                    val pBottomRightNear = project(obs.lane + w/2f, relY, 0f)
                    val pTopLeftNear = project(obs.lane - w/2f, relY, h)
                    val pTopRightNear = project(obs.lane + w/2f, relY, h)

                    // Back Face Boundaries (at relY + obsThicknessY)
                    val pBottomLeftFar = project(obs.lane - w/2f, relY + obsThicknessY, 0f)
                    val pBottomRightFar = project(obs.lane + w/2f, relY + obsThicknessY, 0f)
                    val pTopLeftFar = project(obs.lane - w/2f, relY + obsThicknessY, h)
                    val pTopRightFar = project(obs.lane + w/2f, relY + obsThicknessY, h)

                    if (pBottomLeftNear != null && pBottomRightNear != null && pTopLeftNear != null && pTopRightNear != null &&
                        pBottomLeftFar != null && pBottomRightFar != null && pTopLeftFar != null && pTopRightFar != null) {

                        // Color selection based on Obstacle Style and Level
                        val (primaryColor, shadowColor, outlineColor) = when (obs.type) {
                            ObstacleType.HURDLE -> {
                                // Wooden horizontal hurdle with warning stripes
                                Triple(Color(0xFF8D6E63), Color(0xFF5D4037), Color(0xFFFFCC80))
                            }
                            ObstacleType.LASER_WALL -> {
                                // Electric transparent cyber barricade
                                Triple(Color(0x93FF1744), Color(0xCEB00020), Color(0xFFFF1744))
                            }
                            ObstacleType.HIGH_ARCH -> {
                                // Archway bar (slide underneath)
                                if (activeTheme == LevelTheme.GRASSLAND) {
                                    Triple(Color(0xFF4E342E), Color(0xFF3E2723), Color(0xFFD7CCC8))
                                } else {
                                    Triple(Color(0xFF0D47A1), Color(0xFF0A1D37), Color(0xFF29B6F6))
                                }
                            }
                            ObstacleType.MAGMA_SPIKE -> {
                                // Sharp Volcanic spikes
                                Triple(Color(0xFF1E1414), Color(0xFF0F0B0B), Color(0xFFFF3300))
                            }
                        }

                        // Drawing 3D block polygons
                        when (obs.type) {
                            ObstacleType.HURDLE -> {
                                // Draw wood feet left pillar and right pillar
                                val pillarW = (width * 0.015f) * scaleNear
                                drawRect(Color(0xFF4E342E), Offset(pBottomLeftNear.x, pTopLeftNear.y), Size(pillarW, pBottomLeftNear.y - pTopLeftNear.y))
                                drawRect(Color(0xFF4E342E), Offset(pBottomRightNear.x - pillarW, pTopRightNear.y), Size(pillarW, pBottomRightNear.y - pTopRightNear.y))

                                // Draw horizontal main bar with 3D depth
                                val frontBarPath = Path().apply {
                                    moveTo(pBottomLeftNear.x, pTopLeftNear.y + h * (height * 0.05f) * scaleNear)
                                    lineTo(pBottomRightNear.x, pTopRightNear.y + h * (height * 0.05f) * scaleNear)
                                    lineTo(pBottomRightNear.x, pTopRightNear.y + h * (height * 0.13f) * scaleNear)
                                    lineTo(pBottomLeftNear.x, pTopLeftNear.y + h * (height * 0.13f) * scaleNear)
                                    close()
                                }
                                val topBarPath = Path().apply {
                                    moveTo(pBottomLeftNear.x, pTopLeftNear.y + h * (height * 0.05f) * scaleNear)
                                    lineTo(pBottomRightNear.x, pTopRightNear.y + h * (height * 0.05f) * scaleNear)
                                    val topBarFarR = project(obs.lane + w/2f, relY + obsThicknessY, h * 0.8f) ?: pTopRightFar
                                    val topBarFarL = project(obs.lane - w/2f, relY + obsThicknessY, h * 0.8f) ?: pTopLeftFar
                                    lineTo(topBarFarR.x, topBarFarR.y)
                                    lineTo(topBarFarL.x, topBarFarL.y)
                                    close()
                                }
                                
                                drawPath(topBarPath, shadowColor)
                                drawPath(frontBarPath, primaryColor)

                                // Draw yellow / black diagonal warning stripes on the hurdle
                                clipPath(frontBarPath) {
                                    for (stripe in 0..12) {
                                        val startX = pBottomLeftNear.x + (pBottomRightNear.x - pBottomLeftNear.x) * (stripe / 12f)
                                        val stripeW = 12f * scaleNear
                                        val stripePath = Path().apply {
                                            moveTo(startX, pBottomLeftNear.y)
                                            lineTo(startX + stripeW, pBottomLeftNear.y)
                                            lineTo(startX + stripeW - 20f * scaleNear, pTopLeftNear.y)
                                            lineTo(startX - 20f * scaleNear, pTopLeftNear.y)
                                            close()
                                        }
                                        drawPath(stripePath, Color(0xFFFBC02D))
                                    }
                                }
                            }
                            ObstacleType.LASER_WALL -> {
                                // Draw glowing cyber neon pillars on the edges
                                val pillarW = 16f * scaleNear
                                val leftPillarFront = Rect(pTopLeftNear.x - pillarW/2f, pTopLeftNear.y, pTopLeftNear.x + pillarW/2f, pBottomLeftNear.y)
                                val rightPillarFront = Rect(pTopRightNear.x - pillarW/2f, pTopRightNear.y, pTopRightNear.x + pillarW/2f, pBottomRightNear.y)
                                
                                drawRect(shadowColor, leftPillarFront.topLeft, leftPillarFront.size)
                                drawRect(shadowColor, rightPillarFront.topLeft, rightPillarFront.size)

                                // Draw glowing semi-transparent laser grid screen in between
                                val laserScreen = Path().apply {
                                    moveTo(pTopLeftNear.x, pTopLeftNear.y)
                                    lineTo(pTopRightNear.x, pTopRightNear.y)
                                    lineTo(pBottomRightNear.x, pBottomRightNear.y)
                                    lineTo(pBottomLeftNear.x, pBottomLeftNear.y)
                                    close()
                                }
                                drawPath(laserScreen, primaryColor, alpha = 0.55f)

                                // Laser grid horizontal scan lines
                                val scans = 6
                                for (s in 1..scans) {
                                    val r = s.toFloat() / (scans + 1)
                                    val leftY = pTopLeftNear.y + (pBottomLeftNear.y - pTopLeftNear.y) * r
                                    val rightY = pTopRightNear.y + (pBottomRightNear.y - pTopRightNear.y) * r
                                    drawLine(
                                        color = outlineColor,
                                        start = Offset(pTopLeftNear.x, leftY),
                                        end = Offset(pTopRightNear.x, rightY),
                                        strokeWidth = 4f * scaleNear,
                                        alpha = 0.85f
                                    )
                                }
                            }
                            ObstacleType.HIGH_ARCH -> {
                                // High clearance tunnel arch (Hanging bar, we slide underneath)
                                // Top block from Z = 2.0 to 3.0
                                val archUnderHeightY = 1.3f // space to slide (height > 1.3 is solid)

                                val pArchBottomLeftNear = project(obs.lane - w/2f, relY, archUnderHeightY) ?: pBottomLeftNear
                                val pArchBottomRightNear = project(obs.lane + w/2f, relY, archUnderHeightY) ?: pBottomRightNear

                                // Draw solid side pillar structures
                                val pillarWNear = (width * 0.035f) * scaleNear
                                drawLine(Color.LightGray, pBottomLeftNear, pArchBottomLeftNear, strokeWidth = pillarWNear)
                                drawLine(Color.LightGray, pBottomRightNear, pArchBottomRightNear, strokeWidth = pillarWNear)

                                // Draw hanging block with 3D depth
                                val archTopPath = Path().apply {
                                    moveTo(pArchBottomLeftNear.x, pArchBottomLeftNear.y)
                                    lineTo(pArchBottomRightNear.x, pArchBottomRightNear.y)
                                    lineTo(pTopRightNear.x, pTopRightNear.y)
                                    lineTo(pTopLeftNear.x, pTopLeftNear.y)
                                    close()
                                }
                                drawPath(archTopPath, primaryColor)
                                
                                val archDepthPath = Path().apply {
                                    val pArchBottomLeftFar = project(obs.lane - w/2f, relY + obsThicknessY, archUnderHeightY) ?: pBottomLeftFar
                                    moveTo(pArchBottomLeftNear.x, pArchBottomLeftNear.y)
                                    lineTo(pArchBottomLeftFar.x, pArchBottomLeftFar.y)
                                    val pTopLeftFarCustom = project(obs.lane - w/2f, relY + obsThicknessY, h) ?: pTopLeftFar
                                    lineTo(pTopLeftFarCustom.x, pTopLeftFarCustom.y)
                                    lineTo(pTopLeftNear.x, pTopLeftNear.y)
                                    close()
                                }
                                drawPath(archDepthPath, shadowColor)

                                // Draw glowing yellow indicators
                                drawCircle(Color.Yellow, 6f * scaleNear, pArchBottomLeftNear)
                                drawCircle(Color.Yellow, 6f * scaleNear, pArchBottomRightNear)
                            }
                            ObstacleType.MAGMA_SPIKE -> {
                                // Draws sharp triangle-based magma spikes in the center of lane
                                val spikePathNearFront = Path().apply {
                                    moveTo(pBottomLeftNear.x, pBottomLeftNear.y)
                                    lineTo(pBottomRightNear.x, pBottomRightNear.y)
                                    lineTo((pTopLeftNear.x + pTopRightNear.x) / 2f, pTopLeftNear.y)
                                    close()
                                }
                                drawPath(spikePathNearFront, primaryColor)

                                // Shading the back extrusion side for 3D depth
                                val spikePath3DExtru = Path().apply {
                                    val pPeakNear = Offset((pTopLeftNear.x + pTopRightNear.x) / 2f, pTopLeftNear.y)
                                    val pPeakFar = Offset((pTopLeftFar.x + pTopRightFar.x) / 2f, pTopLeftFar.y)
                                    moveTo(pPeakNear.x, pPeakNear.y)
                                    lineTo(pPeakFar.x, pPeakFar.y)
                                    lineTo(pBottomRightFar.x, pBottomRightFar.y)
                                    lineTo(pBottomRightNear.x, pBottomRightNear.y)
                                    close()
                                }
                                drawPath(spikePath3DExtru, shadowColor)

                                // Hot magma cracks outline glowing
                                drawLine(
                                    color = Color(0xFFFF5D00),
                                    start = pBottomLeftNear,
                                    end = Offset((pTopLeftNear.x + pTopRightNear.x) / 2f, pTopLeftNear.y),
                                    strokeWidth = 4f * scaleNear
                                )
                            }
                        }
                    }
                }
            }

            // 7. Draw Player Model in 3D (Always in viewport at constant forward distance y = 11f relative)
            val isInvincibleFlicker = viewModel.playerInvincibilityTimer > 0 && 
                                     (viewModel.playerInvincibilityTimer % 6) < 3

            if (!isInvincibleFlicker) {
                val pY = 11f
                val playerVisualOffsetSmooth = viewModel.playerVisualLaneOffset
                val pZ = viewModel.playerZ
                val isSlide = viewModel.isSliding

                // Basic body scaling coordinates in virtual physical units
                val playerH = if (isSlide) 0.62f else 1.85f
                val playerW = 1.05f

                val pFootL = project(playerVisualOffsetSmooth - 0.28f, pY, pZ)
                val pFootR = project(playerVisualOffsetSmooth + 0.28f, pY, pZ)
                val pPelvis = project(playerVisualOffsetSmooth, pY, pZ + playerH * 0.4f)
                val pShoulders = project(playerVisualOffsetSmooth, pY, pZ + playerH * 0.72f)
                val pHeadTop = project(playerVisualOffsetSmooth, pY, pZ + playerH)

                val scale = focalLength / (pY + focalLength)
                val sizeBody = (width * 0.15f) * scale

                // Dynamic walking arm/leg oscillation based on running distance
                val runCycle = distance * 0.48f
                val runSwing = sin(runCycle)
                val armSwing = cos(runCycle)

                if (pFootL != null && pFootR != null && pPelvis != null && pShoulders != null && pHeadTop != null) {
                    
                    // Draw player shadow on the track
                    val playerGroundPos = project(playerVisualOffsetSmooth, pY, 0f)
                    if (playerGroundPos != null) {
                        drawOval(
                            color = Color(0x5E000000),
                            topLeft = Offset(playerGroundPos.x - sizeBody * 0.6f, playerGroundPos.y - sizeBody * 0.25f),
                            size = Size(sizeBody * 1.2f, sizeBody * 0.5f)
                        )
                    }

                    // A. Draw running legs (Skeletal lines/rectangles)
                    val legL_knee = Offset(
                        pFootL.x + (if (pZ > 0.1f) -10f else runSwing * 22f) * scale,
                        (pFootL.y + pPelvis.y) / 2f - (if (runSwing > 0) 10f else 0f) * scale
                    )
                    val legR_knee = Offset(
                        pFootR.x + (if (pZ > 0.1f) 10f else -runSwing * 22f) * scale,
                        (pFootR.y + pPelvis.y) / 2f - (if (runSwing < 0) 10f else 0f) * scale
                    )

                    // Draw left leg (darker/behind shading)
                    drawLine(Color(0xFF2C3E50), pPelvis, legL_knee, strokeWidth = 9.dp.toPx() * scale, cap = StrokeCap.Round)
                    drawLine(Color(0xFF2C3E50), legL_knee, pFootL, strokeWidth = 7.dp.toPx() * scale, cap = StrokeCap.Round)

                    // Draw right leg (front shading)
                    drawLine(Color(0xFF34495E), pPelvis, legR_knee, strokeWidth = 9.dp.toPx() * scale, cap = StrokeCap.Round)
                    drawLine(Color(0xFF34495E), legR_knee, pFootR, strokeWidth = 7.dp.toPx() * scale, cap = StrokeCap.Round)

                    // B. Draw Torso / Body (3D blocky sweatshirt hoodie styling matching user description!)
                    // Draw a cute grey-blue box in perspective representing a warm hoodie
                    val hoodieColor = Color(0xFF5A728E) // Steel Grey Blue
                    val hoodieShadow = Color(0xFF42556B)
                    val hoodieRim = Color(0xFF7D9BBF)

                    val bodyPath = Path().apply {
                        val shoulderW = sizeBody * 0.35f
                        val hipW = sizeBody * 0.25f
                        moveTo(pShoulders.x - shoulderW, pShoulders.y)
                        lineTo(pShoulders.x + shoulderW, pShoulders.y)
                        lineTo(pPelvis.x + hipW, pPelvis.y)
                        lineTo(pPelvis.x - hipW, pPelvis.y)
                        close()
                    }
                    drawPath(bodyPath, hoodieColor)

                    // Adding hoodie front pocket
                    val pocketPath = Path().apply {
                        val pokW = sizeBody * 0.15f
                        moveTo(pPelvis.x - pokW, pPelvis.y - sizeBody * 0.1f)
                        lineTo(pPelvis.x + pokW, pPelvis.y - sizeBody * 0.1f)
                        lineTo(pPelvis.x + pokW * 0.7f, pPelvis.y - sizeBody * 0.22f)
                        lineTo(pPelvis.x - pokW * 0.7f, pPelvis.y - sizeBody * 0.22f)
                        close()
                    }
                    drawPath(pocketPath, hoodieShadow)

                    // C. Draw swinging Arms
                    val handL = Offset(pShoulders.x - sizeBody * 0.45f, pShoulders.y + sizeBody * 0.25f + armSwing * 15f * scale)
                    val handR = Offset(pShoulders.x + sizeBody * 0.45f, pShoulders.y + sizeBody * 0.25f - armSwing * 15f * scale)
                    
                    drawLine(hoodieShadow, pShoulders, handL, strokeWidth = 12f * scale, cap = StrokeCap.Round)
                    drawLine(hoodieColor, pShoulders, handR, strokeWidth = 12f * scale, cap = StrokeCap.Round)

                    // D. Draw 3D/Isometric Head (Cute square head with face sprite and spiky dark hair!)
                    val headW = sizeBody * 0.48f
                    val headH = sizeBody * 0.48f
                    val headTL = Offset(pShoulders.x - headW/2f, pHeadTop.y)
                    val headRect = Rect(headTL, Size(headW, headH))

                    if (avatarBitmap != null) {
                        // Place user's portrait texture nicely on the headbox
                        // Clean circular clip to blend into block character head
                        clipPath(Path().apply { addOval(headRect) }) {
                            drawImage(
                                image = avatarBitmap,
                                dstOffset = IntOffset(headTL.x.toInt(), headTL.y.toInt()),
                                dstSize = IntSize(headW.toInt(), headH.toInt())
                            )
                        }
                        // Circular sleek border outline
                        drawOval(
                            color = hoodieRim,
                            topLeft = headTL,
                            size = Size(headW, headH),
                            style = Stroke(width = 4f * scale)
                        )
                    } else {
                        // Fallback cute character face drawing
                        drawRoundRect(Color(0xFFFFCC99), headTL, Size(headW, headH), cornerRadius = CornerRadius(8f, 8f))
                        // Draw hair spiky black hair
                        val hairPath = Path().apply {
                            moveTo(headTL.x - 3f, headTL.y + headH * 0.3f)
                            lineTo(headTL.x + headW * 0.2f, headTL.y - 12f * scale)
                            lineTo(headTL.x + headW * 0.5f, headTL.y - 8f * scale)
                            lineTo(headTL.x + headW * 0.8f, headTL.y - 14f * scale)
                            lineTo(headTL.x + headW + 3f, headTL.y + headH * 0.3f)
                            lineTo(headTL.x + headW * 0.8f, headTL.y + headH * 0.15f)
                            lineTo(headTL.x + headW * 0.5f, headTL.y + headH * 0.2f)
                            lineTo(headTL.x + headW * 0.2f, headTL.y + headH * 0.12f)
                            close()
                        }
                        drawPath(hairPath, Color(0xFF1C1C1C)) // Dark black spiky hair
                    }

                    // E. Slide Friction sparks!
                    if (isSlide) {
                        repeat(5) {
                            val sparkDistanceX = (Random.nextFloat() - 0.5f) * sizeBody * 1.5f
                            val sparkDistanceY = Random.nextFloat() * sizeBody * 0.5f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFFF000), Color(0x00FF9000)),
                                    center = Offset(pPelvis.x + sparkDistanceX, pPelvis.y + sparkDistanceY),
                                    radius = 12f * scale
                                ),
                                radius = 8f * scale,
                                center = Offset(pPelvis.x + sparkDistanceX, pPelvis.y + sparkDistanceY)
                            )
                        }
                    }
                }
            }
        }
    }
}
