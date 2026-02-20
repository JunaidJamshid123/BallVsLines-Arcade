package com.example.linevsball

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

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
    var fallSpeed: Float    // Current falling speed
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
    
    // Hot pink neon color (#FF0080) - contrasts with purple background
    private val neonPink = Color.rgb(255, 0, 128)
    private val neonPinkLight = Color.rgb(255, 100, 180)
    private val neonPinkDark = Color.rgb(200, 0, 100)

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
        setShadowLayer(15f, 0f, 0f, neonPink)
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
        }
    }
    
    /**
     * Start the destruction animation
     */
    fun startDestructionAnimation() {
        isAnimatingDestruction = true
    }

    /**
     * Draw the line with hot pink neon gradient and glow
     */
    fun draw(canvas: Canvas) {
        val lineRect = rect
        
        // Create gradient using neon pink
        paint.shader = LinearGradient(
            lineRect.left, lineRect.top,
            lineRect.right, lineRect.bottom,
            intArrayOf(neonPinkLight, neonPink, neonPinkDark),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.alpha = alpha
        
        // Draw glow behind the line
        glowPaint.color = neonPink
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
         */
        fun spawn(screenWidth: Float, width: Float, height: Float, fallSpeed: Float): Line {
            val minX = width / 2 + 20
            val maxX = screenWidth - width / 2 - 20
            val x = (minX + Math.random() * (maxX - minX)).toFloat()
            
            return Line(
                x = x,
                y = -height,  // Spawn above screen
                width = width,
                height = height,
                fallSpeed = fallSpeed
            )
        }
    }
}
