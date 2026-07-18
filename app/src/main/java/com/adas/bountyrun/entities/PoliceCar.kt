package com.adas.bountyrun.entities

import com.adas.bountyrun.core.DetectKind
import com.adas.bountyrun.core.IDetectable
import kotlin.math.abs
import kotlin.math.sign

/**
 * A pursuing police unit with a small state machine (spec §19 Police AI).
 * Position is tracked as a relative Z to the player (negative = behind). Police
 * try to close the gap and align laterally to contain the player, but avoid
 * deliberately ramming (they ease off when very close).
 */
class PoliceCar : IDetectable {

    enum class State { PURSUIT, INTERCEPT, CONTAINMENT, ARREST }

    override var worldZ: Float = -60f
    override var laneX: Float = 0f
    override var speedKmh: Float = 0f
    override val halfWidth = 0.34f
    override val halfLength = 2.3f
    override var active: Boolean = false
    override val kind: DetectKind = DetectKind.POLICE

    var state: State = State.PURSUIT
        private set

    fun deploy(startZ: Float, laneX: Float) {
        this.worldZ = startZ
        this.laneX = laneX
        this.speedKmh = 0f
        this.state = State.PURSUIT
        this.active = true
    }

    /**
     * Update pursuit. [wantedLevel] scales how aggressively police close in.
     * Returns true when this unit has contained the player (arrest).
     */
    fun update(dt: Float, playerLaneX: Float, playerSpeedKmh: Float, wantedLevel: Int): Boolean {
        if (!active) return false

        // Target speed: exceed the player so the gap closes, scaled by wanted level.
        val overspeed = 8f + wantedLevel * 6f
        val target = playerSpeedKmh + overspeed
        speedKmh += ((target - speedKmh).coerceIn(-30f, 30f)) * dt * 1.5f
        speedKmh = speedKmh.coerceAtLeast(0f)

        // Close the relative gap (both moving forward; police faster => Z rises toward 0).
        val closingMs = (speedKmh - playerSpeedKmh) * com.adas.bountyrun.engine.GameGeometry.KMH_TO_MS
        worldZ += closingMs * dt

        // Lateral containment — align to the player's lane.
        val dx = playerLaneX - laneX
        laneX += sign(dx) * minOf(abs(dx), 1.4f * dt)

        // State transitions based on the closing gap.
        val gap = abs(worldZ)
        state = when {
            gap < 3.2f && abs(dx) < 0.6f -> State.ARREST
            gap < 9f -> State.CONTAINMENT
            gap < 30f -> State.INTERCEPT
            else -> State.PURSUIT
        }

        // Avoid ramming: ease off just before contact unless in arrest range.
        if (gap in 3.2f..5f && closingMs > 0) worldZ -= closingMs * dt * 0.5f

        return state == State.ARREST
    }
}
