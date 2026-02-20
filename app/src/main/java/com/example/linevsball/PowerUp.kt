package com.example.linevsball

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.random.Random

/**
 * Power-up types available in the game
 */
enum class PowerUpType(val color: Int, val symbol: String, val duration: Long) {
    SHIELD(Color.rgb(0, 200, 255), "🛡", 5000L),           // Cyan - Protects from one hit
    MULTI_BALL(Color.rgb(255, 200, 0), "+", 0L),           // Gold - Adds extra ball
    SLOW_MO(Color.rgb(180, 100, 255), "⏱", 4000L),         // Purple - Slows lines
    BIG_PADDLE(Color.rgb(0, 255, 150), "▬", 6000L),        // Green - Larger paddle
    MAGNET(Color.rgb(255, 100, 200), "◎", 5000L),          // Pink - Ball attracted to paddle
    SCORE_BOOST(Color.rgb(255, 215, 0), "★", 5000L)        // Gold - 2x score
}

/**
 * PowerUp class for collectible power-ups that spawn from destroyed lines
 */
data class PowerUp(
    var x: Float,
    var y: Float,
    var velocityY: Float = 3f,
    val type: PowerUpType,
    val size: Float = 40f
) {
    private var pulsePhase: Float = 0f
    private var rotation: Float = 0f
    
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val glowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = size * 0.6f
        color = Color.WHITE
        isFakeBoldText = true
    }
    
    val rect: RectF
        get() = RectF(x - size/2, y - size/2, x + size/2, y + size/2)
    
    fun update() {
        y += velocityY
        pulsePhase += 0.15f
        rotation += 2f
    }
    
    fun draw(canvas: Canvas) {
        val pulse = 1f + 0.15f * kotlin.math.sin(pulsePhase)
        val drawSize = size * pulse
        
        // Draw outer glow
        glowPaint.color = type.color
        glowPaint.alpha = 80
        canvas.drawCircle(x, y, drawSize * 1.3f, glowPaint)
        
        // Draw main circle
        paint.color = type.color
        paint.alpha = 220
        paint.setShadowLayer(15f, 0f, 0f, type.color)
        canvas.drawCircle(x, y, drawSize / 2, paint)
        
        // Draw inner highlight
        paint.color = Color.WHITE
        paint.alpha = 150
        paint.clearShadowLayer()
        canvas.drawCircle(x - drawSize * 0.15f, y - drawSize * 0.15f, drawSize * 0.15f, paint)
        
        // Draw symbol
        textPaint.textSize = drawSize * 0.5f
        canvas.drawText(type.symbol, x, y + textPaint.textSize * 0.35f, textPaint)
    }
    
    fun isOffScreen(screenHeight: Float): Boolean = y > screenHeight + size
    
    companion object {
        /**
         * Randomly spawn a power-up (15% chance)
         */
        fun maybeSpawn(x: Float, y: Float, scaleFactor: Float = 1f): PowerUp? {
            if (Random.nextFloat() > 0.15f) return null  // 15% spawn chance
            
            val types = PowerUpType.values()
            val type = types[Random.nextInt(types.size)]
            
            return PowerUp(
                x = x,
                y = y,
                velocityY = 2.5f * scaleFactor,
                type = type,
                size = 40f * scaleFactor
            )
        }
        
        /**
         * Force spawn a specific power-up type
         */
        fun spawn(x: Float, y: Float, type: PowerUpType, scaleFactor: Float = 1f): PowerUp {
            return PowerUp(
                x = x,
                y = y,
                velocityY = 2.5f * scaleFactor,
                type = type,
                size = 40f * scaleFactor
            )
        }
    }
}
