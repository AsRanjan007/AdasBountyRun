package com.adas.bountyrun.engine

import com.adas.bountyrun.config.HazardKind
import com.adas.bountyrun.core.DamagePart
import com.adas.bountyrun.core.DetectKind
import com.adas.bountyrun.core.GameSession
import com.adas.bountyrun.core.SafetyEvent
import com.adas.bountyrun.core.SafetyType
import com.adas.bountyrun.entities.PlayerCar
import com.adas.bountyrun.entities.RoadEntity

/** Outcome of an impact so the world can react (VFX, police, game-over). */
data class ImpactResult(
    val severity: Float,          // 0..1 normalised impact energy
    val critical: Boolean,        // triggers game-over casualty
    val escalatePolice: Boolean,
    val message: String,          // educational ADAS message (spec §11)
    val prevention: String        // which ADAS feature could have prevented it
)

/**
 * Applies vehicle physics/damage and bounty consequences of a collision
 * (spec §8, §11, §13). Non-graphic, responsible treatment (spec §26).
 */
class CollisionManager(private val session: GameSession) {

    /** Speed (km/h) above which a vulnerable-road-user impact is a critical casualty. */
    private val criticalCasualtySpeed = 30f

    fun resolve(player: PlayerCar, other: RoadEntity, nowMs: Long): ImpactResult {
        val rules = session.setup.scoring
        val impactKmh = player.speedKmh
        val severity = (impactKmh / 120f).coerceIn(0.1f, 1f)
        session.report.log(SafetyEvent(SafetyType.COLLISION, nowMs, other.label))

        var critical = false
        var escalate = false
        var message: String
        var prevention: String

        when (other.kind) {
            DetectKind.PEDESTRIAN -> {
                escalate = true
                if (impactKmh >= criticalCasualtySpeed) {
                    critical = true
                    message = "Critical pedestrian casualty. Speed left no time to stop."
                } else {
                    session.bounty.penalty(rules.hittingPedestrian, "Hit a pedestrian")
                    message = "Pedestrian struck. Always yield to people on the road."
                }
                prevention = "Pedestrian Detection + Automatic Emergency Braking could have prevented this."
                player.applyDamage(18f * severity, DamagePart.BODY)
            }
            DetectKind.CYCLIST -> {
                escalate = true
                session.bounty.penalty(rules.hittingCyclist, "Hit a cyclist")
                message = "Cyclist struck. Give vulnerable riders extra space."
                prevention = "Cyclist Detection + Blind Spot Monitoring could have prevented this."
                player.applyDamage(16f * severity, DamagePart.BODY)
                if (impactKmh >= criticalCasualtySpeed + 8f) critical = true
            }
            DetectKind.ANIMAL -> {
                session.bounty.penalty(rules.hittingAnimal, "Hit an animal")
                message = "Animal struck. Slow down where animals may cross."
                prevention = "Animal Detection + Forward Collision Warning could have prevented this."
                player.applyDamage(20f * severity, DamagePart.BODY)
            }
            DetectKind.EMERGENCY -> {
                escalate = true
                session.bounty.penalty(rules.hittingEmergencyVehicle, "Hit an emergency vehicle")
                message = "Emergency vehicle struck. Always make way for them."
                prevention = "Adaptive Cruise Control + FCW could have prevented this."
                player.applyDamage(30f * severity, DamagePart.BODY)
            }
            DetectKind.POLICE -> {
                escalate = true
                session.bounty.penalty(rules.majorVehicleCollision, "Collided with police")
                message = "You hit a police vehicle. Pursuit intensifies."
                prevention = "Maintaining safe distance avoids pursuit collisions."
                player.applyDamage(30f * severity, DamagePart.BODY)
            }
            DetectKind.VEHICLE -> {
                val major = impactKmh >= 55f
                escalate = major
                session.bounty.penalty(
                    if (major) rules.majorVehicleCollision else rules.minorVehicleCollision,
                    if (major) "Major vehicle collision" else "Minor vehicle collision"
                )
                message = if (major) "Major collision. Keep a safe following distance."
                          else "Minor collision. Ease off and keep your distance."
                prevention = "Forward Collision Warning + AEB reduce rear-end crashes."
                player.applyDamage((if (major) 40f else 18f) * severity, DamagePart.BODY)
                player.applyDamage(10f * severity, DamagePart.ENGINE)
            }
            DetectKind.HAZARD -> {
                val h = other.hazardKind
                if (h != null && h.lethal) {
                    escalate = true
                    session.bounty.penalty(rules.hittingPedestrian / 2, "Hit ${h.displayName}")
                    message = "${h.displayName} struck near the roadside."
                    prevention = "Pedestrian Detection could have prevented this."
                    player.applyDamage(15f * severity, DamagePart.BODY)
                } else {
                    session.bounty.penalty(rules.damagingPublicProperty, "Hit a road hazard")
                    message = "Road hazard hit: ${h?.displayName ?: "obstacle"}."
                    prevention = "Traffic Sign Recognition + FCW warn of hazards ahead."
                    applyHazardDamage(player, h, severity)
                }
            }
        }

        if (critical) session.criticalCasualty(message)
        session.report.notePrevention(prevention)
        return ImpactResult(severity, critical, escalate, message, prevention)
    }

    private fun applyHazardDamage(player: PlayerCar, h: HazardKind?, severity: Float) {
        when (h) {
            HazardKind.POTHOLE, HazardKind.OPEN_MANHOLE -> player.applyDamage(14f, DamagePart.TYRES)
            HazardKind.SPEED_BREAKER -> player.applyDamage(6f * severity, DamagePart.TYRES)
            HazardKind.OIL_SPILL, HazardKind.WATERLOGGING -> player.applyDamage(4f, DamagePart.BRAKES)
            HazardKind.ROCK, HazardKind.FALLEN_CARGO, HazardKind.TREE_BRANCH, HazardKind.LANDSLIDE ->
                player.applyDamage(22f * severity, DamagePart.BODY)
            HazardKind.CONSTRUCTION_CONE, HazardKind.ROAD_BARRIER -> player.applyDamage(6f * severity, DamagePart.BODY)
            HazardKind.BROKEN_DOWN_VEHICLE, HazardKind.PARKED_VEHICLE ->
                player.applyDamage(24f * severity, DamagePart.BODY)
            HazardKind.DUST_CLOUD -> { /* visibility only */ }
            else -> player.applyDamage(10f * severity, DamagePart.BODY)
        }
    }
}
