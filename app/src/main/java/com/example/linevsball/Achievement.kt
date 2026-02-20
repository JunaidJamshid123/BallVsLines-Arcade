package com.example.linevsball

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Achievement definitions
 */
enum class AchievementType(
    val title: String,
    val description: String,
    val icon: String,
    val color: Int
) {
    FIRST_HIT("First Blood", "Destroy your first line", "💥", Color.rgb(255, 100, 100)),
    COMBO_MASTER("Combo Master", "Get a x3 combo", "🔥", Color.rgb(255, 150, 0)),
    SCORE_100("Century", "Score 100 points", "💯", Color.rgb(0, 200, 255)),
    SCORE_500("High Roller", "Score 500 points", "⭐", Color.rgb(255, 215, 0)),
    SCORE_1000("Legend", "Score 1000 points", "🏆", Color.rgb(255, 0, 255)),
    SURVIVOR_30("Survivor", "Survive 30 seconds", "⏱", Color.rgb(0, 255, 200)),
    SURVIVOR_60("Endurance", "Survive 60 seconds", "💪", Color.rgb(180, 100, 255)),
    MULTI_BALL("Ball Master", "Have 3 balls at once", "🎱", Color.rgb(0, 255, 150)),
    PERFECT_10("Perfect 10", "Destroy 10 lines without missing", "✨", Color.rgb(255, 200, 100)),
    GOLD_HUNTER("Gold Hunter", "Destroy 5 gold lines in one game", "🥇", Color.rgb(255, 215, 0))
}

/**
 * Achievement notification popup that appears when achievement is unlocked
 */
data class AchievementPopup(
    val achievement: AchievementType,
    var displayTime: Float = 3f,  // Seconds to display
    var slideProgress: Float = 0f,  // 0 = hidden, 1 = fully visible
    var isSliding: Boolean = true
) {
    private val paint = Paint().apply {
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }
    
    private val subtitlePaint = Paint().apply {
        isAntiAlias = true
        color = Color.rgb(200, 200, 200)
        textAlign = Paint.Align.LEFT
    }
    
    private val iconPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    
    fun update(deltaTime: Float): Boolean {
        if (isSliding && slideProgress < 1f) {
            slideProgress = (slideProgress + deltaTime * 3f).coerceAtMost(1f)
        } else if (slideProgress >= 1f) {
            isSliding = false
            displayTime -= deltaTime
            if (displayTime <= 0.5f) {
                slideProgress = (slideProgress - deltaTime * 3f).coerceAtLeast(0f)
            }
        }
        return displayTime <= 0f && slideProgress <= 0f
    }
    
    fun draw(canvas: Canvas, screenWidth: Float, scaleFactor: Float) {
        val popupWidth = 340f * scaleFactor    // WIDER
        val popupHeight = 90f * scaleFactor    // TALLER
        val padding = 15f * scaleFactor
        
        // Slide in from right
        val xOffset = (1f - slideProgress) * (popupWidth + 20f)
        val x = screenWidth - popupWidth - padding + xOffset
        val y = 150f * scaleFactor
        
        // Background
        paint.color = Color.argb(230, 30, 30, 50)
        canvas.drawRoundRect(
            RectF(x, y, x + popupWidth, y + popupHeight),
            18f * scaleFactor, 18f * scaleFactor, paint
        )
        
        // Border glow
        paint.color = achievement.color
        paint.alpha = 180
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * scaleFactor
        canvas.drawRoundRect(
            RectF(x, y, x + popupWidth, y + popupHeight),
            18f * scaleFactor, 18f * scaleFactor, paint
        )
        paint.style = Paint.Style.FILL
        
        // Icon - LARGER
        iconPaint.textSize = 42f * scaleFactor
        canvas.drawText(achievement.icon, x + 35f * scaleFactor, y + 58f * scaleFactor, iconPaint)
        
        // Title - LARGER
        textPaint.textSize = 26f * scaleFactor
        textPaint.color = achievement.color
        canvas.drawText("🏅 ${achievement.title}", x + 70f * scaleFactor, y + 38f * scaleFactor, textPaint)
        
        // Description - LARGER
        subtitlePaint.textSize = 18f * scaleFactor
        canvas.drawText(achievement.description, x + 70f * scaleFactor, y + 65f * scaleFactor, subtitlePaint)
    }
}

/**
 * Achievement manager to track and unlock achievements
 */
class AchievementManager(private val prefs: SharedPreferences) {
    private val unlockedAchievements = mutableSetOf<AchievementType>()
    val pendingPopups = mutableListOf<AchievementPopup>()
    
    // Session tracking
    var linesDestroyedWithoutMiss = 0
    var goldLinesDestroyed = 0
    
    init {
        // Load unlocked achievements from preferences
        AchievementType.values().forEach { type ->
            if (prefs.getBoolean("achievement_${type.name}", false)) {
                unlockedAchievements.add(type)
            }
        }
    }
    
    fun isUnlocked(type: AchievementType): Boolean = type in unlockedAchievements
    
    fun unlock(type: AchievementType) {
        if (type !in unlockedAchievements) {
            unlockedAchievements.add(type)
            prefs.edit().putBoolean("achievement_${type.name}", true).apply()
            pendingPopups.add(AchievementPopup(type))
        }
    }
    
    fun checkAchievements(
        score: Int,
        comboMultiplier: Int,
        ballCount: Int,
        survivalTimeSeconds: Float,
        lineDestroyed: Boolean = false,
        isGoldLine: Boolean = false
    ) {
        // First hit
        if (lineDestroyed && !isUnlocked(AchievementType.FIRST_HIT)) {
            unlock(AchievementType.FIRST_HIT)
        }
        
        // Track lines destroyed without miss
        if (lineDestroyed) {
            linesDestroyedWithoutMiss++
            if (linesDestroyedWithoutMiss >= 10 && !isUnlocked(AchievementType.PERFECT_10)) {
                unlock(AchievementType.PERFECT_10)
            }
        }
        
        // Track gold lines
        if (isGoldLine) {
            goldLinesDestroyed++
            if (goldLinesDestroyed >= 5 && !isUnlocked(AchievementType.GOLD_HUNTER)) {
                unlock(AchievementType.GOLD_HUNTER)
            }
        }
        
        // Combo achievements
        if (comboMultiplier >= 3 && !isUnlocked(AchievementType.COMBO_MASTER)) {
            unlock(AchievementType.COMBO_MASTER)
        }
        
        // Score achievements
        if (score >= 100 && !isUnlocked(AchievementType.SCORE_100)) {
            unlock(AchievementType.SCORE_100)
        }
        if (score >= 500 && !isUnlocked(AchievementType.SCORE_500)) {
            unlock(AchievementType.SCORE_500)
        }
        if (score >= 1000 && !isUnlocked(AchievementType.SCORE_1000)) {
            unlock(AchievementType.SCORE_1000)
        }
        
        // Survival achievements
        if (survivalTimeSeconds >= 30 && !isUnlocked(AchievementType.SURVIVOR_30)) {
            unlock(AchievementType.SURVIVOR_30)
        }
        if (survivalTimeSeconds >= 60 && !isUnlocked(AchievementType.SURVIVOR_60)) {
            unlock(AchievementType.SURVIVOR_60)
        }
        
        // Multi-ball achievement
        if (ballCount >= 3 && !isUnlocked(AchievementType.MULTI_BALL)) {
            unlock(AchievementType.MULTI_BALL)
        }
    }
    
    fun onLineMissed() {
        linesDestroyedWithoutMiss = 0
    }
    
    fun resetSession() {
        linesDestroyedWithoutMiss = 0
        goldLinesDestroyed = 0
    }
    
    fun updatePopups(deltaTime: Float) {
        pendingPopups.removeAll { it.update(deltaTime) }
    }
    
    fun drawPopups(canvas: Canvas, screenWidth: Float, scaleFactor: Float) {
        pendingPopups.forEach { it.draw(canvas, screenWidth, scaleFactor) }
    }
    
    fun getUnlockedCount(): Int = unlockedAchievements.size
    fun getTotalCount(): Int = AchievementType.values().size
}
