package com.adas.bountyrun.config

/**
 * The player's chosen configuration for a run (spec §2 core loop steps 1-6).
 * Passed from the menu into the game session. Mutable so the menu can build it
 * up incrementally; snapshotted by [com.adas.bountyrun.core.GameSession].
 */
data class GameSetup(
    var country: CountryProfile = CountryProfile.INDIA,
    var environment: EnvironmentType = EnvironmentType.HIGHWAY,
    var vehicle: VehicleSpec = VehicleSpec.SEDAN_SAFE,
    var adas: AdasConfig = AdasConfig(),
    var weather: Weather = Weather.CLEAR,
    var timeOfDay: TimeOfDay = TimeOfDay.AFTERNOON,
    var mode: GameMode = GameMode.ADAS_AWARENESS,
    var scoring: ScoringRules = ScoringRules.DEFAULT,
    var levelTable: LevelTable = LevelTable.DEFAULT,
    var startLevel: Int = 1,
    /** Bounty carried into the next level; null starts fresh from the rules. */
    var carryBounty: Int? = null
) {
    /** Driving side is derived from the country (spec §2 step 2 "automatically"). */
    val drivingSide: DrivingSide get() = country.drivingSide

    companion object {
        /** Singleton carrier between activities (kept simple; no DI framework). */
        @Volatile
        var current: GameSetup = GameSetup()
    }
}
