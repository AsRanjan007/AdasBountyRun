package com.adas.bountyrun.entities

import com.adas.bountyrun.config.HazardKind
import com.adas.bountyrun.core.DetectKind
import com.adas.bountyrun.core.ICollisionRisk
import com.adas.bountyrun.core.IDetectable
import kotlin.math.abs

/**
 * A single reusable road object: background traffic, a vulnerable road user, an
 * animal or a static/kinematic hazard (spec §6). One flexible class keeps the
 * object pool simple; behaviour is selected by [behavior] and [kind].
 *
 * Instances are pooled (spec §28) — always reset via [spawn] before use and
 * mark [active] = false to recycle.
 */
class RoadEntity : IDetectable, ICollisionRisk {

    enum class Behavior { LANE_FOLLOW, ONCOMING, CROSSING, WANDER, STATIC }

    override var worldZ: Float = 0f
    override var laneX: Float = 0f
    override var speedKmh: Float = 0f
    override var halfWidth: Float = 0.35f
    override var halfLength: Float = 2.2f
    override var active: Boolean = false
    override var kind: DetectKind = DetectKind.VEHICLE
    override var hazardKind: HazardKind? = null

    var behavior: Behavior = Behavior.LANE_FOLLOW

    /** Lateral drift velocity in lane units/sec (crossing / wandering). */
    var lateralVel: Float = 0f
    /** Target lane centre a lane-following vehicle steers toward. */
    var targetLaneX: Float = 0f
    /** True once this object has been scored (avoids double counting). */
    var scored: Boolean = false
    /** True once counted as "protected/avoided" so we reward only once. */
    var rewardedAvoidance: Boolean = false
    /** True once this object was on a collision course (used for fair scoring). */
    var enteredCorridor: Boolean = false
    /** Closest lateral gap to the player observed while ahead (near-miss test). */
    var minLateralGap: Float = Float.MAX_VALUE
    /** Colour hint for the renderer (ARGB). */
    var colorHint: Int = 0xFFB0BEC5.toInt()
    /** Display label used by the sensor overlay. */
    var label: String = ""
    /** Set true when this entity has stopped due to an accident ahead. */
    var halted: Boolean = false

    fun spawn(
        z: Float, laneX: Float, speedKmh: Float, kind: DetectKind,
        behavior: Behavior, halfWidth: Float, halfLength: Float,
        color: Int, label: String, hazard: HazardKind? = null
    ) {
        this.worldZ = z
        this.laneX = laneX
        this.speedKmh = speedKmh
        this.kind = kind
        this.behavior = behavior
        this.halfWidth = halfWidth
        this.halfLength = halfLength
        this.colorHint = color
        this.label = label
        this.hazardKind = hazard
        this.targetLaneX = laneX
        this.lateralVel = 0f
        this.scored = false
        this.rewardedAvoidance = false
        this.enteredCorridor = false
        this.minLateralGap = Float.MAX_VALUE
        this.halted = false
        this.active = true
    }

    /**
     * Advance the entity. [playerSpeedKmh] closes/opens the relative gap; [dt] is
     * seconds. Behaviour AI (spec §19) is intentionally lightweight and fair —
     * crossing actors always leave a valid avoidance path.
     */
    fun update(dt: Float, playerSpeedKmh: Float) {
        if (!active) return
        val ownMs = (if (halted) 0f else speedKmh) * com.adas.bountyrun.engine.GameGeometry.KMH_TO_MS
        val playerMs = playerSpeedKmh * com.adas.bountyrun.engine.GameGeometry.KMH_TO_MS
        // Same-direction traffic recedes when player is faster; oncoming closes fast.
        val closingMs = when (behavior) {
            Behavior.ONCOMING -> -ownMs - playerMs
            else -> ownMs - playerMs
        }
        worldZ += closingMs * dt

        when (behavior) {
            Behavior.CROSSING, Behavior.WANDER -> {
                laneX += lateralVel * dt
                // Bounce wandering animals gently within the road envelope.
                if (behavior == Behavior.WANDER &&
                    abs(laneX) > com.adas.bountyrun.engine.GameGeometry.ROAD_HALF_UNITS
                ) {
                    lateralVel = -lateralVel
                }
            }
            Behavior.LANE_FOLLOW, Behavior.ONCOMING -> {
                // Gentle lane keeping toward the target lane centre.
                val diff = targetLaneX - laneX
                laneX += diff.coerceIn(-1f, 1f) * dt * 0.8f
            }
            Behavior.STATIC -> { /* stays put in the world; only Z changes */ }
        }
    }

    /** Rough overlap test against the player at (playerLaneX, playerHalf*). */
    fun overlapsPlayer(playerLaneX: Float, playerHalfW: Float, playerHalfL: Float): Boolean {
        if (!active) return false
        val zOverlap = abs(worldZ) < (halfLength + playerHalfL)
        val xOverlap = abs(laneX - playerLaneX) < (halfWidth + playerHalfW)
        return zOverlap && xOverlap
    }
}
