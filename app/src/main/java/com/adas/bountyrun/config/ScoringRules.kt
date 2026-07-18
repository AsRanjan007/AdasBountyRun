package com.adas.bountyrun.config

/**
 * Central, data-driven scoring table (spec §8).
 *
 * Every reward and penalty in the game is read from here so balancing can be
 * changed in one place without touching gameplay code. Values match the master
 * specification but are all overridable.
 */
data class ScoringRules(
    val startingBounty: Int = 10_000,

    // Positive rewards
    val safePedestrianAvoidance: Int = 300,
    val safeCyclistAvoidance: Int = 250,
    val animalAvoidance: Int = 200,
    val correctEmergencyBraking: Int = 350,
    val correctLaneChange: Int = 150,
    val maintainingSafeDistance: Int = 100,
    val followingTrafficSignals: Int = 100,
    val allowingAmbulancePass: Int = 300,
    val routeWithoutCollision: Int = 1_000,
    val correctAcc: Int = 200,
    val correctBlindSpot: Int = 200,
    val safeStopAfterIncident: Int = 250,
    val perfectLevelCompletion: Int = 1_500,

    // Penalties (stored positive; applied as deductions)
    val minorVehicleCollision: Int = 500,
    val majorVehicleCollision: Int = 1_500,
    val hittingAnimal: Int = 2_000,
    val hittingCyclist: Int = 3_000,
    val hittingPedestrian: Int = 5_000,
    val runningRedLight: Int = 600,
    val dangerousSpeeding: Int = 400,
    val wrongSideDriving: Int = 800,
    val ignoringAdasWarning: Int = 300,
    val escapingPolicePerSecond: Int = 120,
    val damagingPublicProperty: Int = 400,
    val hittingEmergencyVehicle: Int = 2_500,

    /** A "critical casualty" zeroes the bounty (spec §8 & §1 game-over). */
    val criticalCasualtyZeroesBounty: Boolean = true
) {
    companion object {
        /** The default, spec-accurate rule set. */
        val DEFAULT = ScoringRules()
    }
}
