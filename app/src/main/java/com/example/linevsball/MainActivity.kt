package com.example.linevsball

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * MainActivity - Entry point for the Ball vs Lines game.
 * Sets up fullscreen immersive mode and hosts the GameView.
 */
class MainActivity : ComponentActivity() {

    // Reference to the game view for lifecycle management
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable fullscreen immersive mode
        setupFullscreen()

        // Keep screen on during gameplay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Create and set the game view as content
        gameView = GameView(this)
        setContentView(gameView)
    }

    /**
     * Set up fullscreen immersive mode to hide system bars
     */
    private fun setupFullscreen() {
        // Make content extend into system bars area
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Get the window insets controller
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Hide both the status bar and navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Set behavior for showing system bars temporarily on swipe
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * Pause game when activity is paused
     */
    override fun onPause() {
        super.onPause()
        if (::gameView.isInitialized) {
            gameView.pause()
        }
    }

    /**
     * Resume game when activity is resumed
     */
    override fun onResume() {
        super.onResume()
        // GameView handles resume in surfaceCreated callback
    }
}