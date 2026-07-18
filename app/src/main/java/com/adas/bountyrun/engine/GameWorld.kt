package com.adas.bountyrun.engine

import com.adas.bountyrun.adas.AdasEvent
import com.adas.bountyrun.adas.AdasManager
import com.adas.bountyrun.adas.WarningSeverity
import com.adas.bountyrun.config.GameSetup
import com.adas.bountyrun.core.DetectKind
import com.adas.bountyrun.core.GameSession
import com.adas.bountyrun.core.SafetyEvent
import com.adas.bountyrun.core.SafetyType
import com.adas.bountyrun.entities.PlayerCar
import com.adas.bountyrun.entities.RoadEntity
import kotlin.math.abs

/**
 * The simulation core (spec §30 architecture). Owns all live objects and runs
 * the per-frame update pipeline:
 *   input → ADAS → physics → world/AI → collision/risk → bounty/police → session.
 * Rendering reads this state but never mutates it.
 */
class GameWorld(val setup: GameSetup) {

    val session = GameSession(setup)
    val player = PlayerCar(setup.vehicle, setup.weather.gripFactor)
    val adas = AdasManager(setup.adas)
    private val collision = CollisionManager(session)
    val police = PoliceManager(session)
    private val spawner = Spawner(setup.country, session.level)

    /** Pool sized for the densest level plus hazards (spec §28). */
    val pool = ObjectPool(28, { RoadEntity() }, { it.active })

    /** Ambient light 0..1 for the renderer (night handling, spec §14). */
    val ambientLight: Float get() = setup.timeOfDay.ambientLight
    val visibility: Float get() = setup.weather.visibility

    /** Latest educational incident banner for the awareness overlay (spec §11). */
    var incidentMessage: String = ""
        private set
    var incidentPrevention: String = ""
        private set
    private var incidentDisplayTimer = 0f

    // Scoring cooldowns so continuous states don't spam points.
    private var speedingCooldown = 0f
    private var laneCooldown = 0f
    private var cooperateLatched = false

    private var lastReportSnapshot = false

    fun startLevel() {
        player.reset()
        adas.reset()
        police.reset()
        session.report.clear()
        for (e in pool.all) e.active = false
        incidentMessage = ""
        incidentPrevention = ""
    }

    /** True while the driver is behaving safely (used for wanted decay). */
    private fun drivingSafely(): Boolean =
        player.speedKmh < 8f || (player.speedKmh <= session.speedLimitKmh + 5 && !session.wanted.isWanted)

    /**
     * Advance the whole simulation by [dt] seconds. [nowMs] is a monotonic clock
     * used for ADAS reaction-time measurement.
     */
    fun update(dt: Float, nowMs: Long) {
        if (!session.isRunning) return

        // Sensor quality reflects weather and any sensor damage (spec §13/§14).
        adas.setSensorQuality(player.sensorHealthFraction() * setup.weather.sensorFactor)

        // 1) ADAS runs before physics so its brake/steer assists take effect.
        adas.update(dt, nowMs, player, detectables(), session)

        // 2) Speeding enforcement (spec §8).
        enforceSpeeding(dt, nowMs)

        // 3) Physics integration.
        player.update(dt, topSpeedCapKmh = 1000f)

        // 4) Lane / off-road enforcement (spec §8 lane violation).
        enforceLaneDiscipline(dt, nowMs)

        // 5) Level progress (spec §7).
        val meters = player.speedKmh * GameGeometry.KMH_TO_MS * dt
        if (session.level.addDistance(meters)) {
            completeLevel()
            return
        }

        // 6) Spawning / Hazard Director (spec §19).
        spawner.maintainTraffic(pool)
        spawner.tickHazards(dt, pool)

        // 7) World + AI update, collisions and avoidance scoring.
        updateEntities(dt, nowMs)

        // 8) Police pursuit (spec §12).
        val caught = police.update(dt, player, drivingSafely())
        handleCooperation()

        // 9) Terminal conditions (spec §1).
        session.evaluate(vehicleDisabled = player.isDisabled, policeCaught = caught)

        if (incidentDisplayTimer > 0f) incidentDisplayTimer -= dt else { incidentMessage = "" }
    }

    private fun updateEntities(dt: Float, nowMs: Long) {
        val corridorPad = 0.2f
        for (e in pool.all) {
            if (!e.active) continue
            e.update(dt, player.speedKmh)

            // Track threat exposure for fair avoidance scoring.
            if (e.worldZ in 0f..30f) {
                val gap = abs(e.laneX - player.laneX)
                if (gap < e.minLateralGap) e.minLateralGap = gap
                if (gap <= player.halfWidth + e.halfWidth + corridorPad) e.enteredCorridor = true
            }

            // Collision.
            if (!e.scored && e.overlapsPlayer(player.laneX, player.halfWidth, player.halfLength)) {
                e.scored = true
                val result = collision.resolve(player, e, nowMs)
                showIncident(result.message, result.prevention)
                if (result.escalatePolice) {
                    police.onIncident(if (result.critical) 2 else 1)
                    if (session.wanted.level == 1) {
                        adas.events.addLast(AdasEvent("POLICE PURSUIT INITIATED", "va_police", WarningSeverity.DANGER))
                    }
                }
                e.active = false
                continue
            }

            // Passed the player safely -> avoidance reward for living road users.
            if (e.worldZ < GameGeometry.DESPAWN_Z) {
                awardAvoidance(e, nowMs)
                e.active = false
            }
        }
    }

    /** Reward safe avoidance / count near-miss (spec §8/§17). */
    private fun awardAvoidance(e: RoadEntity, nowMs: Long) {
        if (e.rewardedAvoidance || !e.enteredCorridor) return
        e.rewardedAvoidance = true
        val rules = setup.scoring
        when (e.kind) {
            DetectKind.PEDESTRIAN -> {
                session.bounty.reward(rules.safePedestrianAvoidance, "Pedestrian protected")
                session.report.log(SafetyEvent(SafetyType.PEDESTRIAN_SAVED, nowMs))
            }
            DetectKind.CYCLIST -> {
                session.bounty.reward(rules.safeCyclistAvoidance, "Cyclist protected")
                session.report.log(SafetyEvent(SafetyType.CYCLIST_SAVED, nowMs))
            }
            DetectKind.ANIMAL -> {
                session.bounty.reward(rules.animalAvoidance, "Animal protected")
                session.report.log(SafetyEvent(SafetyType.ANIMAL_SAVED, nowMs))
            }
            else -> {}
        }
        if (e.minLateralGap < player.halfWidth + e.halfWidth + 0.25f) {
            session.report.log(SafetyEvent(SafetyType.NEAR_MISS, nowMs))
        }
    }

    private fun enforceSpeeding(dt: Float, nowMs: Long) {
        speedingCooldown -= dt
        val limit = session.speedLimitKmh
        if (player.speedKmh > limit + 12 && speedingCooldown <= 0f) {
            speedingCooldown = 2.5f
            session.bounty.penalty(setup.scoring.dangerousSpeeding, "Dangerous speeding")
            session.report.log(SafetyEvent(SafetyType.SPEEDING, nowMs))
        }
    }

    private fun enforceLaneDiscipline(dt: Float, nowMs: Long) {
        laneCooldown -= dt
        if (abs(player.laneX) > GameGeometry.ROAD_HALF_UNITS && laneCooldown <= 0f) {
            laneCooldown = 2f
            session.bounty.penalty(setup.scoring.wrongSideDriving / 4, "Off the carriageway")
            session.report.log(SafetyEvent(SafetyType.LANE_VIOLATION, nowMs))
        }
    }

    /** Reward a single safe stop while wanted, then let heat decay (spec §3D). */
    private fun handleCooperation() {
        if (session.wanted.isWanted && player.speedKmh < 5f && !cooperateLatched) {
            cooperateLatched = true
            police.cooperate()
            showIncident("You stopped to cooperate. Wanted level reduced.",
                "Cooperating is safer than fleeing and preserves bounty.")
        } else if (player.speedKmh > 20f) {
            cooperateLatched = false
        }
    }

    private fun completeLevel() {
        val report = session.completeLevel()
        lastReport = report
    }

    private fun showIncident(message: String, prevention: String) {
        incidentMessage = message
        incidentPrevention = prevention
        incidentDisplayTimer = 4.5f
    }

    /** Set when a level is completed; consumed by the activity to show the report. */
    var lastReport: com.adas.bountyrun.core.LevelReport? = null
        private set

    /** Snapshot of live detectables for ADAS + the sensor overlay. */
    fun detectables(): List<com.adas.bountyrun.core.IDetectable> {
        val list = ArrayList<com.adas.bountyrun.core.IDetectable>(32)
        for (e in pool.all) if (e.active) list.add(e)
        for (u in police.units) if (u.active) list.add(u)
        return list
    }
}
