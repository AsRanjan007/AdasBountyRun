package com.adas.bountyrun.engine

import com.adas.bountyrun.core.GameSession
import com.adas.bountyrun.entities.PlayerCar
import com.adas.bountyrun.entities.PoliceCar
import kotlin.random.Random

/**
 * Deploys and drives pursuit units to match the wanted level (spec §12/§19) and
 * applies the continuous "escaping police" bounty drain (spec §8). Escaping is a
 * high-risk negative decision, never rewarded (spec §3D/§26).
 */
class PoliceManager(
    private val session: GameSession,
    private val rnd: Random = Random(System.nanoTime())
) {
    private val maxUnits = 6
    val units = Array(maxUnits) { PoliceCar() }

    private var escapeDrainAccumulator = 0f

    val activeUnits: List<PoliceCar> get() = units.filter { it.active }
    val anyActive: Boolean get() = units.any { it.active }

    /** Deploy up to the wanted tier's patrol count from behind the player. */
    private fun syncDeployment() {
        val target = session.wanted.patrolCount
        var active = units.count { it.active }
        var i = 0
        while (active < target && i < units.size) {
            val u = units[i]
            if (!u.active) {
                u.deploy(startZ = -45f - rnd.nextFloat() * 40f, laneX = (rnd.nextInt(3) - 1).toFloat())
                active++
            }
            i++
        }
        // Stand units down if the wanted level dropped.
        if (target < active) {
            for (u in units) {
                if (u.active && u.worldZ < -70f) u.active = false
            }
        }
    }

    /**
     * Update pursuit. Returns true if the player has been caught (arrest).
     * [drivingSafely] lets the wanted level decay when the player cooperates.
     */
    fun update(dt: Float, player: PlayerCar, drivingSafely: Boolean): Boolean {
        session.wanted.update(dt, drivingSafely)
        if (!session.wanted.isWanted) {
            for (u in units) u.active = false
            return false
        }
        syncDeployment()

        var caught = false
        for (u in units) {
            if (!u.active) continue
            if (u.update(dt, player.laneX, player.speedKmh, session.wanted.level)) caught = true
            if (u.worldZ < GameGeometry.DESPAWN_Z - 20f) u.active = false
        }

        // Continuous escape penalty while actively fleeing at speed (spec §8).
        if (!drivingSafely && player.speedKmh > 25f) {
            escapeDrainAccumulator += dt
            if (escapeDrainAccumulator >= 1f) {
                escapeDrainAccumulator = 0f
                session.bounty.penalty(session.setup.scoring.escapingPolicePerSecond, "Escaping police")
            }
        }
        return caught
    }

    /** Called on any pursuit-worthy incident (spec §11 step 7). */
    fun onIncident(escalateBy: Int = 1) {
        session.wanted.escalate(escalateBy)
    }

    /** Called when the player stops safely to cooperate (spec §3D). */
    fun cooperate() {
        session.wanted.cooperate()
        session.bounty.reward(session.setup.scoring.safeStopAfterIncident, "Safe stop after incident")
    }

    fun reset() {
        for (u in units) u.active = false
        escapeDrainAccumulator = 0f
    }
}
