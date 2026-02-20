package com.example.linevsball

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Ball data class representing the bouncing ball in the game.
 * Contains position, velocity, and rendering properties.
 * Enhanced with trail effect for fast arcade feel.
 */
data class Ball(
    var x: Float,           // Center X position
    var y: Float,           // Center Y position
    var velocityX: Float,   // Horizontal velocity
    var velocityY: Float,   // Vertical velocity
    val radius: Float,      // Ball radius
    var baseSpeed: Float    // Base speed multiplier for difficulty scaling
) {
    // Neon cyan color (#00FFFF) - pops against purple background
    private val neonCyan = Color.rgb(0, 255, 255)
    private val neonCyanDark = Color.rgb(0, 200, 220)
    
    // Trail effect - store last 8 positions
    private val trailPositions = ArrayDeque<Pair<Float, Float>>(TRAIL_LENGTH)
    
    // Scale bounce effect (1.0 = normal, >1.0 = expanded)
    var scaleFactor: Float = 1f
    private var scaleVelocity: Float = 0f
    
    // Paint for the ball with cyan glow effect using setShadowLayer
    private val paint = Paint().apply {
        isAntiAlias = true
        color = neonCyan
        // Add soft glow effect
        setShadowLayer(radius * 0.8f, 0f, 0f, neonCyan)
    }
    
    // Paint for the outer glow effect
    private val glowPaint = Paint().apply {
        isAntiAlias = true
        color = neonCyan
        alpha = 60
    }
    
    // Paint for the highlight (inner shine)
    private val highlightPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        alpha = 200
    }
    
    // Paint for trail effect
    private val trailPaint = Paint().apply {
        isAntiAlias = true
    }

    companion object {
        const val TRAIL_LENGTH = 8
    }

    /**
     * Update ball position based on velocity
     */
    fun update() {
        // Store current position in trail
        trailPositions.addFirst(Pair(x, y))
        if (trailPositions.size > TRAIL_LENGTH) {
            trailPositions.removeLast()
        }
        
        // Update position
        x += velocityX
        y += velocityY
        
        // Update scale bounce effect (spring back to normal)
        if (scaleFactor != 1f) {
            scaleVelocity += (1f - scaleFactor) * 0.3f  // Spring force
            scaleVelocity *= 0.7f  // Damping
            scaleFactor += scaleVelocity
            if (kotlin.math.abs(scaleFactor - 1f) < 0.01f && kotlin.math.abs(scaleVelocity) < 0.01f) {
                scaleFactor = 1f
                scaleVelocity = 0f
            }
        }
    }
    
    /**
     * Trigger scale bounce effect on impact
     */
    fun triggerScaleBounce() {
        scaleFactor = 1.3f
        scaleVelocity = 0f
    }

    /**
     * Draw the ball trail effect - fading circles behind the ball
     */
    fun drawTrail(canvas: Canvas) {
        for ((index, pos) in trailPositions.withIndex()) {
            val alpha = ((TRAIL_LENGTH - index) * 25).coerceIn(0, 200)
            val trailRadius = radius * (1f - index * 0.08f)
            
            trailPaint.color = neonCyan
            trailPaint.alpha = alpha
            canvas.drawCircle(pos.first, pos.second, trailRadius, trailPaint)
        }
    }

    /**
     * Draw the ball with enhanced glow effect
     */
    fun draw(canvas: Canvas) {
        val drawRadius = radius * scaleFactor
        
        // Draw outer glow aura
        glowPaint.shader = RadialGradient(
            x, y, drawRadius * 2.5f,
            intArrayOf(Color.argb(100, 0, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0.2f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, drawRadius * 2.5f, glowPaint)
        
        // Draw main ball with gradient
        paint.shader = RadialGradient(
            x - drawRadius * 0.3f, y - drawRadius * 0.3f, drawRadius * 1.2f,
            intArrayOf(Color.WHITE, neonCyan, neonCyanDark),
            floatArrayOf(0.1f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, drawRadius, paint)
        
        // Draw small highlight for 3D effect
        canvas.drawCircle(x - drawRadius * 0.3f, y - drawRadius * 0.3f, drawRadius * 0.25f, highlightPaint)
    }

    /**
     * Reverse horizontal direction
     */
    fun bounceX() {
        velocityX = -velocityX
    }

    /**
     * Reverse vertical direction
     */
    fun bounceY() {
        velocityY = -velocityY
    }

    /**
     * Increase ball speed for difficulty scaling
     */
    fun increaseSpeed(factor: Float) {
        val currentSpeed = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY)
        val newSpeed = currentSpeed * factor
        val angle = kotlin.math.atan2(velocityY.toDouble(), velocityX.toDouble())
        velocityX = (newSpeed * kotlin.math.cos(angle)).toFloat()
        velocityY = (newSpeed * kotlin.math.sin(angle)).toFloat()
    }

    /**
     * Reset ball to initial position with random upward direction
     */
    fun reset(screenWidth: Float, screenHeight: Float, initialSpeed: Float) {
        x = screenWidth / 2
        y = screenHeight / 2
        // Launch ball at random angle going upward
        val angle = Math.toRadians((Math.random() * 60 + 60).toDouble()) // 60-120 degrees
        velocityX = (initialSpeed * kotlin.math.cos(angle)).toFloat()
        velocityY = -(initialSpeed * kotlin.math.sin(angle)).toFloat()
    }

    /**
     * Create a copy of this ball with slightly different velocity
     * Used for multi-ball spawning on line collision
     */
    fun spawnCopy(): Ball {
        // Random velocity variation (±30%)
        val variation = 0.7f + (Math.random() * 0.6f).toFloat()
        val angleOffset = Math.toRadians((Math.random() * 60 - 30).toDouble()) // ±30 degrees
        
        val currentAngle = kotlin.math.atan2(velocityY.toDouble(), velocityX.toDouble())
        val newAngle = currentAngle + angleOffset
        val speed = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY) * variation
        
        return Ball(
            x = this.x,
            y = this.y,
            velocityX = (speed * kotlin.math.cos(newAngle)).toFloat(),
            velocityY = (speed * kotlin.math.sin(newAngle)).toFloat(),
            radius = this.radius,
            baseSpeed = this.baseSpeed
        )
    }

    /**
     * Check if ball is below screen (lost)
     */
    fun isLost(screenHeight: Float): Boolean {
        return y - radius > screenHeight
    }
}

