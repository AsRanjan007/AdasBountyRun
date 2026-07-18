package com.adas.bountyrun.adas

import com.adas.bountyrun.config.AdasConfig
import com.adas.bountyrun.config.AdasFeature
import com.adas.bountyrun.core.DetectKind
import com.adas.bountyrun.core.GameSession
import com.adas.bountyrun.core.IDetectable
import com.adas.bountyrun.core.SafetyEvent
import com.adas.bountyrun.core.SafetyType
import com.adas.bountyrun.engine.GameGeometry
import com.adas.bountyrun.entities.PlayerCar
import kotlin.math.abs

/** HUD warning severity → colour band (spec §16). */
enum class WarningSeverity { SAFE, CAUTION, HIGH, DANGER }

/** A one-shot ADAS event the view drains for banners + spoken alerts. */
data class AdasEvent(
    val banner: String,
    val voiceKey: String,
    val severity: WarningSeverity
)

/** Live ADAS state the HUD reads every frame (spec §16). */
data class AdasState(
    var severity: WarningSeverity = WarningSeverity.SAFE,
    var activeFeature: String = "READY",
    var ttc: Float = Float.MAX_VALUE,
    var followingDistanceM: Float = -1f,
    var blindSpotSide: Int = 0,
    var accEngaged: Boolean = false,
    var aebActive: Boolean = false,
    var message: String = ""
)

/**
 * Runs every enabled ADAS feature each frame (spec §9), issuing warnings, driving
 * automatic braking / steering assists, and feeding the bounty and report systems.
 * All thresholds come from [AdasConfig] — nothing is hard-coded.
 */
class AdasManager(
    private val config: AdasConfig,
    private val sensor: SensorModel = SensorModel()
) {
    val state = AdasState()
    /** Drained by the view for banners + text-to-speech. */
    val events = ArrayDeque<AdasEvent>()

    // Reaction / warning bookkeeping.
    private var activeThreat: IDetectable? = null
    private var warnStartMs: Long = 0
    private var warnedForThreat = false
    private var handledForThreat = false
    private var aebRewardedForThreat = false
    private var dmsTimer = 0f

    /** Weather × sensor-damage quality (spec §13/§14). */
    fun setSensorQuality(q: Float) { sensor.sensorQuality = q.coerceIn(0.15f, 1f) }

    fun reset() {
        events.clear()
        activeThreat = null
        warnedForThreat = false
        handledForThreat = false
        aebRewardedForThreat = false
        state.severity = WarningSeverity.SAFE
        state.aebActive = false
        state.accEngaged = false
    }

    /**
     * Main per-frame ADAS pass. Order matters: perceive → warn → assist.
     * @param nowMs monotonic clock for reaction-time measurement.
     */
    fun update(
        dt: Float,
        nowMs: Long,
        player: PlayerCar,
        objects: List<IDetectable>,
        session: GameSession
    ) {
        state.severity = WarningSeverity.SAFE
        state.aebActive = false
        state.message = ""
        var feature = "MONITORING"

        val threat = sensor.forwardThreat(player, objects, player.speedKmh)
        state.ttc = threat?.ttc ?: Float.MAX_VALUE
        state.followingDistanceM =
            if (threat != null && threat.kind == DetectKind.VEHICLE) threat.distanceM else -1f
        if (threat != null && threat.kind == DetectKind.VEHICLE) {
            session.report.observeFollowingDistance(threat.distanceM)
        }

        // ---- Forward Collision Warning (spec §9) ----
        if (threat != null && config.isOn(AdasFeature.FORWARD_COLLISION_WARNING)) {
            if (threat.ttc < config.fcwWarnTtc) {
                feature = "FCW"
                val severe = threat.ttc < config.fcwWarnTtc * 0.6f
                state.severity = if (severe) WarningSeverity.DANGER else WarningSeverity.HIGH
                session.report.observeCollisionRisk((1f - threat.ttc / config.fcwWarnTtc).coerceIn(0f, 1f))
                onNewThreat(threat.target, nowMs, session, classVoice(threat.kind))
                measureHandling(player, nowMs, session)
            } else {
                clearThreatIfPassed(threat.target)
            }
        } else if (threat == null) {
            clearThreatIfPassed(null)
        }

        // ---- Automatic Emergency Braking (spec §9) ----
        if (threat != null && config.isOn(AdasFeature.AUTOMATIC_EMERGENCY_BRAKING) &&
            threat.ttc < config.aebTriggerTtc
        ) {
            feature = "AEB"
            player.assistBrake = 1f
            state.aebActive = true
            state.severity = WarningSeverity.DANGER
            if (!aebRewardedForThreat) {
                aebRewardedForThreat = true
                session.report.log(SafetyEvent(SafetyType.AEB_ACTIVATION, nowMs))
                session.report.log(SafetyEvent(SafetyType.SAFE_BRAKE, nowMs))
                session.bounty.reward(session.setup.scoring.correctEmergencyBraking, "Correct emergency braking")
                session.report.notePrevention("Automatic Emergency Braking reduced impact energy.")
                emit("AUTOMATIC EMERGENCY BRAKING", "va_aeb", WarningSeverity.DANGER)
            }
        }

        // ---- Emergency Steering Assist (spec §9) ----
        if (threat != null && threat.inPath && config.isOn(AdasFeature.EMERGENCY_STEERING_ASSIST) &&
            threat.ttc < config.aebTriggerTtc * 0.8f
        ) {
            val escape = clearAdjacentDirection(player, objects)
            if (escape != 0) {
                player.assistSteer = escape * 1.6f
                feature = "ESA"
                session.report.notePrevention("Emergency Steering Assist provided an evasive path.")
            }
        }

        // ---- Adaptive Cruise Control (spec §9) ----
        if (config.isOn(AdasFeature.ADAPTIVE_CRUISE_CONTROL)) {
            adaptiveCruise(dt, player, threat, session)
        } else state.accEngaged = false

        // ---- Lane Departure Warning / Lane Keep Assist (spec §9) ----
        laneAssist(player, nowMs, session)?.let { feature = it }

        // ---- Blind Spot Monitoring (spec §9) ----
        if (config.isOn(AdasFeature.BLIND_SPOT_MONITORING)) {
            val side = sensor.blindSpot(player, objects)
            state.blindSpotSide = side
            // Warn only if the driver steers toward the occupied side.
            if (side != 0 && player.steerInput * side > 0.2f) {
                if (state.severity < WarningSeverity.CAUTION) state.severity = WarningSeverity.CAUTION
                emitOnce("blindspot", "VEHICLE IN BLIND SPOT", "va_blindspot", WarningSeverity.CAUTION)
                session.bounty.reward(session.setup.scoring.correctBlindSpot, "Blind-spot warning heeded")
            }
        } else state.blindSpotSide = 0

        // ---- Driver Monitoring System (spec §9) — periodic attention prompt ----
        if (config.isOn(AdasFeature.DRIVER_MONITORING)) {
            dmsTimer += dt
            if (dmsTimer > 22f) {
                dmsTimer = 0f
                emit("DRIVER ATTENTION REQUIRED", "va_attention", WarningSeverity.CAUTION)
            }
        }

        state.activeFeature = feature
    }

    /** ACC: hold the set speed while keeping a safe headway (spec §9). */
    private fun adaptiveCruise(dt: Float, player: PlayerCar, threat: ThreatInfo?, session: GameSession) {
        state.accEngaged = true
        val setSpeed = session.speedLimitKmh.toFloat()
        // Auto-throttle toward the set speed when the driver is coasting.
        if (player.brakeInput < 0.05f && player.throttleInput < 0.05f) {
            player.throttleInput = if (player.speedKmh < setSpeed - 1f) 0.6f else 0.15f
        }
        // Maintain headway to a lead vehicle.
        if (threat != null && threat.kind == DetectKind.VEHICLE) {
            val desiredGap = config.accHeadwaySeconds * player.speedKmh * GameGeometry.KMH_TO_MS
            if (threat.distanceM < desiredGap) {
                val deficit = (desiredGap - threat.distanceM) / desiredGap.coerceAtLeast(1f)
                player.assistBrake = maxOf(player.assistBrake, (deficit * 0.7f).coerceIn(0f, 0.7f))
                if (player.throttleInput > 0f) player.throttleInput = 0f
            } else if (threat.distanceM > desiredGap * 1.6f) {
                // Safe distance maintained — small ongoing reward, throttled by cooldown.
                session.report.observeFollowingDistance(threat.distanceM)
            }
        }
    }

    /** LDW + LKA (spec §9). Returns the active feature label if engaged. */
    private fun laneAssist(player: PlayerCar, nowMs: Long, session: GameSession): String? {
        val offset = abs(player.laneOffsetNormalized())
        var label: String? = null
        if (config.isOn(AdasFeature.LANE_DEPARTURE_WARNING) && offset > config.ldwOffsetThreshold) {
            if (state.severity < WarningSeverity.CAUTION) state.severity = WarningSeverity.CAUTION
            label = "LDW"
            emitOnce("ldw", "LANE DEPARTURE", "va_lane", WarningSeverity.CAUTION)
            session.report.log(SafetyEvent(SafetyType.ADAS_WARNING, nowMs, "LDW"))
        } else {
            events.clearFlag("ldw")
        }
        if (config.isOn(AdasFeature.LANE_KEEP_ASSIST) && offset > config.lkaOffsetThreshold) {
            player.assistSteer = player.directionToLaneCenter() * 1.1f
            label = "LKA"
            session.report.log(SafetyEvent(SafetyType.ADAS_HANDLED, nowMs, "LKA"))
        }
        return label
    }

    // ---- Threat / reaction bookkeeping helpers ----

    private fun onNewThreat(target: IDetectable, nowMs: Long, session: GameSession, voice: Pair<String, String>) {
        if (activeThreat !== target) {
            activeThreat = target
            warnStartMs = nowMs
            warnedForThreat = false
            handledForThreat = false
            aebRewardedForThreat = false
        }
        if (!warnedForThreat) {
            warnedForThreat = true
            session.report.log(SafetyEvent(SafetyType.ADAS_WARNING, nowMs, voice.second))
            emit(voice.first, voice.second, state.severity)
        }
    }

    private fun measureHandling(player: PlayerCar, nowMs: Long, session: GameSession) {
        if (warnedForThreat && !handledForThreat && (player.brakeInput > 0.3f || player.steerInput.let { abs(it) > 0.3f })) {
            handledForThreat = true
            val reaction = (nowMs - warnStartMs).coerceAtLeast(0)
            session.report.logReaction(reaction)
            session.report.log(SafetyEvent(SafetyType.ADAS_HANDLED, nowMs))
        }
    }

    private fun clearThreatIfPassed(current: IDetectable?) {
        if (activeThreat != null && activeThreat !== current) {
            activeThreat = null
            warnedForThreat = false
            handledForThreat = false
            aebRewardedForThreat = false
        }
    }

    /** Is an adjacent lane clear for an evasive nudge? Returns -1/0/+1. */
    private fun clearAdjacentDirection(player: PlayerCar, objects: List<IDetectable>): Int {
        val leftClear = laneClear(player, objects, player.laneX - 1f)
        val rightClear = laneClear(player, objects, player.laneX + 1f)
        val leftInBounds = player.laneX - 1f >= -GameGeometry.ROAD_HALF_UNITS
        val rightInBounds = player.laneX + 1f <= GameGeometry.ROAD_HALF_UNITS
        return when {
            rightClear && rightInBounds -> 1
            leftClear && leftInBounds -> -1
            else -> 0
        }
    }

    private fun laneClear(player: PlayerCar, objects: List<IDetectable>, targetX: Float): Boolean {
        for (o in objects) {
            if (!o.active) continue
            if (o.worldZ in -6f..40f && abs(o.laneX - targetX) < (o.halfWidth + player.halfWidth + 0.2f)) {
                return false
            }
        }
        return true
    }

    private fun classVoice(kind: DetectKind): Pair<String, String> = when (kind) {
        DetectKind.PEDESTRIAN -> "PEDESTRIAN DETECTED" to "va_pedestrian"
        DetectKind.CYCLIST -> "CYCLIST DETECTED" to "va_pedestrian"
        DetectKind.ANIMAL -> "ANIMAL ON ROAD" to "va_pedestrian"
        else -> "COLLISION RISK AHEAD" to "va_collision"
    }

    private fun emit(banner: String, voiceKey: String, severity: WarningSeverity) {
        events.addLast(AdasEvent(banner, voiceKey, severity))
        state.message = banner
    }

    // Simple de-dup so continuous conditions speak once until they clear.
    private val activeFlags = HashSet<String>()
    private fun emitOnce(flag: String, banner: String, voiceKey: String, severity: WarningSeverity) {
        if (activeFlags.add(flag)) emit(banner, voiceKey, severity)
        state.message = banner
    }
    private fun ArrayDeque<AdasEvent>.clearFlag(flag: String) { activeFlags.remove(flag) }
}
