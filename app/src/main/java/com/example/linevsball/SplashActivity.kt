package com.example.linevsball

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * SplashActivity - Beautiful animated splash screen for Ball vs Lines
 * Features neon glow effects, pulsing icon, and smooth transitions
 */
class SplashActivity : ComponentActivity() {

    private val splashDuration = 2500L  // 2.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable fullscreen immersive mode
        setupFullscreen()
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Create splash screen programmatically with neon theme
        val splashView = createSplashView()
        setContentView(splashView)
        
        // Start animations
        startAnimations(splashView)
        
        // Navigate to main activity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, splashDuration)
    }
    
    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    private fun createSplashView(): View {
        val density = resources.displayMetrics.density
        
        // Root container with gradient background
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(10, 5, 20))  // Deep purple-black
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        // Center content container
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        
        // App icon with glow effect
        val iconContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (180 * density).toInt(),
                (180 * density).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (40 * density).toInt()
            }
        }
        
        // Glow background behind icon
        val glowView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (200 * density).toInt(),
                (200 * density).toInt()
            ).apply {
                gravity = Gravity.CENTER
            }
            setBackgroundColor(Color.rgb(0, 255, 255))
            alpha = 0.3f
            tag = "glow"
        }
        
        // App icon
        val iconView = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                (150 * density).toInt(),
                (150 * density).toInt()
            ).apply {
                gravity = Gravity.CENTER
            }
            tag = "icon"
        }
        
        iconContainer.addView(glowView)
        iconContainer.addView(iconView)
        
        // Title text with neon cyan color
        val titleText = TextView(this).apply {
            text = "BALL vs LINES"
            textSize = 42f
            setTextColor(Color.rgb(0, 255, 255))  // Neon cyan
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(20f, 0f, 0f, Color.rgb(0, 255, 255))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * density).toInt()
            }
            tag = "title"
            alpha = 0f
        }
        
        // Subtitle with neon purple
        val subtitleText = TextView(this).apply {
            text = "NEON ARCADE"
            textSize = 22f
            setTextColor(Color.rgb(157, 0, 255))  // Neon purple
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(15f, 0f, 0f, Color.rgb(157, 0, 255))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (60 * density).toInt()
            }
            tag = "subtitle"
            alpha = 0f
        }
        
        // Loading indicator
        val loadingText = TextView(this).apply {
            text = "Loading..."
            textSize = 16f
            setTextColor(Color.rgb(180, 180, 180))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            tag = "loading"
            alpha = 0f
        }
        
        contentLayout.addView(iconContainer)
        contentLayout.addView(titleText)
        contentLayout.addView(subtitleText)
        contentLayout.addView(loadingText)
        
        rootLayout.addView(contentLayout)
        
        return rootLayout
    }
    
    private fun startAnimations(rootView: View) {
        val iconView = rootView.findViewWithTag<ImageView>("icon")
        val glowView = rootView.findViewWithTag<View>("glow")
        val titleText = rootView.findViewWithTag<TextView>("title")
        val subtitleText = rootView.findViewWithTag<TextView>("subtitle")
        val loadingText = rootView.findViewWithTag<TextView>("loading")
        
        // Icon scale animation (bounce in)
        iconView?.let { icon ->
            icon.scaleX = 0f
            icon.scaleY = 0f
            
            val scaleX = ObjectAnimator.ofFloat(icon, "scaleX", 0f, 1f).apply {
                duration = 600
                interpolator = OvershootInterpolator(1.5f)
            }
            val scaleY = ObjectAnimator.ofFloat(icon, "scaleY", 0f, 1f).apply {
                duration = 600
                interpolator = OvershootInterpolator(1.5f)
            }
            
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                start()
            }
        }
        
        // Glow pulse animation
        glowView?.let { glow ->
            val pulseX = ObjectAnimator.ofFloat(glow, "scaleX", 0.8f, 1.2f, 0.8f).apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            val pulseY = ObjectAnimator.ofFloat(glow, "scaleY", 0.8f, 1.2f, 0.8f).apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            val pulseAlpha = ObjectAnimator.ofFloat(glow, "alpha", 0.2f, 0.5f, 0.2f).apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            AnimatorSet().apply {
                playTogether(pulseX, pulseY, pulseAlpha)
                startDelay = 300
                start()
            }
        }
        
        // Title fade in and slide up
        titleText?.let { title ->
            title.translationY = 50f
            
            val fadeIn = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f).apply {
                duration = 500
            }
            val slideUp = ObjectAnimator.ofFloat(title, "translationY", 50f, 0f).apply {
                duration = 500
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            AnimatorSet().apply {
                playTogether(fadeIn, slideUp)
                startDelay = 400
                start()
            }
        }
        
        // Subtitle fade in
        subtitleText?.let { subtitle ->
            subtitle.translationY = 30f
            
            val fadeIn = ObjectAnimator.ofFloat(subtitle, "alpha", 0f, 1f).apply {
                duration = 500
            }
            val slideUp = ObjectAnimator.ofFloat(subtitle, "translationY", 30f, 0f).apply {
                duration = 500
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            AnimatorSet().apply {
                playTogether(fadeIn, slideUp)
                startDelay = 600
                start()
            }
        }
        
        // Loading text fade in
        loadingText?.let { loading ->
            val fadeIn = ObjectAnimator.ofFloat(loading, "alpha", 0f, 1f).apply {
                duration = 400
            }
            
            fadeIn.startDelay = 800
            fadeIn.start()
            
            // Pulsing effect for loading text
            val pulse = ObjectAnimator.ofFloat(loading, "alpha", 0.4f, 1f, 0.4f).apply {
                duration = 1000
                repeatCount = ObjectAnimator.INFINITE
                startDelay = 1200
            }
            pulse.start()
        }
    }
}
