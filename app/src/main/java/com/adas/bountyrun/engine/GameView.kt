package com.adas.bountyrun.engine

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.adas.bountyrun.adas.AdasEvent
import com.adas.bountyrun.config.GameSetup
import com.adas.bountyrun.core.GameOverReason
import com.adas.bountyrun.core.LevelReport
import com.adas.bountyrun.core.SessionPhase

/**
 * SurfaceView host running the fixed-step game loop on its own thread (spec §1).
 * Input is fed in from the activity's on-screen controls; results (level report,
 * game over, ADAS events for voice/haptics) are posted back on the main thread.
 */
class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    lateinit var world: GameWorld
        private set
    val renderer = Renderer()

    // Driver inputs (set by the control buttons each frame).
    @Volatile var steerInput = 0f
    @Volatile var throttleInput = 0f
    @Volatile var brakeInput = 0f

    // Callbacks to the activity (invoked on the main thread).
    var onAdasEvent: ((AdasEvent) -> Unit)? = null
    var onLevelComplete: ((LevelReport) -> Unit)? = null
    var onGameOver: ((GameOverReason) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var paused = false
    private var thread: Thread? = null
    private var levelReported = false
    private var gameOverReported = false

    init {
        holder.addCallback(this)
    }

    fun initWorld(setup: GameSetup) {
        world = GameWorld(setup)
        world.startLevel()
        levelReported = false
        gameOverReported = false
    }

    fun setSensorOverlay(on: Boolean) { renderer.showSensorOverlay = on }
    fun pauseGame() { paused = true }
    fun resumeGame() { paused = false }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "GameLoop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1_000_000_000f
            last = now
            // Clamp dt to keep the simulation stable after stalls (spec §28).
            if (dt > 0.05f) dt = 0.05f

            if (!paused && ::world.isInitialized) step(dt, System.currentTimeMillis())
            drawFrame(dt)

            // Aim for ~60 FPS.
            val frameMs = (System.nanoTime() - now) / 1_000_000
            val sleep = 16 - frameMs
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
        }
    }

    private fun step(dt: Float, nowMs: Long) {
        val p = world.player
        p.steerInput = steerInput
        p.throttleInput = throttleInput
        p.brakeInput = brakeInput

        world.update(dt, nowMs)

        // Drain ADAS events for voice + haptics.
        while (world.adas.events.isNotEmpty()) {
            val ev = world.adas.events.removeFirst()
            main.post { onAdasEvent?.invoke(ev) }
        }

        // Level completion.
        if (world.session.phase == SessionPhase.LEVEL_COMPLETE && !levelReported) {
            levelReported = true
            val report = world.lastReport
            if (report != null) main.post { onLevelComplete?.invoke(report) }
        }
        // Game over.
        if (world.session.isGameOver && !gameOverReported) {
            gameOverReported = true
            val reason = world.session.gameOverReason
            main.post { onGameOver?.invoke(reason) }
        }
    }

    private fun drawFrame(dt: Float) {
        val h = holder
        if (!h.surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = h.lockCanvas()
            if (canvas != null && ::world.isInitialized) {
                renderer.draw(canvas, world, width, height, dt)
            }
        } finally {
            if (canvas != null) try { h.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
        }
    }
}
