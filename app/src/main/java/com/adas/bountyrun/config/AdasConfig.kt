package com.adas.bountyrun.config

/** The individual ADAS features the vehicle can be equipped with (spec §3E, §9). */
enum class AdasFeature(val displayName: String, val shortLabel: String) {
    FORWARD_COLLISION_WARNING("Forward Collision Warning", "FCW"),
    AUTOMATIC_EMERGENCY_BRAKING("Automatic Emergency Braking", "AEB"),
    ADAPTIVE_CRUISE_CONTROL("Adaptive Cruise Control", "ACC"),
    LANE_DEPARTURE_WARNING("Lane Departure Warning", "LDW"),
    LANE_KEEP_ASSIST("Lane Keep Assist", "LKA"),
    BLIND_SPOT_MONITORING("Blind Spot Monitoring", "BSM"),
    TRAFFIC_SIGN_RECOGNITION("Traffic Sign Recognition", "TSR"),
    DRIVER_MONITORING("Driver Monitoring System", "DMS"),
    EMERGENCY_STEERING_ASSIST("Emergency Steering Assist", "ESA"),
    REAR_CROSS_TRAFFIC_ALERT("Rear Cross Traffic Alert", "RCTA"),
    PARKING_ASSIST("Parking Assistance", "PARK"),
    HIGH_BEAM_ASSIST("High Beam Assist", "HBA"),
    HILL_DESCENT_CONTROL("Hill Descent Control", "HDC"),
    ELECTRONIC_STABILITY_CONTROL("Electronic Stability Control", "ESC")
}

/**
 * A player's ADAS configuration (spec §2 step 5). Features can be toggled on/off
 * from the ADAS setup screen. Thresholds are shared and data-driven so the same
 * numbers feed gameplay, the HUD and the end-of-level report.
 */
data class AdasConfig(
    val enabled: MutableSet<AdasFeature> = defaultEnabled(),
    /** Time-to-collision (s) at which FCW first warns. */
    val fcwWarnTtc: Float = 2.6f,
    /** TTC (s) at which AEB engages if the driver has not reacted. */
    val aebTriggerTtc: Float = 1.2f,
    /** Following distance (s) ACC tries to maintain. */
    val accHeadwaySeconds: Float = 2.0f,
    /** Lane offset (0..1 of half-lane) at which LDW warns. */
    val ldwOffsetThreshold: Float = 0.72f,
    /** Lane offset at which LKA nudges back. */
    val lkaOffsetThreshold: Float = 0.85f
) {
    fun isOn(feature: AdasFeature) = enabled.contains(feature)

    fun toggle(feature: AdasFeature) {
        if (!enabled.add(feature)) enabled.remove(feature)
    }

    companion object {
        /** MVP loadout (spec §22): FCW, AEB, LDW, LKA, ACC on by default. */
        fun defaultEnabled(): MutableSet<AdasFeature> = mutableSetOf(
            AdasFeature.FORWARD_COLLISION_WARNING,
            AdasFeature.AUTOMATIC_EMERGENCY_BRAKING,
            AdasFeature.LANE_DEPARTURE_WARNING,
            AdasFeature.LANE_KEEP_ASSIST,
            AdasFeature.ADAPTIVE_CRUISE_CONTROL,
            AdasFeature.BLIND_SPOT_MONITORING,
            AdasFeature.TRAFFIC_SIGN_RECOGNITION
        )
    }
}
