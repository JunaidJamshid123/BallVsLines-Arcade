package com.example.linevsball

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.random.Random

/**
 * Line types with different behaviors and point values
 */
enum class LineType(
    val primaryColor: Int,
    val lightColor: Int,
    val darkColor: Int,
    val pointValue: Int,
    val speedMultiplier: Float
) {
    NORMAL(
        Color.rgb(255, 0, 128),      // Hot pink
        Color.rgb(255, 100, 180),
        Color.rgb(200, 0, 100),
        10,
        1f
    ),
    GOLD(
        Color.rgb(255, 215, 0),      // Gold - bonus points
        Color.rgb(255, 245, 150),
        Color.rgb(200, 160, 0),
        50,
        1.2f
    ),
    SPEED(
        Color.rgb(255, 80, 80),      // Red - faster
        Color.rgb(255, 150, 150),
        Color.rgb(180, 40, 40),
        15,
        1.8f
    ),
    POWER(
        Color.rgb(0, 255, 200),      // Cyan - drops power-up
        Color.rgb(150, 255, 230),
        Color.rgb(0, 180, 150),
        20,
        0.9f
    ),
    SLOW(
        Color.rgb(100, 200, 255),    // Light blue - slower
        Color.rgb(180, 230, 255),
        Color.rgb(50, 150, 220),
        5,
        0.6f
    )
}

/**
 * Line data class representing falling obstacles that the ball must destroy.
 * Lines spawn at the top and fall downward at increasing speed.
 * Enhanced with destruction animation (shrink + fade over 150ms).
 */
data class Line(
    var x: Float,           // Center X position
    var y: Float,           // Center Y position
    val width: Float,       // Line width
    val height: Float,      // Line height (thickness)
    var fallSpeed: Float,   // Current falling speed
    val type: LineType = LineType.NORMAL  // Line type
) {
    // Flag to mark line for removal when destroyed
    var isDestroyed: Boolean = false
    
    // Destruction animation state
    var isAnimatingDestruction: Boolean = false
    var destructionProgress: Float = 0f  // 0 to 1
    private val destructionSpeed: Float = 0.067f  // ~150ms at 60fps (1 / (60 * 0.15))
    
    // Current scale for shrink effect
    var scale: Float = 1f
    
    // Alpha for fade effect
    var alpha: Int = 255
    
    // Shimmer effect for gold lines
    private var shimmerPhase: Float = Random.nextFloat() * 6.28f

    // Rectangle for collision detection and drawing
    val rect: RectF
        get() {
            val scaledWidth = width * scale
            val scaledHeight = height * scale
            return RectF(
                x - scaledWidth / 2,
                y - scaledHeight / 2,
                x + scaledWidth / 2,
                y + scaledHeight / 2
            )
        }
    
    // Unscaled rect for collision (use original size)
    val collisionRect: RectF
        get() = RectF(
            x - width / 2,
            y - height / 2,
            x + width / 2,
            y + height / 2
        )

    // Paint for drawing the line with gradient
    private val paint = Paint().apply {
        isAntiAlias = true
    }
    
    // Paint for the glow/shadow effect
    private val glowPaint = Paint().apply {
        isAntiAlias = true
        setShadowLayer(15f, 0f, 0f, type.primaryColor)
    }

    /**
     * Update line position (falling downward) and destruction animation
     */
    fun update() {
        if (isAnimatingDestruction) {
            destructionProgress += destructionSpeed
            scale = 1f - (destructionProgress * 0.3f)  // Shrink to 70%
            alpha = ((1f - destructionProgress) * 255).toInt().coerceIn(0, 255)
            
            if (destructionProgress >= 1f) {
                isDestroyed = true
            }
        } else {
            y += fallSpeed
            // Update shimmer for gold lines
            if (type == LineType.GOLD) {
                shimmerPhase += 0.15f
            }
        }
    }
    
    /**
     * Start the destruction animation
     */
    fun startDestructionAnimation() {
        isAnimatingDestruction = true
    }

    /**
     * Draw the line with neon gradient and glow based on type
     */
    fun draw(canvas: Canvas) {
        val lineRect = rect
        
        // Create gradient using line type colors
        var lightColor = type.lightColor
        if (type == LineType.GOLD) {
            // Add shimmer effect for gold lines
            val shimmer = (kotlin.math.sin(shimmerPhase) * 0.3f + 0.7f).coerceIn(0f, 1f)
            lightColor = Color.rgb(
                (255 * shimmer).toInt().coerceIn(200, 255),
                (245 * shimmer).toInt().coerceIn(200, 255),
                (150 * shimmer).toInt().coerceIn(100, 200)
            )
        }
        
        paint.shader = LinearGradient(
            lineRect.left, lineRect.top,
            lineRect.right, lineRect.bottom,
            intArrayOf(lightColor, type.primaryColor, type.darkColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.alpha = alpha
        
        // Draw glow behind the line
        glowPaint.color = type.primaryColor
        glowPaint.alpha = (alpha * 0.4f).toInt().coerceIn(0, 100)
        canvas.drawRoundRect(
            RectF(lineRect.left - 5, lineRect.top - 5, lineRect.right + 5, lineRect.bottom + 5),
            10f, 10f, glowPaint
        )
        
        // Draw main line with rounded corners
        canvas.drawRoundRect(lineRect, 8f, 8f, paint)
    }

    /**
     * Check if ball collides with this line
     * Returns true if collision detected
     */
    fun checkCollision(ball: Ball): Boolean {
        if (isDestroyed || isAnimatingDestruction) return false
        
        val lineRect = collisionRect
        
        // Find the closest point on the line to the ball center
        val closestX = ball.x.coerceIn(lineRect.left, lineRect.right)
        val closestY = ball.y.coerceIn(lineRect.top, lineRect.bottom)
        
        // Calculate distance from ball center to closest point
        val distanceX = ball.x - closestX
        val distanceY = ball.y - closestY
        val distance = kotlin.math.sqrt(distanceX * distanceX + distanceY * distanceY)
        
        return distance <= ball.radius
    }

    /**
     * Handle ball collision with this line
     * Starts destruction animation and reflects ball
     */
    fun handleCollision(ball: Ball): Boolean {
        if (!checkCollision(ball)) return false
        
        // Start destruction animation instead of instant removal
        startDestructionAnimation()
        
        val lineRect = collisionRect
        
        // Determine collision side for proper reflection
        val ballBottom = ball.y + ball.radius
        val ballTop = ball.y - ball.radius
        val ballRight = ball.x + ball.radius
        val ballLeft = ball.x - ball.radius
        
        val overlapTop = ballBottom - lineRect.top
        val overlapBottom = lineRect.bottom - ballTop
        val overlapLeft = ballRight - lineRect.left
        val overlapRight = lineRect.right - ballLeft
        
        val minOverlap = minOf(overlapTop, overlapBottom, overlapLeft, overlapRight)
        
        when (minOverlap) {
            overlapTop, overlapBottom -> ball.bounceY()
            overlapLeft, overlapRight -> ball.bounceX()
        }
        
        return true
    }

    /**
     * Check if line has reached the bottom of the screen
     * Ignore lines that are being destroyed
     */
    fun hasReachedBottom(screenHeight: Float): Boolean {
        if (isAnimatingDestruction) return false
        return y + height / 2 >= screenHeight
    }

    companion object {
        /**
         * Factory method to create a new line at random position at top of screen
         * Randomly selects line type with weighted probabilities
         */
        fun spawn(screenWidth: Float, width: Float, height: Float, fallSpeed: Float): Line {
            val minX = width / 2 + 20
            val maxX = screenWidth - width / 2 - 20
            val x = (minX + Math.random() * (maxX - minX)).toFloat()
            
            // Select random line type with weighted probability
            val type = when (Random.nextInt(100)) {
                in 0..59 -> LineType.NORMAL      // 60% normal
                in 60..74 -> LineType.GOLD       // 15% gold (bonus)
                in 75..84 -> LineType.SPEED      // 10% speed (fast)
                in 85..94 -> LineType.POWER      // 10% power (drops powerup)
                else -> LineType.SLOW            // 5% slow
            }
            
            return Line(
                x = x,
                y = -height,  // Spawn above screen
                width = width,
                height = height,
                fallSpeed = fallSpeed * type.speedMultiplier,
                type = type
            )
        }
        
        /**
         * Spawn a specific type of line
         */
        fun spawnType(screenWidth: Float, width: Float, height: Float, fallSpeed: Float, type: LineType): Line {
            val minX = width / 2 + 20
            val maxX = screenWidth - width / 2 - 20
            val x = (minX + Math.random() * (maxX - minX)).toFloat()
            
            return Line(
                x = x,
                y = -height,
                width = width,
                height = height,
                fallSpeed = fallSpeed * type.speedMultiplier,
                type = type
            )
        }
    }
}
