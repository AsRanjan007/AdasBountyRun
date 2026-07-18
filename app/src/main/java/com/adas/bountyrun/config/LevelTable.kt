package com.adas.bountyrun.config

/**
 * Level-progression configuration (spec §7).
 *
 * Level 1 begins at [baseSpeedKmh]; every subsequent level increases the target
 * speed by exactly [speedStepKmh]. Difficulty knobs (traffic density, hazard
 * frequency, night probability, police escalation) scale with the level index
 * and are consumed by the Hazard Director and spawners.
 */
data class LevelTable(
    val baseSpeedKmh: Int = 30,
    val speedStepKmh: Int = 10,
    val totalLevels: Int = 10,
    /** Distance (metres) the player must travel to complete a level. */
    val levelDistanceMeters: Float = 2_000f
) {
    /** Target/limit speed for a 1-based [level] (Level 1 -> 30, Level 2 -> 40 ...). */
    fun targetSpeedKmh(level: Int): Int =
        baseSpeedKmh + (level.coerceIn(1, Int.MAX_VALUE) - 1) * speedStepKmh

    /** 0f..1f difficulty ramp used to scale spawn rates and severity. */
    fun difficulty(level: Int): Float =
        ((level - 1).toFloat() / (totalLevels - 1).coerceAtLeast(1)).coerceIn(0f, 1f)

    /** Probability that a level runs at night, rising with progression. */
    fun nightProbability(level: Int): Float = (0.05f + 0.09f * (level - 1)).coerceIn(0f, 0.85f)

    /** Base seconds between hazard spawns; shrinks as levels increase. */
    fun hazardIntervalSeconds(level: Int): Float =
        (3.2f - 2.0f * difficulty(level)).coerceAtLeast(0.9f)

    /** Simultaneous background traffic target. */
    fun trafficDensity(level: Int): Int = 4 + (level - 1)

    companion object {
        val DEFAULT = LevelTable()
    }
}
