package com.adas.bountyrun.core

import com.adas.bountyrun.config.LevelTable

/**
 * Tracks the current level, its target speed and completion progress (spec §7).
 * Target speed increases by exactly the configured step each level.
 */
class LevelManager(
    private val table: LevelTable,
    startLevel: Int = 1
) {
    var level: Int = startLevel.coerceAtLeast(1)
        private set

    /** Metres travelled within the current level. */
    var distanceTravelled: Float = 0f
        private set

    val targetSpeedKmh: Int get() = table.targetSpeedKmh(level)
    val difficulty: Float get() = table.difficulty(level)
    val trafficDensity: Int get() = table.trafficDensity(level)
    val hazardInterval: Float get() = table.hazardIntervalSeconds(level)
    val nightProbability: Float get() = table.nightProbability(level)
    val distanceGoal: Float get() = table.levelDistanceMeters
    val progress: Float get() = (distanceTravelled / distanceGoal).coerceIn(0f, 1f)
    val isLastLevel: Boolean get() = level >= table.totalLevels

    /** Advance the odometer by [meters]; returns true when the level goal is met. */
    fun addDistance(meters: Float): Boolean {
        distanceTravelled += meters
        return distanceTravelled >= distanceGoal
    }

    /** Move to the next level (+step km/h) and reset the odometer. */
    fun advance() {
        level += 1
        distanceTravelled = 0f
    }
}
