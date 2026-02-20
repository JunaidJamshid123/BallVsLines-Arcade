package com.example.linevsball

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * GameView is the main game component that handles rendering, game loop, and input.
 * Extends SurfaceView for custom Canvas-based rendering.
 * Enhanced with neon arcade visuals, combo system, particle effects, lives, power-ups!
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    // ═══════════════════════════════════════════════════════════════
    // RETROWAVE NEON COLOR SCHEME (Purple Theme)
    // ═══════════════════════════════════════════════════════════════
    companion object {
        val COLOR_BACKGROUND = Color.rgb(10, 5, 20)        // Deep purple-black
        val COLOR_BALL = Color.rgb(0, 255, 255)            // Neon cyan (#00FFFF)
        val COLOR_LINE = Color.rgb(255, 0, 128)            // Hot pink (#FF0080)
        val COLOR_PADDLE = Color.rgb(180, 100, 255)        // Neon purple (#B464FF)
        val COLOR_COMBO = Color.rgb(255, 0, 255)           // Magenta (#FF00FF)
        val COLOR_NEON_PURPLE = Color.rgb(157, 0, 255)     // Primary neon purple (#9D00FF)
        val COLOR_GRID = Color.rgb(138, 43, 226)           // Blue-violet (#8A2BE2)
    }

    // Game states
    enum class GameState {
        START_SCREEN,
        PLAYING,
        PAUSED,
        GAME_OVER
    }

    // Game thread and control
    private var gameThread: Thread? = null
    private var isRunning: Boolean = false
    private val targetFPS: Long = 60
    private val targetFrameTime: Long = 1000 / targetFPS

    // Screen dimensions
    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f
    
    // Responsive sizing (calculated based on screen)
    private var statusBarHeight: Float = 0f
    private var scaleFactor: Float = 1f
    private var responsiveBallRadius: Float = 25f
    private var responsivePaddleWidth: Float = 200f
    private var responsivePaddleHeight: Float = 25f
    private var responsiveInitialSpeed: Float = 12f

    // Game objects - Multi-ball support
    private val balls: MutableList<Ball> = mutableListOf()
    private lateinit var paddle: Paddle
    private val lines: MutableList<Line> = mutableListOf()
    
    // ═══════════════════════════════════════════════════════════════
    // NEW: POWER-UPS SYSTEM
    // ═══════════════════════════════════════════════════════════════
    private val powerUps: MutableList<PowerUp> = mutableListOf()
    private var activeShield: Boolean = false
    private var shieldTimer: Float = 0f
    private var slowMoActive: Boolean = false
    private var slowMoTimer: Float = 0f
    private var bigPaddleActive: Boolean = false
    private var bigPaddleTimer: Float = 0f
    private var magnetActive: Boolean = false
    private var magnetTimer: Float = 0f
    private var scoreBoostActive: Boolean = false
    private var scoreBoostTimer: Float = 0f
    private var originalPaddleWidth: Float = 200f
    
    // ═══════════════════════════════════════════════════════════════
    // NEW: LIVES SYSTEM
    // ═══════════════════════════════════════════════════════════════
    private var lives: Int = 3
    private val maxLives: Int = 3
    
    // ═══════════════════════════════════════════════════════════════
    // NEW: DIFFICULTY LEVEL SYSTEM
    // ═══════════════════════════════════════════════════════════════
    private var difficultyLevel: Int = 1
    private val maxDifficultyLevel: Int = 10
    private var levelUpAnimationTime: Float = 0f
    private val levelThresholds = listOf(0, 100, 250, 500, 800, 1200, 1800, 2500, 3500, 5000)
    // Milestone rewards - extra life at these scores
    private val lifeRewardMilestones = mutableSetOf(500, 1500, 3000, 5000)
    private var lastMilestoneAwarded: Int = 0
    
    // ═══════════════════════════════════════════════════════════════
    // NEW: STATISTICS TRACKING
    // ═══════════════════════════════════════════════════════════════
    private var totalGamesPlayed: Int = 0
    private var totalLinesDestroyed: Int = 0
    private var totalScoreEarned: Long = 0
    private var bestComboEver: Int = 0
    private var longestSurvivalTime: Long = 0
    private var sessionLinesDestroyed: Int = 0  // Lines destroyed this game
    
    // ═══════════════════════════════════════════════════════════════
    // NEW: HAPTIC FEEDBACK
    // ═══════════════════════════════════════════════════════════════
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    
    // ═══════════════════════════════════════════════════════════════
    // NEW: ACHIEVEMENT SYSTEM
    // ═══════════════════════════════════════════════════════════════
    private lateinit var achievementManager: AchievementManager
    
    // ═══════════════════════════════════════════════════════════════
    // NEW: SCREEN PULSE VFX
    // ═══════════════════════════════════════════════════════════════
    private var screenPulseAlpha: Int = 0
    private var screenPulseColor: Int = COLOR_COMBO
    
    // Visual effects
    private val floatingTexts: MutableList<FloatingText> = mutableListOf()
    private var screenShakeOffset: Float = 0f
    private var screenShakeIntensity: Float = 0f
    private val hitFlashes: MutableList<HitFlash> = mutableListOf()
    private val particles: MutableList<Particle> = mutableListOf()  // Particle system

    // Game state
    private var gameState: GameState = GameState.START_SCREEN
    private var score: Int = 0
    private var bestScore: Int = 0
    
    // ═══════════════════════════════════════════════════════════════
    // COMBO SYSTEM
    // ═══════════════════════════════════════════════════════════════
    private var comboCount: Int = 0              // Lines destroyed without losing ball
    private var comboMultiplier: Int = 1         // Current combo multiplier (1, 2, 3)
    private var comboDisplayTime: Float = 0f    // Time to display combo text
    private val comboDecayTime: Float = 2f      // Combo text display duration
    
    // ═══════════════════════════════════════════════════════════════
    // SCORE ANIMATION
    // ═══════════════════════════════════════════════════════════════
    private var scoreScale: Float = 1f           // Current scale (1.0 normal, 1.2 pop)
    private var scoreScaleVelocity: Float = 0f
    
    // Multi-ball limits
    private val maxBalls: Int = 8

    // Difficulty parameters
    private var baseLineSpeed: Float = 3f
    private var lineSpawnInterval: Long = 2000L  // milliseconds
    private var lastLineSpawnTime: Long = 0L
    private var gameStartTime: Long = 0L
    private val initialBallSpeed: Float = 12f
    private val ballRadius: Float = 25f
    
    // HUD background paint for visibility
    private val hudBackgroundPaint = Paint().apply {
        color = Color.argb(120, 0, 0, 0)  // Semi-transparent black
        isAntiAlias = true
    }

    // SharedPreferences for storing best score and statistics
    private val prefs: SharedPreferences = context.getSharedPreferences("BallVsLines", Context.MODE_PRIVATE)
    
    // Level up paint for visual feedback
    private val levelUpPaint = Paint().apply {
        isAntiAlias = true
        color = Color.rgb(255, 215, 0)  // Gold
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // Paints for drawing UI elements
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val titlePaint = Paint().apply {
        isAntiAlias = true
        color = COLOR_BALL
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(10f, 0f, 0f, COLOR_BALL)
    }

    private val subtitlePaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        alpha = 180
    }
    
    // HUD paint with better styling
    private val hudPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 45f
    }
    
    private val hudValuePaint = Paint().apply {
        isAntiAlias = true
        color = COLOR_BALL
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 55f
        setShadowLayer(6f, 0f, 0f, COLOR_BALL)
    }
    
    // Combo text paint (electric pink with glow)
    private val comboPaint = Paint().apply {
        isAntiAlias = true
        color = COLOR_COMBO
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 50f
        setShadowLayer(15f, 0f, 0f, COLOR_COMBO)
    }
    
    // Background gradient paint
    private val backgroundPaint = Paint()
    
    // Background bitmap
    private var backgroundBitmap: Bitmap? = null
    private var scaledBackgroundBitmap: Bitmap? = null
    
    // Pause button paint
    private val pauseButtonPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        alpha = 180
    }
    
    // Lives heart paint
    private val heartPaint = Paint().apply {
        isAntiAlias = true
        textSize = 35f
    }
    
    // Power-up indicator paint
    private val powerUpIndicatorPaint = Paint().apply {
        isAntiAlias = true
        textSize = 24f
        textAlign = Paint.Align.LEFT
    }
    
    // Screen pulse paint for VFX
    private val screenPulsePaint = Paint().apply {
        isAntiAlias = true
    }

    init {
        // Set up surface holder callback
        holder.addCallback(this)
        
        // Load best score and statistics
        bestScore = prefs.getInt("bestScore", 0)
        totalGamesPlayed = prefs.getInt("totalGamesPlayed", 0)
        totalLinesDestroyed = prefs.getInt("totalLinesDestroyed", 0)
        totalScoreEarned = prefs.getLong("totalScoreEarned", 0L)
        bestComboEver = prefs.getInt("bestComboEver", 0)
        longestSurvivalTime = prefs.getLong("longestSurvivalTime", 0L)
        
        // Load background image from drawable
        backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.backgroundd)
        
        // Initialize achievement manager
        achievementManager = AchievementManager(prefs)
        
        // Make focusable for input
        isFocusable = true
        
        // Enable hardware acceleration for shadow layers
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /**
     * Called when surface is created - initialize game objects
     */
    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
        
        // Calculate responsive sizes based on screen dimensions
        calculateResponsiveSizes()
        
        initializeGameObjects()
        
        // Start game thread
        isRunning = true
        gameThread = Thread(this)
        gameThread?.start()
    }
    
    /**
     * Calculate responsive sizes based on screen dimensions
     * Base design: 1080x1920 (standard 1080p phone)
     */
    private fun calculateResponsiveSizes() {
        val baseWidth = 1080f
        val baseHeight = 1920f
        
        // Calculate scale factor based on screen width
        scaleFactor = screenWidth / baseWidth
        
        // Get status bar height for safe area
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        statusBarHeight = if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId).toFloat()
        } else {
            (24 * resources.displayMetrics.density)  // Fallback: 24dp
        }
        
        // Add extra padding for notch devices
        statusBarHeight += 20f * scaleFactor
        
        // Calculate responsive game element sizes
        responsiveBallRadius = 25f * scaleFactor
        responsivePaddleWidth = 200f * scaleFactor
        responsivePaddleHeight = 25f * scaleFactor
        responsiveInitialSpeed = 12f * scaleFactor
        baseLineSpeed = 3f * scaleFactor
    }

    /**
     * Called when surface dimensions change
     */
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
        
        // Update paddle screen width for boundary checking
        if (::paddle.isInitialized) {
            paddle.screenWidth = screenWidth
        }
    }

    /**
     * Called when surface is destroyed - stop game thread
     */
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        try {
            gameThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * Initialize all game objects to starting state
     */
    private fun initializeGameObjects() {
        // Clear existing balls and create one preview ball
        balls.clear()
        balls.add(Ball(
            x = screenWidth / 2,
            y = screenHeight / 2,
            velocityX = 0f,
            velocityY = 0f,
            radius = responsiveBallRadius,
            baseSpeed = responsiveInitialSpeed
        ))

        // Create paddle near bottom of screen (responsive positioning)
        val paddleBottomMargin = 120f * scaleFactor
        paddle = Paddle(
            x = screenWidth / 2,
            y = screenHeight - paddleBottomMargin,
            width = responsivePaddleWidth,
            height = responsivePaddleHeight,
            screenWidth = screenWidth
        )

        // Clear any existing lines
        lines.clear()
        floatingTexts.clear()
        hitFlashes.clear()
        particles.clear()
    }

    /**
     * Reset game to initial state for new game
     */
    private fun resetGame() {
        score = 0
        baseLineSpeed = 3f * scaleFactor
        lineSpawnInterval = 2000L
        screenShakeIntensity = 0f
        
        // Reset combo system
        comboCount = 0
        comboMultiplier = 1
        comboDisplayTime = 0f
        
        // Reset score animation
        scoreScale = 1f
        scoreScaleVelocity = 0f
        
        // Reset lives
        lives = maxLives
        
        // Reset difficulty level system
        difficultyLevel = 1
        levelUpAnimationTime = 0f
        lastMilestoneAwarded = 0
        
        // Reset session statistics
        sessionLinesDestroyed = 0
        
        // Reset power-ups
        powerUps.clear()
        activeShield = false
        shieldTimer = 0f
        slowMoActive = false
        slowMoTimer = 0f
        bigPaddleActive = false
        bigPaddleTimer = 0f
        magnetActive = false
        magnetTimer = 0f
        scoreBoostActive = false
        scoreBoostTimer = 0f
        originalPaddleWidth = responsivePaddleWidth
        
        // Reset VFX
        screenPulseAlpha = 0
        
        // Reset achievement session tracking
        achievementManager.resetSession()
        
        // Clear and create single ball with random upward direction
        balls.clear()
        val newBall = Ball(
            x = screenWidth / 2,
            y = screenHeight / 2,
            velocityX = 0f,
            velocityY = 0f,
            radius = responsiveBallRadius,
            baseSpeed = responsiveInitialSpeed
        )
        newBall.reset(screenWidth, screenHeight, responsiveInitialSpeed)
        balls.add(newBall)
        
        // Reset paddle with responsive size
        paddle.width = responsivePaddleWidth
        paddle.height = responsivePaddleHeight
        paddle.reset(screenWidth, screenHeight)
        
        // Clear all objects
        lines.clear()
        floatingTexts.clear()
        hitFlashes.clear()
        particles.clear()
        
        // Record game start time
        gameStartTime = System.currentTimeMillis()
        lastLineSpawnTime = gameStartTime
        
        gameState = GameState.PLAYING
    }

    /**
     * Main game loop - runs at ~60 FPS
     */
    override fun run() {
        while (isRunning) {
            val startTime = System.currentTimeMillis()

            // Update game state
            if (gameState == GameState.PLAYING) {
                update()
            }

            // Render frame
            val canvas = holder.lockCanvas()
            if (canvas != null) {
                try {
                    synchronized(holder) {
                        draw(canvas)
                    }
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            // Control frame rate
            val frameTime = System.currentTimeMillis() - startTime
            if (frameTime < targetFrameTime) {
                try {
                    Thread.sleep(targetFrameTime - frameTime)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Update game logic - called every frame during gameplay
     */
    private fun update() {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - gameStartTime

        // Increase difficulty over time
        updateDifficulty(elapsedTime)
        
        // Update screen shake
        updateScreenShake()
        
        // Update floating texts
        updateFloatingTexts()
        
        // Update hit flashes
        updateHitFlashes()
        
        // Update particles
        updateParticles()
        
        // Update score animation
        updateScoreAnimation()
        
        // Update combo display timer
        if (comboDisplayTime > 0) {
            comboDisplayTime -= 1f / 60f  // Decrease by frame time
        }

        // Update all balls
        val ballsToRemove = mutableListOf<Ball>()
        val ballsToAdd = mutableListOf<Ball>()
        
        for (ball in balls) {
            ball.update()
            
            // Handle ball collision with screen edges
            handleScreenCollisions(ball)
            
            // Handle ball collision with paddle
            paddle.handleCollision(ball)
            
            // Check if ball is lost
            if (ball.isLost(screenHeight)) {
                ballsToRemove.add(ball)
            }
        }

        // Spawn new lines periodically
        if (currentTime - lastLineSpawnTime >= lineSpawnInterval) {
            spawnLine()
            lastLineSpawnTime = currentTime
        }

        // Update lines and check collisions with all balls
        for (line in lines) {
            // Apply slow-mo effect to line speed
            val originalSpeed = line.fallSpeed
            if (slowMoActive) {
                line.fallSpeed = originalSpeed * 0.5f
            }
            line.update()
            if (slowMoActive) {
                line.fallSpeed = originalSpeed  // Restore original speed
            }

            // Check collision with each ball (skip if already animating destruction)
            if (!line.isAnimatingDestruction) {
                for (ball in balls) {
                    if (line.handleCollision(ball)) {
                        // Increment combo and line count
                        comboCount++
                        sessionLinesDestroyed++
                        updateComboMultiplier()
                        
                        // Calculate score with combo multiplier and line type bonus
                        val basePoints = line.type.pointValue
                        val boostMultiplier = if (scoreBoostActive) 2 else 1
                        val points = basePoints * comboMultiplier * boostMultiplier
                        score += points
                        
                        // Trigger haptic feedback
                        triggerHaptic(HapticType.LINE_HIT)
                        
                        // Check achievements
                        val isGoldLine = line.type == LineType.GOLD
                        achievementManager.checkAchievements(
                            score = score,
                            comboMultiplier = comboMultiplier,
                            ballCount = balls.size + ballsToAdd.size,
                            survivalTimeSeconds = (currentTime - gameStartTime) / 1000f,
                            lineDestroyed = true,
                            isGoldLine = isGoldLine
                        )
                        
                        // Spawn power-up from POWER lines (or random chance from others)
                        if (line.type == LineType.POWER) {
                            val powerUp = PowerUp.maybeSpawn(line.x, line.y, scaleFactor)
                            powerUp?.let { powerUps.add(it) }
                            // Always spawn from power lines
                            if (powerUp == null) {
                                powerUps.add(PowerUp.spawn(line.x, line.y, PowerUpType.values().random(), scaleFactor))
                            }
                        } else if (kotlin.random.Random.nextFloat() < 0.08f) {
                            // 8% chance from other lines
                            PowerUp.maybeSpawn(line.x, line.y, scaleFactor)?.let { powerUps.add(it) }
                        }
                        
                        // Trigger score animation
                        triggerScoreAnimation()
                        
                        // Add floating score text with combo info
                        val bonusText = when {
                            line.type == LineType.GOLD -> " GOLD!"
                            scoreBoostActive -> " 2X!"
                            else -> ""
                        }
                        val popupText = if (comboMultiplier > 1) "+$points (x$comboMultiplier)$bonusText" else "+$points$bonusText"
                        floatingTexts.add(FloatingText.createScorePopup(ball.x, ball.y - 30, points, comboMultiplier > 1 || isGoldLine))
                        
                        // Screen pulse for gold lines or combos
                        if (isGoldLine || comboMultiplier >= 2) {
                            screenPulseAlpha = if (isGoldLine) 80 else 50
                            screenPulseColor = if (isGoldLine) Color.rgb(255, 215, 0) else COLOR_COMBO
                        }
                        
                        // Add hit flash effect
                        hitFlashes.add(HitFlash(line.x, line.y, line.width))
                        
                        // Spawn particle burst at collision point (more for gold)
                        val particleCount = if (isGoldLine) 12 else 8
                        spawnParticles(line.x, line.y, particleCount, line.type.primaryColor)
                        
                        // Trigger ball scale bounce effect
                        ball.triggerScaleBounce()
                        
                        // Trigger screen shake (subtle 50ms)
                        triggerScreenShake(4f)
                        
                        // Spawn new ball if under limit
                        if (balls.size + ballsToAdd.size < maxBalls) {
                            ballsToAdd.add(ball.spawnCopy())
                        }
                        
                        // Slightly increase ball speed with score
                        if (score % 100 == 0) {
                            balls.forEach { it.increaseSpeed(1.02f) }
                        }
                        
                        break // Each line can only be hit once per frame
                    }
                }
            }

            // Check if line reached bottom - lose a life or GAME OVER
            if (line.hasReachedBottom(screenHeight)) {
                achievementManager.onLineMissed()
                if (activeShield) {
                    // Shield absorbs the hit
                    activeShield = false
                    shieldTimer = 0f
                    triggerHaptic(HapticType.SHIELD_BREAK)
                    screenPulseAlpha = 100
                    screenPulseColor = Color.rgb(0, 200, 255)
                    floatingTexts.add(FloatingText.createScorePopup(screenWidth / 2, screenHeight / 2 - 50f, 0, true))
                    line.startDestructionAnimation()
                } else {
                    lives--
                    triggerHaptic(HapticType.LIFE_LOST)
                    
                    // Show life lost floating text
                    floatingTexts.add(FloatingText.createScorePopup(screenWidth / 2, screenHeight / 2, -1, true))
                    
                    if (lives <= 0) {
                        gameOver()
                        return
                    } else {
                        // Flash screen red
                        screenPulseAlpha = 150
                        screenPulseColor = Color.rgb(255, 50, 50)
                        line.startDestructionAnimation()
                    }
                }
            }
        }

        // Remove fully destroyed lines (after animation completes)
        lines.removeIf { it.isDestroyed }
        
        // Update power-ups
        updatePowerUps()
        
        // Check power-up collection
        checkPowerUpCollision()
        
        // Update power-up timers
        updatePowerUpTimers()
        
        // Update screen pulse VFX
        if (screenPulseAlpha > 0) {
            screenPulseAlpha = (screenPulseAlpha - 3).coerceAtLeast(0)
        }
        
        // Update achievement popups
        achievementManager.updatePopups(1f / 60f)
        
        // Handle lost balls - reset combo and handle lives
        if (ballsToRemove.isNotEmpty()) {
            resetCombo()
            triggerHaptic(HapticType.BALL_LOST)
        }
        
        // Remove lost balls
        balls.removeAll(ballsToRemove)
        
        // Add new balls
        balls.addAll(ballsToAdd)

        // Check if all balls are lost - lose a life or GAME OVER
        if (balls.isEmpty()) {
            lives--
            triggerHaptic(HapticType.LIFE_LOST)
            
            if (lives <= 0) {
                gameOver()
            } else {
                // Respawn a new ball
                val newBall = Ball(
                    x = screenWidth / 2,
                    y = screenHeight / 2,
                    velocityX = 0f,
                    velocityY = 0f,
                    radius = responsiveBallRadius,
                    baseSpeed = responsiveInitialSpeed
                )
                newBall.reset(screenWidth, screenHeight, responsiveInitialSpeed)
                balls.add(newBall)
                
                // Show life lost message
                floatingTexts.add(FloatingText.createScorePopup(screenWidth / 2, screenHeight / 2, -1, true))
                
                // Flash screen red
                screenPulseAlpha = 150
                screenPulseColor = Color.rgb(255, 50, 50)
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // COMBO SYSTEM HELPERS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Update combo multiplier based on combo count
     */
    private fun updateComboMultiplier() {
        val previousMultiplier = comboMultiplier
        comboMultiplier = when {
            comboCount >= 5 -> 3  // 5+ lines = x3
            comboCount >= 3 -> 2  // 3+ lines = x2
            else -> 1
        }
        
        // Show combo text when multiplier increases
        if (comboMultiplier > previousMultiplier) {
            comboDisplayTime = comboDecayTime
            // Add floating combo text
            floatingTexts.add(FloatingText.createComboPopup(screenWidth / 2, screenHeight / 3, comboMultiplier))
        }
    }
    
    /**
     * Reset combo when ball is lost
     */
    private fun resetCombo() {
        comboCount = 0
        comboMultiplier = 1
    }
    
    // ═══════════════════════════════════════════════════════════════
    // HAPTIC FEEDBACK SYSTEM
    // ═══════════════════════════════════════════════════════════════
    
    enum class HapticType {
        LINE_HIT,      // Light tap
        BALL_LOST,     // Medium pulse
        LIFE_LOST,     // Strong pulse  
        SHIELD_BREAK,  // Double tap
        POWER_UP,      // Quick buzz
        GAME_OVER      // Long vibrate
    }
    
    private fun triggerHaptic(type: HapticType) {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (type) {
                    HapticType.LINE_HIT -> VibrationEffect.createOneShot(10, 50)
                    HapticType.BALL_LOST -> VibrationEffect.createOneShot(30, 100)
                    HapticType.LIFE_LOST -> VibrationEffect.createOneShot(80, 200)
                    HapticType.SHIELD_BREAK -> VibrationEffect.createWaveform(longArrayOf(0, 20, 30, 20), -1)
                    HapticType.POWER_UP -> VibrationEffect.createOneShot(15, 80)
                    HapticType.GAME_OVER -> VibrationEffect.createOneShot(200, 255)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (type) {
                    HapticType.LINE_HIT -> vib.vibrate(10)
                    HapticType.BALL_LOST -> vib.vibrate(30)
                    HapticType.LIFE_LOST -> vib.vibrate(80)
                    HapticType.SHIELD_BREAK -> vib.vibrate(longArrayOf(0, 20, 30, 20), -1)
                    HapticType.POWER_UP -> vib.vibrate(15)
                    HapticType.GAME_OVER -> vib.vibrate(200)
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // POWER-UP SYSTEM
    // ═══════════════════════════════════════════════════════════════
    
    private fun updatePowerUps() {
        val speedMultiplier = if (slowMoActive) 0.5f else 1f
        powerUps.forEach { powerUp ->
            powerUp.velocityY = 2.5f * scaleFactor * speedMultiplier
            powerUp.update()
        }
        powerUps.removeIf { it.isOffScreen(screenHeight) }
    }
    
    private fun checkPowerUpCollision() {
        val collectedPowerUps = mutableListOf<PowerUp>()
        
        for (powerUp in powerUps) {
            // Check collision with paddle
            if (powerUp.rect.intersect(paddle.rect)) {
                collectedPowerUps.add(powerUp)
                activatePowerUp(powerUp.type)
                triggerHaptic(HapticType.POWER_UP)
                
                // Spawn celebration particles
                spawnParticles(powerUp.x, powerUp.y, 10, powerUp.type.color)
                
                // Show power-up text
                floatingTexts.add(FloatingText.createScorePopup(
                    powerUp.x, powerUp.y - 20, 0, true
                ))
            }
        }
        
        powerUps.removeAll(collectedPowerUps)
    }
    
    private fun activatePowerUp(type: PowerUpType) {
        when (type) {
            PowerUpType.SHIELD -> {
                activeShield = true
                shieldTimer = type.duration / 1000f
            }
            PowerUpType.MULTI_BALL -> {
                if (balls.size < maxBalls && balls.isNotEmpty()) {
                    balls.add(balls[0].spawnCopy())
                }
            }
            PowerUpType.SLOW_MO -> {
                slowMoActive = true
                slowMoTimer = type.duration / 1000f
            }
            PowerUpType.BIG_PADDLE -> {
                if (!bigPaddleActive) {
                    originalPaddleWidth = paddle.width
                }
                bigPaddleActive = true
                bigPaddleTimer = type.duration / 1000f
                paddle.width = originalPaddleWidth * 1.5f
            }
            PowerUpType.MAGNET -> {
                magnetActive = true
                magnetTimer = type.duration / 1000f
            }
            PowerUpType.SCORE_BOOST -> {
                scoreBoostActive = true
                scoreBoostTimer = type.duration / 1000f
            }
        }
        
        // Screen pulse for power-up collection
        screenPulseAlpha = 60
        screenPulseColor = type.color
    }
    
    /**
     * Trigger a screen pulse VFX with specified color
     */
    private fun triggerScreenPulse(color: Int, intensity: Int = 100) {
        screenPulseAlpha = intensity
        screenPulseColor = color
    }
    
    private fun updatePowerUpTimers() {
        val dt = 1f / 60f
        
        if (shieldTimer > 0) {
            shieldTimer -= dt
            if (shieldTimer <= 0) activeShield = false
        }
        
        if (slowMoTimer > 0) {
            slowMoTimer -= dt
            if (slowMoTimer <= 0) slowMoActive = false
        }
        
        if (bigPaddleTimer > 0) {
            bigPaddleTimer -= dt
            if (bigPaddleTimer <= 0) {
                bigPaddleActive = false
                paddle.width = originalPaddleWidth
            }
        }
        
        if (magnetTimer > 0) {
            magnetTimer -= dt
            if (magnetTimer <= 0) magnetActive = false
        }
        
        if (scoreBoostTimer > 0) {
            scoreBoostTimer -= dt
            if (scoreBoostTimer <= 0) scoreBoostActive = false
        }
        
        // Magnet effect: attract balls to paddle
        if (magnetActive) {
            for (ball in balls) {
                val dx = paddle.x - ball.x
                ball.velocityX += dx * 0.002f
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // SCORE ANIMATION HELPERS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Trigger score pop animation
     */
    private fun triggerScoreAnimation() {
        scoreScale = 1.2f
        scoreScaleVelocity = 0f
    }
    
    /**
     * Update score scale animation (spring back to normal)
     */
    private fun updateScoreAnimation() {
        if (scoreScale != 1f) {
            scoreScaleVelocity += (1f - scoreScale) * 0.2f  // Spring force
            scoreScaleVelocity *= 0.7f  // Damping
            scoreScale += scoreScaleVelocity
            if (kotlin.math.abs(scoreScale - 1f) < 0.01f && kotlin.math.abs(scoreScaleVelocity) < 0.01f) {
                scoreScale = 1f
                scoreScaleVelocity = 0f
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // PARTICLE SYSTEM HELPERS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Spawn particle burst at position with optional count and color
     */
    private fun spawnParticles(x: Float, y: Float, count: Int = 8, color: Int = COLOR_LINE) {
        particles.addAll(Particle.createBurst(x, y, count, color))
    }
    
    /**
     * Update all particles
     */
    private fun updateParticles() {
        val toRemove = mutableListOf<Particle>()
        for (particle in particles) {
            particle.update()
            if (particle.isExpired()) {
                toRemove.add(particle)
            }
        }
        particles.removeAll(toRemove)
    }
    
    /**
     * Draw all particles
     */
    private fun drawParticles(canvas: Canvas) {
        for (particle in particles) {
            particle.draw(canvas)
        }
    }
    
    /**
     * Update screen shake effect
     */
    private fun updateScreenShake() {
        if (screenShakeIntensity > 0) {
            screenShakeOffset = ((Math.random() - 0.5) * screenShakeIntensity * 2).toFloat()
            screenShakeIntensity *= 0.9f // Decay
            if (screenShakeIntensity < 0.5f) {
                screenShakeIntensity = 0f
                screenShakeOffset = 0f
            }
        }
    }
    
    /**
     * Trigger screen shake effect
     */
    private fun triggerScreenShake(intensity: Float) {
        screenShakeIntensity = intensity
    }
    
    /**
     * Update floating text effects
     */
    private fun updateFloatingTexts() {
        val toRemove = mutableListOf<FloatingText>()
        for (text in floatingTexts) {
            text.update()
            if (text.isExpired()) {
                toRemove.add(text)
            }
        }
        floatingTexts.removeAll(toRemove)
    }
    
    /**
     * Update hit flash effects
     */
    private fun updateHitFlashes() {
        val toRemove = mutableListOf<HitFlash>()
        for (flash in hitFlashes) {
            flash.update()
            if (flash.isExpired()) {
                toRemove.add(flash)
            }
        }
        hitFlashes.removeAll(toRemove)
    }

    /**
     * Update difficulty based on score (level system)
     */
    private fun updateDifficulty(elapsedTime: Long) {
        // Determine new difficulty level based on score
        var newLevel = 1
        for (i in levelThresholds.indices.reversed()) {
            if (score >= levelThresholds[i]) {
                newLevel = i + 1
                break
            }
        }
        newLevel = newLevel.coerceAtMost(maxDifficultyLevel)
        
        // Check if we leveled up
        if (newLevel > difficultyLevel) {
            difficultyLevel = newLevel
            levelUpAnimationTime = 2.5f  // Show "LEVEL UP!" for 2.5 seconds
            triggerHaptic(HapticType.LIFE_LOST)  // Strong feedback for level up
            triggerScreenPulse(Color.rgb(255, 215, 0))  // Gold flash
            
            // Add floating text
            floatingTexts.add(FloatingText.createScorePopup(
                screenWidth / 2, screenHeight / 3, "LEVEL $difficultyLevel!"
            ))
        }
        
        // Apply difficulty based on level
        // Level 1: speed=3, spawn=2000ms | Level 10: speed=7.5, spawn=700ms
        val speedMultiplier = 1f + (difficultyLevel - 1) * 0.18f  // 1.0 to 2.5
        val spawnMultiplier = 1f - (difficultyLevel - 1) * 0.065f // 1.0 to 0.35
        
        baseLineSpeed = (3f * speedMultiplier * scaleFactor).coerceAtMost(7.5f * scaleFactor)
        lineSpawnInterval = (2000L * spawnMultiplier).toLong().coerceAtLeast(700L)
        
        // Check for milestone life rewards
        checkMilestoneReward()
        
        // Update level up animation timer
        if (levelUpAnimationTime > 0) {
            levelUpAnimationTime -= 1f / 60f  // Assuming 60 FPS
        }
    }
    
    /**
     * Check and award extra life at score milestones
     */
    private fun checkMilestoneReward() {
        for (milestone in lifeRewardMilestones) {
            if (score >= milestone && milestone > lastMilestoneAwarded) {
                lastMilestoneAwarded = milestone
                
                // Award extra life (up to max)
                if (lives < maxLives) {
                    lives++
                    triggerHaptic(HapticType.POWER_UP)
                    triggerScreenPulse(Color.rgb(255, 80, 80))  // Red flash
                    
                    floatingTexts.add(FloatingText.createScorePopup(
                        screenWidth / 2, screenHeight / 2, "+1 LIFE!"
                    ))
                }
                break  // Only award one milestone at a time
            }
        }
    }

    /**
     * Handle ball bouncing off screen edges
     */
    private fun handleScreenCollisions(ball: Ball) {
        // Left wall
        if (ball.x - ball.radius <= 0) {
            ball.x = ball.radius
            ball.bounceX()
        }
        // Right wall
        if (ball.x + ball.radius >= screenWidth) {
            ball.x = screenWidth - ball.radius
            ball.bounceX()
        }
        // Top wall
        if (ball.y - ball.radius <= 0) {
            ball.y = ball.radius
            ball.bounceY()
        }
    }

    /**
     * Spawn a new falling line
     */
    private fun spawnLine() {
        val lineWidth = (100f + (Math.random() * 150).toFloat()) * scaleFactor  // Responsive random width
        val lineHeight = 20f * scaleFactor
        val responsiveSpeed = baseLineSpeed * scaleFactor
        
        val newLine = Line.spawn(screenWidth, lineWidth, lineHeight, responsiveSpeed)
        lines.add(newLine)
    }

    /**
     * Handle game over state
     */
    private fun gameOver() {
        gameState = GameState.GAME_OVER
        triggerHaptic(HapticType.GAME_OVER)
        
        // Calculate survival time
        val survivalTime = System.currentTimeMillis() - gameStartTime
        
        // Update best score
        if (score > bestScore) {
            bestScore = score
        }
        
        // Update best combo
        if (comboMultiplier > bestComboEver) {
            bestComboEver = comboMultiplier
        }
        
        // Update longest survival
        if (survivalTime > longestSurvivalTime) {
            longestSurvivalTime = survivalTime
        }
        
        // Update totals
        totalGamesPlayed++
        totalLinesDestroyed += sessionLinesDestroyed
        totalScoreEarned += score
        
        // Save all statistics
        prefs.edit()
            .putInt("bestScore", bestScore)
            .putInt("totalGamesPlayed", totalGamesPlayed)
            .putInt("totalLinesDestroyed", totalLinesDestroyed)
            .putLong("totalScoreEarned", totalScoreEarned)
            .putInt("bestComboEver", bestComboEver)
            .putLong("longestSurvivalTime", longestSurvivalTime)
            .apply()
    }

    /**
     * Draw everything to canvas
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        
        // Apply screen shake
        canvas.save()
        canvas.translate(screenShakeOffset, screenShakeOffset)

        // Draw dark gradient background
        drawBackground(canvas)

        when (gameState) {
            GameState.START_SCREEN -> drawStartScreen(canvas)
            GameState.PLAYING -> drawGameplay(canvas)
            GameState.PAUSED -> {
                drawGameplay(canvas)  // Draw game state underneath
                drawPauseScreen(canvas)  // Overlay pause screen
            }
            GameState.GAME_OVER -> drawGameOverScreen(canvas)
        }
        
        // Draw screen pulse VFX
        if (screenPulseAlpha > 0) {
            screenPulsePaint.color = screenPulseColor
            screenPulsePaint.alpha = screenPulseAlpha
            canvas.drawRect(0f, 0f, screenWidth, screenHeight, screenPulsePaint)
        }
        
        canvas.restore()
    }
    
    /**
     * Draw retrowave neon background from drawable
     */
    private fun drawBackground(canvas: Canvas) {
        // Scale background bitmap if needed
        if (scaledBackgroundBitmap == null || 
            scaledBackgroundBitmap?.width != screenWidth.toInt() || 
            scaledBackgroundBitmap?.height != screenHeight.toInt()) {
            backgroundBitmap?.let { original ->
                scaledBackgroundBitmap = Bitmap.createScaledBitmap(
                    original, 
                    screenWidth.toInt(), 
                    screenHeight.toInt(), 
                    true
                )
            }
        }
        
        // Draw background image
        scaledBackgroundBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        } ?: run {
            // Fallback gradient if bitmap not available
            backgroundPaint.shader = LinearGradient(
                0f, 0f, 0f, screenHeight,
                COLOR_BACKGROUND,
                Color.rgb(30, 10, 50),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, screenWidth, screenHeight, backgroundPaint)
        }
    }

    /**
     * Draw start screen with title and instructions
     */
    private fun drawStartScreen(canvas: Canvas) {
        // Draw title with neon glow - LARGE and visible
        titlePaint.textSize = 110f * scaleFactor
        titlePaint.color = COLOR_BALL
        titlePaint.setShadowLayer(25f * scaleFactor, 0f, 0f, COLOR_BALL)
        canvas.drawText("BALL vs LINES", screenWidth / 2, screenHeight / 3, titlePaint)

        // Draw ball preview
        if (balls.isNotEmpty()) {
            balls[0].x = screenWidth / 2
            balls[0].y = screenHeight / 2
            balls[0].draw(canvas)
        }

        // Draw instructions with subtle glow - LARGER
        subtitlePaint.textSize = 60f * scaleFactor
        subtitlePaint.color = COLOR_NEON_PURPLE
        subtitlePaint.setShadowLayer(15f * scaleFactor, 0f, 0f, COLOR_NEON_PURPLE)
        subtitlePaint.alpha = 255
        canvas.drawText("TAP TO START", screenWidth / 2, screenHeight * 0.68f, subtitlePaint)

        // Draw best score if exists - LARGER
        if (bestScore > 0) {
            textPaint.textSize = 50f * scaleFactor
            textPaint.setShadowLayer(10f * scaleFactor, 0f, 0f, COLOR_COMBO)
            canvas.drawText("BEST: $bestScore", screenWidth / 2, screenHeight * 0.82f, textPaint)
        }
    }

    /**
     * Draw main gameplay with neon arcade effects
     */
    private fun drawGameplay(canvas: Canvas) {
        // Draw hit flashes (behind everything)
        for (flash in hitFlashes) {
            flash.draw(canvas)
        }
        
        // Draw particles
        drawParticles(canvas)
        
        // Draw all lines
        for (line in lines) {
            line.draw(canvas)
        }
        
        // Draw power-ups
        for (powerUp in powerUps) {
            powerUp.draw(canvas)
        }

        // Draw paddle
        paddle.draw(canvas)
        
        // Draw shield effect around paddle if active
        if (activeShield) {
            val shieldPaint = Paint().apply {
                isAntiAlias = true
                color = PowerUpType.SHIELD.color
                alpha = (100 + 50 * kotlin.math.sin(System.currentTimeMillis() / 100.0)).toInt().coerceIn(80, 180)
                style = Paint.Style.STROKE
                strokeWidth = 4f * scaleFactor
            }
            val shieldRect = paddle.rect
            canvas.drawRoundRect(
                shieldRect.left - 8f, shieldRect.top - 8f,
                shieldRect.right + 8f, shieldRect.bottom + 8f,
                15f, 15f, shieldPaint
            )
        }

        // Draw ball trails first (behind balls)
        for (ball in balls) {
            ball.drawTrail(canvas)
        }
        
        // Draw all balls
        for (ball in balls) {
            ball.draw(canvas)
        }
        
        // Draw floating score texts (on top)
        for (text in floatingTexts) {
            text.draw(canvas)
        }

        // Draw HUD
        drawHUD(canvas)
    }

    /**
     * Draw polished in-game HUD
     * Score (centered), Ball count (top right), Combo (below score)
     */
    private fun drawHUD(canvas: Canvas) {
        val padding = 35f * scaleFactor
        val topY = statusBarHeight + (40f * scaleFactor)
        val labelSize = 38f * scaleFactor  // LARGER labels
        val valueSize = 65f * scaleFactor   // LARGER values
        val comboSize = 48f * scaleFactor   // LARGER combo
        
        // Draw HUD background for visibility - taller
        val hudHeight = (130f * scaleFactor)
        canvas.drawRect(0f, 0f, screenWidth, statusBarHeight + hudHeight, hudBackgroundPaint)
        
        // Draw lives on left (hearts) - LARGER
        heartPaint.textSize = 40f * scaleFactor
        var heartX = padding
        for (i in 0 until maxLives) {
            heartPaint.color = if (i < lives) Color.rgb(255, 80, 80) else Color.rgb(80, 80, 80)
            canvas.drawText("❤", heartX, topY + 10f, heartPaint)
            heartX += 48f * scaleFactor
        }
        
        // Draw pause button on top right - LARGER
        val pauseX = screenWidth - padding - (30f * scaleFactor)
        val pauseY = topY - (12f * scaleFactor)
        val pauseSize = 28f * scaleFactor
        pauseButtonPaint.color = Color.WHITE
        pauseButtonPaint.alpha = 200
        // Draw two vertical bars for pause icon
        canvas.drawRect(pauseX - pauseSize, pauseY, pauseX - pauseSize * 0.6f, pauseY + pauseSize * 1.2f, pauseButtonPaint)
        canvas.drawRect(pauseX - pauseSize * 0.4f, pauseY, pauseX, pauseY + pauseSize * 1.2f, pauseButtonPaint)
        
        // Draw score centered with scale animation
        canvas.save()
        canvas.translate(screenWidth / 2, topY + (35f * scaleFactor))
        canvas.scale(scoreScale, scoreScale)
        canvas.translate(-screenWidth / 2, -(topY + (35f * scaleFactor)))
        
        hudPaint.textAlign = Paint.Align.CENTER
        hudPaint.textSize = labelSize
        canvas.drawText("SCORE", screenWidth / 2, topY, hudPaint)
        hudValuePaint.textAlign = Paint.Align.CENTER
        hudValuePaint.textSize = valueSize
        canvas.drawText("$score", screenWidth / 2, topY + (55f * scaleFactor), hudValuePaint)
        
        canvas.restore()
        
        // Draw combo indicator below score (if active) - LARGER
        if (comboMultiplier > 1) {
            comboPaint.textSize = comboSize
            comboPaint.alpha = if (comboDisplayTime > 0) 255 else 180
            canvas.drawText("COMBO x$comboMultiplier", screenWidth / 2, topY + (100f * scaleFactor), comboPaint)
        }
        
        // Draw ball count on right (below pause) - LARGER
        hudPaint.textAlign = Paint.Align.RIGHT
        hudPaint.textSize = labelSize * 0.8f
        canvas.drawText("BALLS", screenWidth - padding, topY + (40f * scaleFactor), hudPaint)
        hudValuePaint.textAlign = Paint.Align.RIGHT
        hudValuePaint.textSize = valueSize * 0.8f
        canvas.drawText("${balls.size}", screenWidth - padding, topY + (75f * scaleFactor), hudValuePaint)
        
        // Draw survival timer on right (below balls) - LARGER
        val survivalSeconds = (System.currentTimeMillis() - gameStartTime) / 1000
        val minutes = survivalSeconds / 60
        val seconds = survivalSeconds % 60
        val timeString = String.format("%d:%02d", minutes, seconds)
        hudPaint.textAlign = Paint.Align.RIGHT
        hudPaint.textSize = labelSize * 0.7f
        hudPaint.color = Color.rgb(180, 180, 180)
        canvas.drawText("TIME", screenWidth - padding, topY + (100f * scaleFactor), hudPaint)
        hudPaint.color = Color.WHITE
        canvas.drawText(timeString, screenWidth - padding - (45f * scaleFactor), topY + (100f * scaleFactor), hudPaint)
        
        // Draw difficulty level indicator (next to hearts) - LARGER
        levelUpPaint.textSize = 28f * scaleFactor
        val levelColor = when {
            difficultyLevel <= 3 -> Color.rgb(100, 255, 100)  // Green - Easy
            difficultyLevel <= 6 -> Color.rgb(255, 200, 50)   // Yellow - Medium
            else -> Color.rgb(255, 80, 80)                     // Red - Hard
        }
        levelUpPaint.color = levelColor
        levelUpPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("LV.$difficultyLevel", heartX + (15f * scaleFactor), topY + 10f, levelUpPaint)
        
        // Draw LEVEL UP animation if active - LARGER
        if (levelUpAnimationTime > 0) {
            val alpha = (levelUpAnimationTime / 2.5f * 255).toInt().coerceIn(0, 255)
            val scale = 1f + (1f - levelUpAnimationTime / 2.5f) * 0.3f
            levelUpPaint.textSize = 75f * scaleFactor * scale
            levelUpPaint.color = Color.rgb(255, 215, 0)
            levelUpPaint.alpha = alpha
            levelUpPaint.textAlign = Paint.Align.CENTER
            levelUpPaint.setShadowLayer(20f * scaleFactor, 0f, 0f, Color.rgb(255, 215, 0))
            canvas.drawText("LEVEL UP!", screenWidth / 2, screenHeight / 3, levelUpPaint)
            levelUpPaint.clearShadowLayer()
        }
        
        // Draw active power-up indicators on left (below hearts) - LARGER
        var indicatorY = topY + (50f * scaleFactor)
        powerUpIndicatorPaint.textSize = 26f * scaleFactor
        
        if (activeShield) {
            powerUpIndicatorPaint.color = PowerUpType.SHIELD.color
            canvas.drawText("🛡 SHIELD ${shieldTimer.toInt()}s", padding, indicatorY, powerUpIndicatorPaint)
            indicatorY += 30f * scaleFactor
        }
        if (slowMoActive) {
            powerUpIndicatorPaint.color = PowerUpType.SLOW_MO.color
            canvas.drawText("⏱ SLOW-MO ${slowMoTimer.toInt()}s", padding, indicatorY, powerUpIndicatorPaint)
            indicatorY += 30f * scaleFactor
        }
        if (bigPaddleActive) {
            powerUpIndicatorPaint.color = PowerUpType.BIG_PADDLE.color
            canvas.drawText("▬ BIG PADDLE ${bigPaddleTimer.toInt()}s", padding, indicatorY, powerUpIndicatorPaint)
            indicatorY += 30f * scaleFactor
        }
        if (magnetActive) {
            powerUpIndicatorPaint.color = PowerUpType.MAGNET.color
            canvas.drawText("◎ MAGNET ${magnetTimer.toInt()}s", padding, indicatorY, powerUpIndicatorPaint)
            indicatorY += 30f * scaleFactor
        }
        if (scoreBoostActive) {
            powerUpIndicatorPaint.color = PowerUpType.SCORE_BOOST.color
            canvas.drawText("★ 2X SCORE ${scoreBoostTimer.toInt()}s", padding, indicatorY, powerUpIndicatorPaint)
        }
        
        // Draw achievement popups
        achievementManager.drawPopups(canvas, screenWidth, scaleFactor)
    }
    
    /**
     * Draw pause screen overlay
     */
    private fun drawPauseScreen(canvas: Canvas) {
        // Semi-transparent overlay
        hudBackgroundPaint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, hudBackgroundPaint)
        hudBackgroundPaint.color = Color.argb(120, 0, 0, 0)  // Reset
        
        // PAUSED text - LARGER
        titlePaint.textSize = 95f * scaleFactor
        titlePaint.color = COLOR_NEON_PURPLE
        titlePaint.setShadowLayer(20f * scaleFactor, 0f, 0f, COLOR_NEON_PURPLE)
        canvas.drawText("PAUSED", screenWidth / 2, screenHeight / 2 - (60f * scaleFactor), titlePaint)
        
        // Resume instruction - LARGER
        subtitlePaint.textSize = 50f * scaleFactor
        subtitlePaint.color = Color.WHITE
        subtitlePaint.alpha = 220
        canvas.drawText("Tap to Resume", screenWidth / 2, screenHeight / 2 + (50f * scaleFactor), subtitlePaint)
        
        // Reset title paint
        titlePaint.color = COLOR_BALL
        titlePaint.setShadowLayer(15f * scaleFactor, 0f, 0f, COLOR_BALL)
    }
    
    /**
     * Check if pause button was tapped
     */
    private fun isPauseButtonTapped(x: Float, y: Float): Boolean {
        val padding = 35f * scaleFactor
        val topY = statusBarHeight + (40f * scaleFactor)
        val pauseX = screenWidth - padding - (30f * scaleFactor)
        val pauseY = topY - (12f * scaleFactor)
        val hitArea = 60f * scaleFactor
        
        return x >= pauseX - hitArea && x <= pauseX + hitArea &&
               y >= pauseY - hitArea && y <= pauseY + hitArea
    }

    /**
     * Draw game over screen with retrowave styling
     */
    private fun drawGameOverScreen(canvas: Canvas) {
        // Calculate survival time for display
        val survivalTime = System.currentTimeMillis() - gameStartTime
        val survivalSeconds = survivalTime / 1000
        val minutes = survivalSeconds / 60
        val seconds = survivalSeconds % 60
        val timeString = String.format("%d:%02d", minutes, seconds)
        
        // Draw "GAME OVER" title with hot pink glow - LARGER
        titlePaint.textSize = 100f * scaleFactor
        titlePaint.color = COLOR_LINE  // Hot pink for game over
        titlePaint.setShadowLayer(25f * scaleFactor, 0f, 0f, COLOR_LINE)
        canvas.drawText("GAME OVER", screenWidth / 2, screenHeight * 0.20f, titlePaint)
        
        // Reset title paint
        titlePaint.color = COLOR_BALL
        titlePaint.setShadowLayer(15f * scaleFactor, 0f, 0f, COLOR_BALL)

        // Draw final score with cyan glow - LARGER
        hudPaint.textSize = 40f * scaleFactor
        hudPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("SCORE", screenWidth / 2, screenHeight * 0.30f, hudPaint)
        hudValuePaint.textSize = 80f * scaleFactor
        hudValuePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("$score", screenWidth / 2, screenHeight * 0.38f, hudValuePaint)

        // Draw best score or new best message - LARGER
        if (score >= bestScore && score > 0) {
            comboPaint.textSize = 55f * scaleFactor
            comboPaint.alpha = 255
            canvas.drawText("★ NEW BEST! ★", screenWidth / 2, screenHeight * 0.46f, comboPaint)
        } else {
            textPaint.textSize = 45f * scaleFactor
            textPaint.setShadowLayer(8f * scaleFactor, 0f, 0f, COLOR_NEON_PURPLE)
            canvas.drawText("BEST: $bestScore", screenWidth / 2, screenHeight * 0.46f, textPaint)
        }
        
        // Draw game stats in a box - LARGER
        val statsY = screenHeight * 0.54f
        val statsSpacing = 40f * scaleFactor
        hudPaint.textSize = 32f * scaleFactor
        hudPaint.color = Color.rgb(180, 180, 180)
        
        // Level reached
        val levelColor = when {
            difficultyLevel <= 3 -> Color.rgb(100, 255, 100)  // Green
            difficultyLevel <= 6 -> Color.rgb(255, 200, 50)   // Yellow
            else -> Color.rgb(255, 80, 80)                     // Red
        }
        hudPaint.color = levelColor
        canvas.drawText("Level $difficultyLevel", screenWidth / 2, statsY, hudPaint)
        
        // Survival time
        hudPaint.color = Color.rgb(150, 200, 255)
        canvas.drawText("Time: $timeString", screenWidth / 2, statsY + statsSpacing, hudPaint)
        
        // Lines destroyed
        hudPaint.color = Color.rgb(255, 150, 200)
        canvas.drawText("Lines: $sessionLinesDestroyed", screenWidth / 2, statsY + statsSpacing * 2, hudPaint)
        
        // Best combo this game
        if (comboMultiplier > 1) {
            hudPaint.color = COLOR_COMBO
            canvas.drawText("Best Combo: x$comboMultiplier", screenWidth / 2, statsY + statsSpacing * 3, hudPaint)
        }
        
        hudPaint.color = Color.WHITE  // Reset
        
        // Draw achievements progress - LARGER
        val achievementsUnlocked = achievementManager.getUnlockedCount()
        val achievementsTotal = achievementManager.getTotalCount()
        hudPaint.textSize = 32f * scaleFactor
        hudPaint.color = Color.rgb(255, 215, 0)  // Gold color
        canvas.drawText("🏆 Achievements: $achievementsUnlocked/$achievementsTotal", 
            screenWidth / 2, screenHeight * 0.76f, hudPaint)
        
        // Draw total games played - LARGER
        hudPaint.color = Color.rgb(150, 150, 180)
        hudPaint.textSize = 28f * scaleFactor
        canvas.drawText("Total Games: $totalGamesPlayed", screenWidth / 2, screenHeight * 0.81f, hudPaint)
        hudPaint.color = Color.WHITE  // Reset

        // Draw restart instruction with neon purple glow - LARGER
        subtitlePaint.textSize = 55f * scaleFactor
        subtitlePaint.color = COLOR_NEON_PURPLE
        subtitlePaint.setShadowLayer(15f * scaleFactor, 0f, 0f, COLOR_NEON_PURPLE)
        subtitlePaint.alpha = 255
        canvas.drawText("TAP TO RESTART", screenWidth / 2, screenHeight * 0.92f, subtitlePaint)
    }

    /**
     * Handle touch events for paddle movement and game state changes
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                when (gameState) {
                    GameState.START_SCREEN -> {
                        // Start game on tap
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            resetGame()
                        }
                    }
                    GameState.PLAYING -> {
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            // Check if pause button tapped
                            if (isPauseButtonTapped(event.x, event.y)) {
                                gameState = GameState.PAUSED
                                return true
                            }
                        }
                        // Move paddle to touch position
                        paddle.moveTo(event.x)
                    }
                    GameState.PAUSED -> {
                        // Resume game on tap
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            gameState = GameState.PLAYING
                        }
                    }
                    GameState.GAME_OVER -> {
                        // Restart game on tap
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            resetGame()
                        }
                    }
                }
            }
        }
        return true
    }

    /**
     * Pause game when activity is paused
     */
    fun pause() {
        isRunning = false
        try {
            gameThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * Resume game when activity is resumed
     */
    fun resume() {
        isRunning = true
        gameThread = Thread(this)
        gameThread?.start()
    }
}

/**
 * HitFlash represents a brief flash effect when a line is destroyed
 * Uses magenta/pink color to match retrowave theme
 */
data class HitFlash(
    val x: Float,
    val y: Float,
    val width: Float,
    var alpha: Int = 255,
    var lifetime: Float = 1f
) {
    // Hot pink glow for flash effect
    private val flashColor = Color.rgb(255, 0, 128)
    
    private val paint = Paint().apply {
        isAntiAlias = true
        color = flashColor
        setShadowLayer(20f, 0f, 0f, flashColor)
    }
    
    fun update() {
        lifetime -= 0.12f
        alpha = (lifetime * 255).toInt().coerceIn(0, 255)
    }
    
    fun draw(canvas: Canvas) {
        paint.alpha = alpha
        val size = width * (1 + (1 - lifetime) * 0.6f)
        canvas.drawCircle(x, y, size / 2, paint)
    }
    
    fun isExpired(): Boolean = lifetime <= 0f
}
