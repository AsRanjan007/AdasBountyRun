package com.adas.bountyrun.adas

import com.adas.bountyrun.core.DetectKind
import com.adas.bountyrun.core.IDetectable
import com.adas.bountyrun.engine.GameGeometry
import com.adas.bountyrun.entities.PlayerCar
import kotlin.math.abs

/** A perceived threat ahead of the player (spec §9/§10). */
data class ThreatInfo(
    val target: IDetectable,
    val distanceM: Float,
    val closingMs: Float,
    val ttc: Float,
    val inPath: Boolean
) {
    val kind: DetectKind get() = target.kind
}

/**
 * Simulates the perception stack (camera + radar) and reduces the world to the
 * decision-relevant signals ADAS needs (spec §10). Perception quality is scaled
 * by [sensorQuality] (weather × sensor damage), which shortens effective range —
 * a data-driven stand-in for degraded sensors (spec §13/§14).
 */
class SensorModel(private val baseRangeM: Float = 130f) {

    var sensorQuality: Float = 1f

    /** Effective detection range after weather / damage degradation. */
    val effectiveRange: Float get() = baseRangeM * sensorQuality

    /**
     * Find the most imminent in-path threat ahead of the player. Returns null if
     * nothing relevant is within range. [playerSpeedKmh] drives the closing math.
     */
    fun forwardThreat(
        player: PlayerCar,
        objects: List<IDetectable>,
        playerSpeedKmh: Float
    ): ThreatInfo? {
        var best: ThreatInfo? = null
        val range = effectiveRange
        for (o in objects) {
            if (!o.active) continue
            if (o.worldZ <= 0f || o.worldZ > range) continue
            val lateral = abs(o.laneX - player.laneX)
            val corridor = player.halfWidth + o.halfWidth + 0.15f
            val inPath = lateral <= corridor
            // Only consider near-path objects for the forward threat.
            if (lateral > corridor + 0.6f) continue

            val closing = closingMs(o, playerSpeedKmh)
            val distance = (o.worldZ - o.halfLength - player.halfLength).coerceAtLeast(0.1f)
            val ttc = if (closing > 0.1f) distance / closing else Float.MAX_VALUE
            val candidate = ThreatInfo(o, distance, closing, ttc, inPath)
            if (best == null || candidate.ttc < best!!.ttc) best = candidate
        }
        return best
    }

    /** Closing speed in m/s toward an object (positive = gap shrinking). */
    fun closingMs(o: IDetectable, playerSpeedKmh: Float): Float {
        val playerMs = playerSpeedKmh * GameGeometry.KMH_TO_MS
        val objMs = o.speedKmh * GameGeometry.KMH_TO_MS
        // Oncoming objects are flagged by negative model speed in the world; here
        // we treat same-direction as (player - obj) and rely on world closing.
        return playerMs - objMs
    }

    /** Detect a vehicle in the player's blind spot (spec §9 BSM). Returns side. */
    fun blindSpot(player: PlayerCar, objects: List<IDetectable>): Int {
        for (o in objects) {
            if (!o.active || o.kind != DetectKind.VEHICLE && o.kind != DetectKind.POLICE) continue
            if (o.worldZ in -9f..5f) {
                val dx = o.laneX - player.laneX
                if (dx in -1.4f..-0.55f) return -1   // left blind spot
                if (dx in 0.55f..1.4f) return 1      // right blind spot
            }
        }
        return 0
    }
}
