package com.example.linevsball

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * FloatingText represents a score/combo popup that floats upward and fades out.
 * Used to show "+10" effects when lines are destroyed and "COMBO x2!" messages.
 * Enhanced with neon color scheme.
 */
data class FloatingText(
    var x: Float,           // X position
    var y: Float,           // Y position
    val text: String,       // Text to display (e.g., "+10", "COMBO x2!")
    var alpha: Int = 255,   // Current opacity (255 = fully visible)
    var lifetime: Float = 1f, // Remaining lifetime (1.0 = full, 0 = expired)
    val isCombo: Boolean = false,  // Is this a combo announcement?
    var scale: Float = 1f   // Scale for pop effect
) {
    // Neon retrowave colors
    private val colorCyan = Color.rgb(0, 255, 255)      // Neon cyan
    private val colorMagenta = Color.rgb(255, 0, 255)   // Magenta for combos
    private val colorRed = Color.rgb(255, 80, 80)       // Red for life lost
    private val colorGold = Color.rgb(255, 215, 0)      // Gold for bonus
    
    // Movement speed upward
    private val floatSpeed: Float = if (isCombo) 1.5f else 2.5f
    
    // Fade speed per frame
    private val fadeSpeed: Float = if (isCombo) 0.012f else 0.025f
    
    // Scale animation
    private var scaleVelocity: Float = 0f
    
    // Determine color based on text content
    private fun getTextColor(): Int = when {
        text.contains("-1") || text.contains("−1") -> colorRed  // Life lost (red)
        text.contains("+1 LIFE") -> Color.rgb(100, 255, 100)    // Life gained (green)
        text.contains("LEVEL") -> colorGold                      // Level up (gold)
        text.contains("SHIELD") -> Color.rgb(0, 200, 255)
        isCombo -> colorMagenta
        text.contains("GOLD") || text.contains("50") -> colorGold
        else -> colorCyan
    }
    
    // Paint for drawing text - LARGER sizes
    private val paint = Paint().apply {
        isAntiAlias = true
        val textColor = getTextColor()
        color = textColor
        textAlign = Paint.Align.CENTER
        textSize = if (isCombo) 80f else 60f  // LARGER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(if (isCombo) 25f else 15f, 0f, 0f, textColor)
    }

    /**
     * Update floating text position, opacity, and scale
     */
    fun update() {
        // Float upward
        y -= floatSpeed
        
        // Fade out
        lifetime -= fadeSpeed
        alpha = (lifetime * 255).toInt().coerceIn(0, 255)
        
        // Scale animation (pop in then settle)
        if (scale > 1f) {
            scaleVelocity += (1f - scale) * 0.15f
            scaleVelocity *= 0.8f
            scale += scaleVelocity
            if (kotlin.math.abs(scale - 1f) < 0.01f) {
                scale = 1f
            }
        }
    }

    /**
     * Draw the floating text with scale effect
     */
    fun draw(canvas: Canvas) {
        paint.alpha = alpha
        
        // Apply scale transform
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        canvas.translate(-x, -y)
        
        canvas.drawText(text, x, y, paint)
        
        canvas.restore()
    }

    /**
     * Check if text has expired
     */
    fun isExpired(): Boolean = lifetime <= 0f

    companion object {
        /**
         * Create a floating score text at the specified position
         * Handles special cases: -1 = life lost, 0 = shield blocked
         */
        fun createScorePopup(x: Float, y: Float, points: Int, hasCombo: Boolean = false): FloatingText {
            val displayText = when {
                points == -1 -> "−1 LIFE!"
                points == 0 -> "SHIELD!"
                hasCombo && points >= 50 -> "+$points GOLD!"
                else -> "+$points"
            }
            return FloatingText(
                x = x,
                y = y,
                text = displayText,
                isCombo = points == -1 || points == 0,  // Make life/shield messages more prominent
                scale = if (points == -1) 1.5f else 1.3f  // Larger for life lost
            )
        }
        
        /**
         * Create a floating text with custom message (for LEVEL UP, +1 LIFE etc.)
         */
        fun createScorePopup(x: Float, y: Float, message: String): FloatingText {
            val isImportant = message.contains("LEVEL") || message.contains("LIFE")
            return FloatingText(
                x = x,
                y = y,
                text = message,
                isCombo = isImportant,
                scale = if (isImportant) 1.5f else 1.3f
            )
        }
        
        /**
         * Create a combo announcement popup
         * Displays "COMBO x2!" etc. with electric pink color
         */
        fun createComboPopup(x: Float, y: Float, multiplier: Int): FloatingText {
            return FloatingText(
                x = x,
                y = y,
                text = "COMBO x$multiplier!",
                lifetime = 1.5f,  // Longer duration for combo text
                isCombo = true,
                scale = 1.5f  // Start larger for dramatic effect
            )
        }
    }
}
