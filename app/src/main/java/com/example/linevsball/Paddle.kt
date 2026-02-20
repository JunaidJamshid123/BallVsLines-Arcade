package com.example.linevsball

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/**
 * Paddle data class representing the player-controlled paddle at the bottom of the screen.
 * The paddle moves horizontally to keep the ball in play.
 * Enhanced with subtle glow effect for neon arcade theme.
 */
data class Paddle(
    var x: Float,           // Center X position
    var y: Float,           // Center Y position
    var width: Float,       // Paddle width (var for responsive sizing)
    var height: Float,      // Paddle height (var for responsive sizing)
    var screenWidth: Float  // Screen width for boundary checking
) {
    // Neon purple paddle color to match retrowave theme
    private val paddleColor = Color.rgb(180, 100, 255)       // Light neon purple
    private val paddleColorLight = Color.rgb(220, 180, 255)  // Bright purple-white
    private val paddleColorDark = Color.rgb(140, 60, 200)    // Deep purple
    private val glowColor = Color.rgb(157, 0, 255)           // Neon purple glow
    
    // Rectangle for collision detection and drawing
    val rect: RectF
        get() = RectF(
            x - width / 2,
            y - height / 2,
            x + width / 2,
            y + height / 2
        )

    // Paint for drawing the paddle with gradient
    private val paint = Paint().apply {
        isAntiAlias = true
        color = paddleColor
    }
    
    // Paint for neon glow effect
    private val glowPaint = Paint().apply {
        isAntiAlias = true
        color = glowColor
        alpha = 80
        setShadowLayer(20f, 0f, 0f, glowColor)
    }

    // Corner radius for rounded rectangle
    private val cornerRadius = 14f

    /**
     * Move paddle to follow touch position
     * Constrained to screen boundaries
     */
    fun moveTo(targetX: Float) {
        // Clamp paddle position to screen bounds
        x = targetX.coerceIn(width / 2, screenWidth - width / 2)
    }

    /**
     * Draw the paddle as a rounded rectangle with neon purple glow
     */
    fun draw(canvas: Canvas) {
        val paddleRect = rect
        
        // Draw outer neon glow
        canvas.drawRoundRect(
            RectF(paddleRect.left - 6, paddleRect.top - 6, paddleRect.right + 6, paddleRect.bottom + 6),
            cornerRadius + 4, cornerRadius + 4, glowPaint
        )
        
        // Draw main paddle with purple gradient
        paint.shader = LinearGradient(
            paddleRect.left, paddleRect.top,
            paddleRect.left, paddleRect.bottom,
            intArrayOf(paddleColorLight, paddleColor, paddleColorDark),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(paddleRect, cornerRadius, cornerRadius, paint)
    }

    /**
     * Check if ball collides with paddle
     * Returns true if collision detected
     */
    fun checkCollision(ball: Ball): Boolean {
        val paddleRect = rect
        
        // Find the closest point on the paddle to the ball center
        val closestX = ball.x.coerceIn(paddleRect.left, paddleRect.right)
        val closestY = ball.y.coerceIn(paddleRect.top, paddleRect.bottom)
        
        // Calculate distance from ball center to closest point
        val distanceX = ball.x - closestX
        val distanceY = ball.y - closestY
        val distance = kotlin.math.sqrt(distanceX * distanceX + distanceY * distanceY)
        
        return distance <= ball.radius
    }

    /**
     * Handle ball collision with paddle
     * Adjusts ball velocity based on where it hits the paddle
     */
    fun handleCollision(ball: Ball) {
        if (!checkCollision(ball)) return
        
        // Calculate hit position relative to paddle center (-1 to 1)
        val hitPosition = (ball.x - x) / (width / 2)
        
        // Adjust ball angle based on hit position
        // Hitting edges deflects ball more horizontally
        val maxBounceAngle = Math.PI / 3 // 60 degrees max deflection
        val bounceAngle = hitPosition * maxBounceAngle
        
        // Calculate current speed
        val speed = kotlin.math.sqrt(ball.velocityX * ball.velocityX + ball.velocityY * ball.velocityY)
        
        // Set new velocity (always bounce upward)
        ball.velocityX = (speed * kotlin.math.sin(bounceAngle)).toFloat()
        ball.velocityY = -(speed * kotlin.math.cos(bounceAngle)).toFloat()
        
        // Ensure ball is above paddle to prevent multiple collisions
        ball.y = rect.top - ball.radius - 1
    }

    /**
     * Reset paddle to center position
     */
    fun reset(screenWidth: Float, screenHeight: Float) {
        this.screenWidth = screenWidth
        x = screenWidth / 2
        y = screenHeight - 150f
    }
}
