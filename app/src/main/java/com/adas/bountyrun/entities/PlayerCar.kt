package com.adas.bountyrun.entities

import com.adas.bountyrun.config.VehicleSpec
import com.adas.bountyrun.core.DamagePart
import com.adas.bountyrun.core.IDamageable
import com.adas.bountyrun.engine.GameGeometry
import kotlin.math.abs

/**
 * The player-controlled vehicle: a custom vehicle-physics controller (spec §1)
 * plus a damage model (spec §13). Speeds are in km/h; steering is in lane units.
 */
class PlayerCar(private val spec: VehicleSpec, private val gripFactor: Float) : IDamageable {

    var speedKmh: Float = 0f
        private set
    /** Lateral position in lane units; 0 = centre of the road. */
    var laneX: Float = 0f
        private set

    val halfWidth = 0.34f
    val halfLength = 2.3f

    // ---- Driver inputs (set each frame by the input layer) ----
    /** -1 (full left) .. +1 (full right). */
    var steerInput: Float = 0f
    /** 0..1 throttle. */
    var throttleInput: Float = 0f
    /** 0..1 service brake. */
    var brakeInput: Float = 0f
    /** External braking demand from AEB (0..1), applied on top of the driver. */
    var assistBrake: Float = 0f
    /** Lane-keep / emergency-steer lateral nudge in lane units/sec. */
    var assistSteer: Float = 0f

    // ---- Damage model ----
    private val damage = HashMap<DamagePart, Float>()
    var health: Float = spec.maxHealth
        private set

    override val isDisabled: Boolean get() = health <= 0f

    /** Aggregated 0..1 wear used by the HUD. */
    val healthFraction: Float get() = (health / spec.maxHealth).coerceIn(0f, 1f)

    fun damageOf(part: DamagePart): Float = damage[part] ?: 0f

    override fun applyDamage(amount: Float, part: DamagePart) {
        if (amount <= 0f) return
        damage[part] = (damage[part] ?: 0f) + amount
        health = (health - amount).coerceAtLeast(0f)
    }

    /** Effective braking scale — worn brakes stop the car more slowly (spec §13). */
    private fun brakeEfficiency(): Float {
        val brakeWear = (damage[DamagePart.BRAKES] ?: 0f)
        return (1f - brakeWear / 120f).coerceIn(0.45f, 1f)
    }

    /** Effective steer scale — steering/tyre damage reduces control (spec §13). */
    private fun steerEfficiency(): Float {
        val steerWear = (damage[DamagePart.STEERING] ?: 0f) + (damage[DamagePart.TYRES] ?: 0f)
        return (1f - steerWear / 150f).coerceIn(0.5f, 1f)
    }

    /** Sensor performance multiplier — dirty/broken sensors degrade ADAS (spec §13). */
    fun sensorHealthFraction(): Float {
        val sensorWear = (damage[DamagePart.ADAS_SENSORS] ?: 0f) +
            (damage[DamagePart.CAMERA] ?: 0f) + (damage[DamagePart.RADAR] ?: 0f)
        return (1f - sensorWear / 120f).coerceIn(0.3f, 1f)
    }

    /** Integrate one physics step. */
    fun update(dt: Float, topSpeedCapKmh: Float) {
        // Longitudinal dynamics.
        val accel = throttleInput * spec.accelerationKmhPerSec
        val totalBrake = (brakeInput + assistBrake).coerceIn(0f, 1f)
        val decel = totalBrake * spec.brakeKmhPerSec * brakeEfficiency()
        val coast = if (throttleInput <= 0.01f && totalBrake <= 0.01f) spec.coastDragKmhPerSec else 0f

        speedKmh += (accel - decel - coast) * dt
        val cap = minOf(spec.topSpeedKmh, topSpeedCapKmh)
        speedKmh = speedKmh.coerceIn(0f, cap)

        // Lateral dynamics. Grip (weather) and damage scale responsiveness.
        val steer = (steerInput * spec.steerRate * steerEfficiency() * gripFactor)
        laneX += steer * dt + assistSteer * dt
        laneX = laneX.coerceIn(-GameGeometry.ROAD_HALF_UNITS - 0.35f, GameGeometry.ROAD_HALF_UNITS + 0.35f)

        // Consume one-shot assists.
        assistBrake = 0f
        assistSteer = 0f
    }

    /** Distance from the nearest lane centre, normalised so 1.0 == half a lane (LDW/LKA). */
    fun laneOffsetNormalized(): Float {
        val nearestLane = nearestLaneCenter(laneX)
        val half = GameGeometry.LANE_WIDTH_UNITS / 2f
        return (laneX - nearestLane) / half
    }

    /** Signed direction (+1 right, -1 left) back toward the nearest lane centre. */
    fun directionToLaneCenter(): Float {
        val nearest = nearestLaneCenter(laneX)
        return if (nearest >= laneX) 1f else -1f
    }

    private fun nearestLaneCenter(x: Float): Float {
        val centers = floatArrayOf(-1f, 0f, 1f)
        var best = centers[0]
        var bestD = abs(x - best)
        for (c in centers) {
            val d = abs(x - c)
            if (d < bestD) { best = c; bestD = d }
        }
        return best
    }

    /** Emergency impulse toward the safe side (Emergency Steering Assist). */
    fun nudgeLateral(units: Float) { laneX += units }

    fun reset(startLaneX: Float = 0f) {
        speedKmh = 0f
        laneX = startLaneX
        steerInput = 0f; throttleInput = 0f; brakeInput = 0f
        assistBrake = 0f; assistSteer = 0f
    }
}
