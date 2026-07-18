package com.adas.bountyrun.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.adas.bountyrun.R

/**
 * Branded loading screen (spec §22 splash). Plays a cinematic entrance for the
 * emblem and wordmark, runs a short loading bar, then opens the main menu.
 * Drop the exact raster artwork at res/drawable-nodpi/logo.png and point the
 * emblem ImageView at it to use the original logo image (see docs).
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition for Android 12+
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        hideSystemBars()

        val emblem = findViewById<View>(R.id.logoEmblem)
        val adas = findViewById<View>(R.id.wordAdas)
        val bounty = findViewById<View>(R.id.wordBounty)
        val run = findViewById<View>(R.id.wordRun)
        val tagline = findViewById<View>(R.id.tagline)
        val bar = findViewById<ProgressBar>(R.id.loadBar)

        // Emblem: scale + fade in with a subtle overshoot.
        emblem.alpha = 0f
        emblem.scaleX = 0.6f; emblem.scaleY = 0.6f
        emblem.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(1.2f)).setDuration(650).start()
        // Continuous gentle pulse to read as "loading".
        ObjectAnimator.ofFloat(emblem, "rotationY", 0f, 12f, 0f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; start()
        }

        // Wordmark: staggered slide-up.
        slideUp(adas, 250); slideUp(bounty, 400); slideUp(run, 520)
        tagline.alpha = 0f
        tagline.animate().alpha(0.9f).setStartDelay(750).setDuration(500).start()

        // Loading bar drives the transition to the menu.
        ValueAnimator.ofInt(0, 100).apply {
            duration = 2200
            interpolator = DecelerateInterpolator()
            addUpdateListener { bar.progress = it.animatedValue as Int }
            start()
        }

        bar.postDelayed({ goToMenu() }, 2400)
    }

    private fun slideUp(v: View, delay: Long) {
        v.alpha = 0f
        v.translationY = 40f
        v.animate().alpha(1f).translationY(0f)
            .setInterpolator(DecelerateInterpolator()).setStartDelay(delay).setDuration(500).start()
    }

    private fun goToMenu() {
        startActivity(Intent(this, MenuActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }
}
