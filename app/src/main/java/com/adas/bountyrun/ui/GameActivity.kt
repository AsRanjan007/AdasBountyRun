package com.adas.bountyrun.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.adas.bountyrun.R
import com.adas.bountyrun.adas.AdasEvent
import com.adas.bountyrun.adas.WarningSeverity
import com.adas.bountyrun.audio.VoiceManager
import com.adas.bountyrun.config.GameSetup
import com.adas.bountyrun.core.GameOverReason
import com.adas.bountyrun.core.LevelReport
import com.adas.bountyrun.engine.GameView

/**
 * Hosts the live simulation (spec §22). Wires on-screen controls to the game
 * loop, speaks ADAS alerts, and routes level-complete / game-over back to the
 * report screen. Landscape, immersive, hardware-accelerated.
 */
class GameActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var voice: VoiceManager
    private var vibrator: Vibrator? = null
    private var lastReport: LevelReport? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        hideSystemBars()

        voice = VoiceManager(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        gameView = findViewById(R.id.gameView)
        gameView.initWorld(GameSetup.current)
        wireCallbacks()
        wireControls()
    }

    private fun wireCallbacks() {
        gameView.onAdasEvent = { ev -> onAdas(ev) }
        gameView.onLevelComplete = { report -> showLevelComplete(report) }
        gameView.onGameOver = { reason -> showGameOver(reason) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireControls() {
        holdControl(R.id.btnLeft) { down -> gameView.steerInput = if (down) -1f else 0f }
        holdControl(R.id.btnRight) { down -> gameView.steerInput = if (down) 1f else 0f }
        holdControl(R.id.btnGas) { down -> gameView.throttleInput = if (down) 1f else 0f }
        holdControl(R.id.btnBrake) { down -> gameView.brakeInput = if (down) 1f else 0f }

        findViewById<ToggleButton>(R.id.btnSensor).setOnCheckedChangeListener { _, checked ->
            gameView.setSensorOverlay(checked)
        }
        findViewById<ToggleButton>(R.id.btnVoice).apply {
            isChecked = true
            setOnCheckedChangeListener { _, checked -> voice.enabled = checked }
        }
        findViewById<Button>(R.id.btnPause).setOnClickListener { showPauseDialog() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun holdControl(id: Int, onState: (Boolean) -> Unit) {
        findViewById<View>(id).setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { onState(true); v.alpha = 0.7f; true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { onState(false); v.alpha = 1f; true }
                else -> false
            }
        }
    }

    private fun onAdas(ev: AdasEvent) {
        voice.speak(ev.voiceKey)
        if (ev.severity == WarningSeverity.DANGER) haptic(90)
        else if (ev.severity == WarningSeverity.HIGH) haptic(45)
    }

    private fun haptic(ms: Long) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }

    private fun showLevelComplete(report: LevelReport) {
        lastReport = report
        gameView.pauseGame()
        ReportActivity.pendingReport = report
        ReportActivity.wasGameOver = false
        startActivity(Intent(this, ReportActivity::class.java))
        finish()
    }

    private fun showGameOver(reason: GameOverReason) {
        gameView.pauseGame()
        Toast.makeText(this, reason.message, Toast.LENGTH_SHORT).show()
        // Build a final report snapshot for the game-over screen.
        val session = gameView.world.session
        val report = session.report.build(
            level = session.level.level,
            bountyEarned = session.bounty.snapshotEarned(),
            bountyLost = session.bounty.snapshotLost(),
            bountyRemaining = session.bounty.bounty,
            policeResponse = if (session.wanted.isWanted) "Pursuit ended the run" else "No pursuit",
            passed = false
        )
        ReportActivity.pendingReport = report
        ReportActivity.wasGameOver = true
        ReportActivity.gameOverMessage = reason.message
        startActivity(Intent(this, ReportActivity::class.java))
        finish()
    }

    private fun showPauseDialog() {
        gameView.pauseGame()
        AlertDialog.Builder(this)
            .setTitle("Paused")
            .setMessage("ADAS assists the driver. It does not replace attentive and responsible driving.")
            .setPositiveButton("Resume") { d, _ -> d.dismiss(); gameView.resumeGame() }
            .setNegativeButton("Quit to Menu") { _, _ ->
                startActivity(Intent(this, MenuActivity::class.java)); finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onPause() { super.onPause(); if (::gameView.isInitialized) gameView.pauseGame() }
    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (::gameView.isInitialized) gameView.resumeGame()
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.shutdown()
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
