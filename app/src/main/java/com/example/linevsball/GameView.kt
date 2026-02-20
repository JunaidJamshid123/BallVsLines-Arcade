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
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * GameView is the main game component that handles rendering, game loop, and input.
 * Extends SurfaceView for custom Canvas-based rendering.
 * Enhanced with neon arcade visuals, combo system, and particle effects.
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

    // SharedPreferences for storing best score
    private val prefs: SharedPreferences = context.getSharedPreferences("BallVsLines", Context.MODE_PRIVATE)

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

    init {
        // Set up surface holder callback
        holder.addCallback(this)
        
        // Load best score
        bestScore = prefs.getInt("bestScore", 0)
        
        // Load background image from drawable
        backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.backgroundd)
        
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
            line.update()

            // Check collision with each ball (skip if already animating destruction)
            if (!line.isAnimatingDestruction) {
                for (ball in balls) {
                    if (line.handleCollision(ball)) {
                        // Increment combo
                        comboCount++
                        updateComboMultiplier()
                        
                        // Calculate score with combo multiplier
                        val basePoints = 10
                        val points = basePoints * comboMultiplier
                        score += points
                        
                        // Trigger score animation
                        triggerScoreAnimation()
                        
                        // Add floating score text with combo info
                        val popupText = if (comboMultiplier > 1) "+$points (x$comboMultiplier)" else "+$points"
                        floatingTexts.add(FloatingText.createScorePopup(ball.x, ball.y - 30, points, comboMultiplier > 1))
                        
                        // Add hit flash effect
                        hitFlashes.add(HitFlash(line.x, line.y, line.width))
                        
                        // Spawn particle burst at collision point
                        spawnParticles(line.x, line.y)
                        
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

            // Check if line reached bottom - GAME OVER
            if (line.hasReachedBottom(screenHeight)) {
                gameOver()
                return
            }
        }

        // Remove fully destroyed lines (after animation completes)
        lines.removeIf { it.isDestroyed }
        
        // Handle lost balls - reset combo
        if (ballsToRemove.isNotEmpty()) {
            resetCombo()
        }
        
        // Remove lost balls
        balls.removeAll(ballsToRemove)
        
        // Add new balls
        balls.addAll(ballsToAdd)

        // Check if all balls are lost - GAME OVER
        if (balls.isEmpty()) {
            gameOver()
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
     * Spawn particle burst at position
     */
    private fun spawnParticles(x: Float, y: Float) {
        particles.addAll(Particle.createBurst(x, y, 8, COLOR_LINE))
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
     * Update difficulty based on elapsed time
     */
    private fun updateDifficulty(elapsedTime: Long) {
        // Increase line fall speed every 10 seconds
        val speedIncreaseInterval = 10000L
        val speedIncreaseFactor = 1 + (elapsedTime / speedIncreaseInterval) * 0.1f
        baseLineSpeed = 3f * speedIncreaseFactor.coerceAtMost(2.5f)

        // Decrease spawn interval over time (more frequent spawns)
        val spawnDecreaseInterval = 15000L
        val spawnDecreaseFactor = 1 - (elapsedTime / spawnDecreaseInterval) * 0.1f
        lineSpawnInterval = (2000L * spawnDecreaseFactor.coerceAtLeast(0.5f)).toLong()
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
        
        // Update best score
        if (score > bestScore) {
            bestScore = score
            prefs.edit().putInt("bestScore", bestScore).apply()
        }
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
            GameState.GAME_OVER -> drawGameOverScreen(canvas)
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
        // Draw title with neon glow
        titlePaint.textSize = 85f * scaleFactor
        titlePaint.color = COLOR_BALL
        titlePaint.setShadowLayer(15f * scaleFactor, 0f, 0f, COLOR_BALL)
        canvas.drawText("BALL vs LINES", screenWidth / 2, screenHeight / 3, titlePaint)

        // Draw ball preview
        if (balls.isNotEmpty()) {
            balls[0].x = screenWidth / 2
            balls[0].y = screenHeight / 2
            balls[0].draw(canvas)
        }

        // Draw instructions with subtle glow
        subtitlePaint.textSize = 44f * scaleFactor
        subtitlePaint.color = COLOR_NEON_PURPLE
        subtitlePaint.setShadowLayer(10f * scaleFactor, 0f, 0f, COLOR_NEON_PURPLE)
        subtitlePaint.alpha = 255
        canvas.drawText("TAP TO START", screenWidth / 2, screenHeight * 0.7f, subtitlePaint)

        // Draw best score if exists
        if (bestScore > 0) {
            textPaint.textSize = 38f * scaleFactor
            textPaint.setShadowLayer(6f * scaleFactor, 0f, 0f, COLOR_COMBO)
            canvas.drawText("BEST: $bestScore", screenWidth / 2, screenHeight * 0.8f, textPaint)
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

        // Draw paddle
        paddle.draw(canvas)

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
        val padding = 30f * scaleFactor
        val topY = statusBarHeight + (30f * scaleFactor)
        val labelSize = 30f * scaleFactor
        val valueSize = 48f * scaleFactor
        val comboSize = 36f * scaleFactor
        
        // Draw HUD background for visibility
        val hudHeight = (100f * scaleFactor)
        canvas.drawRect(0f, 0f, screenWidth, statusBarHeight + hudHeight, hudBackgroundPaint)
        
        // Draw score centered with scale animation
        canvas.save()
        canvas.translate(screenWidth / 2, topY + (30f * scaleFactor))
        canvas.scale(scoreScale, scoreScale)
        canvas.translate(-screenWidth / 2, -(topY + (30f * scaleFactor)))
        
        hudPaint.textAlign = Paint.Align.CENTER
        hudPaint.textSize = labelSize
        canvas.drawText("SCORE", screenWidth / 2, topY, hudPaint)
        hudValuePaint.textAlign = Paint.Align.CENTER
        hudValuePaint.textSize = valueSize
        canvas.drawText("$score", screenWidth / 2, topY + (45f * scaleFactor), hudValuePaint)
        
        canvas.restore()
        
        // Draw combo indicator below score (if active)
        if (comboMultiplier > 1) {
            comboPaint.textSize = comboSize
            comboPaint.alpha = if (comboDisplayTime > 0) 255 else 180
            canvas.drawText("COMBO x$comboMultiplier", screenWidth / 2, topY + (85f * scaleFactor), comboPaint)
        }
        
        // Draw ball count on right
        hudPaint.textAlign = Paint.Align.RIGHT
        hudPaint.textSize = labelSize * 0.9f
        canvas.drawText("BALLS", screenWidth - padding, topY, hudPaint)
        hudValuePaint.textAlign = Paint.Align.RIGHT
        hudValuePaint.textSize = valueSize * 0.85f
        canvas.drawText("${balls.size}", screenWidth - padding, topY + (38f * scaleFactor), hudValuePaint)
    }

    /**
     * Draw game over screen with retrowave styling
     */
    private fun drawGameOverScreen(canvas: Canvas) {
        // Draw "GAME OVER" title with hot pink glow
        titlePaint.textSize = 75f * scaleFactor
        titlePaint.color = COLOR_LINE  // Hot pink for game over
        titlePaint.setShadowLayer(20f * scaleFactor, 0f, 0f, COLOR_LINE)
        canvas.drawText("GAME OVER", screenWidth / 2, screenHeight / 3, titlePaint)
        
        // Reset title paint
        titlePaint.color = COLOR_BALL
        titlePaint.setShadowLayer(15f * scaleFactor, 0f, 0f, COLOR_BALL)

        // Draw final score with cyan glow
        hudValuePaint.textSize = 60f * scaleFactor
        hudValuePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("$score", screenWidth / 2, screenHeight / 2, hudValuePaint)
        
        hudPaint.textSize = 32f * scaleFactor
        hudPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("SCORE", screenWidth / 2, screenHeight / 2 - (50f * scaleFactor), hudPaint)

        // Draw best score or new best message
        if (score >= bestScore && score > 0) {
            comboPaint.textSize = 45f * scaleFactor
            comboPaint.alpha = 255
            canvas.drawText("NEW BEST!", screenWidth / 2, screenHeight / 2 + (70f * scaleFactor), comboPaint)
        } else {
            textPaint.textSize = 36f * scaleFactor
            textPaint.setShadowLayer(5f * scaleFactor, 0f, 0f, COLOR_NEON_PURPLE)
            canvas.drawText("BEST: $bestScore", screenWidth / 2, screenHeight / 2 + (70f * scaleFactor), textPaint)
        }

        // Draw restart instruction with neon purple glow
        subtitlePaint.textSize = 42f * scaleFactor
        subtitlePaint.color = COLOR_NEON_PURPLE
        subtitlePaint.setShadowLayer(12f * scaleFactor, 0f, 0f, COLOR_NEON_PURPLE)
        subtitlePaint.alpha = 255
        canvas.drawText("TAP TO RESTART", screenWidth / 2, screenHeight * 0.78f, subtitlePaint)
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
                        // Move paddle to touch position
                        paddle.moveTo(event.x)
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
