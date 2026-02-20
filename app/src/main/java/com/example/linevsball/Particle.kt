package com.example.linevsball

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Particle class for impact burst effects.
 * Small circles that explode outward and fade.
 * Uses hot pink color to match retrowave theme.
 */
data class Particle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var radius: Float,
    var alpha: Int = 255,
    var lifetime: Float = 1f,
    val color: Int = Color.rgb(255, 0, 128)  // Hot pink (#FF0080)
) {
    private val fadeSpeed = 0.08f
    private val friction = 0.95f
    
    private val paint = Paint().apply {
        isAntiAlias = true
    }
    
    fun update() {
        // Apply velocity
        x += velocityX
        y += velocityY
        
        // Apply friction (slow down)
        velocityX *= friction
        velocityY *= friction
        
        // Shrink radius
        radius *= 0.95f
        
        // Fade out
        lifetime -= fadeSpeed
        alpha = (lifetime * 255).toInt().coerceIn(0, 255)
    }
    
    fun draw(canvas: Canvas) {
        paint.color = color
        paint.alpha = alpha
        canvas.drawCircle(x, y, radius, paint)
    }
    
    fun isExpired(): Boolean = lifetime <= 0f || radius < 1f
    
    companion object {
        /**
         * Create a burst of particles at a position
         * @param x Center X position
         * @param y Center Y position
         * @param count Number of particles (5-10 for lightweight effect)
         * @param color Particle color (defaults to hot pink)
         * @return List of particles
         */
        fun createBurst(x: Float, y: Float, count: Int = 8, color: Int = Color.rgb(255, 0, 128)): List<Particle> {
            val particles = mutableListOf<Particle>()
            
            for (i in 0 until count) {
                // Random angle for each particle
                val angle = (i.toFloat() / count) * 2 * Math.PI + Random.nextDouble(-0.3, 0.3)
                val speed = Random.nextFloat() * 10f + 5f
                
                particles.add(Particle(
                    x = x + Random.nextFloat() * 10 - 5,
                    y = y + Random.nextFloat() * 10 - 5,
                    velocityX = (cos(angle) * speed).toFloat(),
                    velocityY = (sin(angle) * speed).toFloat(),
                    radius = Random.nextFloat() * 7f + 4f,
                    color = color
                ))
            }
            
            return particles
        }
        
        /**
         * Create cyan particles (for ball hit effects)
         */
        fun createCyanBurst(x: Float, y: Float, count: Int = 6): List<Particle> {
            return createBurst(x, y, count, Color.rgb(0, 255, 255))
        }
        
        /**
         * Create magenta particles (for combo effects)
         */
        fun createMagentaBurst(x: Float, y: Float, count: Int = 10): List<Particle> {
            return createBurst(x, y, count, Color.rgb(255, 0, 255))
        }
    }
}
