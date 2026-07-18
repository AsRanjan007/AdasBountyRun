package com.adas.bountyrun.core

import com.adas.bountyrun.config.HazardKind

/**
 * Core gameplay interfaces (spec §20). Kept small and behaviour-focused so that
 * entities, ADAS features and managers stay decoupled.
 */

/** Anything the sensor suite can perceive ahead of / around the car. */
interface IDetectable {
    /** World position along the road (metres ahead of the player = +Z). */
    val worldZ: Float
    /** Lateral position in lane units (0 = centre lane centre). */
    val laneX: Float
    /** Longitudinal speed in km/h (world frame). */
    val speedKmh: Float
    /** Half-width used for overlap / collision maths (lane units). */
    val halfWidth: Float
    /** Half-length in metres. */
    val halfLength: Float
    /** True while the object is alive/active in the world. */
    val active: Boolean
    /** Classification used by ADAS + scoring. */
    val kind: DetectKind
}

/** Broad detection classes used by ADAS and the sensor overlay. */
enum class DetectKind { VEHICLE, PEDESTRIAN, CYCLIST, ANIMAL, HAZARD, POLICE, EMERGENCY }

/** Something that carries collision risk and can be scored on impact. */
interface ICollisionRisk {
    val kind: DetectKind
    val hazardKind: HazardKind?
}

/** Something that can take damage (player vehicle, sensors, etc.). */
interface IDamageable {
    fun applyDamage(amount: Float, part: DamagePart)
    val isDisabled: Boolean
}

/** Damageable subsystems (spec §13). */
enum class DamagePart {
    ENGINE, TYRES, BRAKES, STEERING, WINDSHIELD, HEADLIGHTS,
    ADAS_SENSORS, CAMERA, RADAR, BODY
}

/** A discrete scoring event routed to the BountyManager (spec §20 IBountyEvent). */
data class BountyEvent(
    val delta: Int,
    val reason: String,
    val critical: Boolean = false
)

/** A safety event logged for the end-of-level report (spec §20 ISafetyEvent). */
data class SafetyEvent(
    val type: SafetyType,
    val timeMs: Long,
    val detail: String = ""
)

enum class SafetyType {
    NEAR_MISS, COLLISION, PEDESTRIAN_SAVED, CYCLIST_SAVED, ANIMAL_SAVED,
    SAFE_BRAKE, LANE_VIOLATION, SPEEDING, RED_LIGHT, ADAS_WARNING,
    ADAS_HANDLED, AEB_ACTIVATION, PROPERTY_DAMAGE
}
