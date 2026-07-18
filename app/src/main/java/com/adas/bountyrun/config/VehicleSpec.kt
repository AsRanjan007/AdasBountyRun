package com.adas.bountyrun.config

/**
 * Player vehicle configuration (spec §20 ScriptableObject equivalent).
 * All dynamics are expressed in SI-ish units so the physics controller stays
 * data-driven.
 */
data class VehicleSpec(
    val id: String,
    val displayName: String,
    /** Top speed in km/h. */
    val topSpeedKmh: Float,
    /** Acceleration in km/h per second at full throttle. */
    val accelerationKmhPerSec: Float,
    /** Service braking deceleration in km/h per second. */
    val brakeKmhPerSec: Float,
    /** Automatic Emergency Braking deceleration (stronger than service brake). */
    val aebKmhPerSec: Float,
    /** Lateral responsiveness (lane units per second at full steer). */
    val steerRate: Float,
    /** Structural integrity; higher survives more impact energy. */
    val maxHealth: Float = 100f,
    /** Passive rolling drag in km/h per second when coasting. */
    val coastDragKmhPerSec: Float = 6f
) {
    companion object {
        /** MVP player car (spec §22: one realistic player car). */
        val SEDAN_SAFE = VehicleSpec(
            id = "sedan_safe",
            displayName = "Safeline Sedan",
            topSpeedKmh = 180f,
            accelerationKmhPerSec = 22f,
            brakeKmhPerSec = 40f,
            aebKmhPerSec = 75f,
            steerRate = 2.6f
        )
        val SUV_GUARDIAN = VehicleSpec(
            id = "suv_guardian",
            displayName = "Guardian SUV",
            topSpeedKmh = 165f,
            accelerationKmhPerSec = 18f,
            brakeKmhPerSec = 36f,
            aebKmhPerSec = 68f,
            steerRate = 2.2f,
            maxHealth = 130f
        )
        val HATCH_CITY = VehicleSpec(
            id = "hatch_city",
            displayName = "City Hatch",
            topSpeedKmh = 150f,
            accelerationKmhPerSec = 24f,
            brakeKmhPerSec = 42f,
            aebKmhPerSec = 78f,
            steerRate = 3.0f,
            maxHealth = 85f
        )

        val ALL = listOf(SEDAN_SAFE, SUV_GUARDIAN, HATCH_CITY)
        fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: SEDAN_SAFE
    }
}
