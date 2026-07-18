package com.adas.bountyrun.core

/**
 * Five-tier wanted system (spec §12). Escalates when the player commits or flees
 * incidents and de-escalates when the player cooperates (stops safely).
 */
class WantedLevelManager {

    /** 0 = clean, 1..5 = escalating pursuit intensity. */
    var level: Int = 0
        private set

    /** Cooldown accumulator; sustained safe driving lowers the wanted level. */
    private var calmSeconds: Float = 0f

    val isWanted: Boolean get() = level > 0

    /** Number of police units the current tier deploys (spec §12). */
    val patrolCount: Int get() = when (level) {
        0 -> 0
        1 -> 1
        2 -> 2
        3 -> 3
        4 -> 4
        else -> 6
    }

    val hasRoadblocks: Boolean get() = level >= 3
    val hasSpikeStrips: Boolean get() = level >= 4
    val hasHelicopter: Boolean get() = level >= 5

    /** Raise the wanted level (capped at 5) after an incident/escape. */
    fun escalate(by: Int = 1) {
        level = (level + by).coerceIn(0, 5)
        calmSeconds = 0f
    }

    /** Cooperating (safe stop) reduces heat immediately. */
    fun cooperate() {
        level = (level - 1).coerceAtLeast(0)
        calmSeconds = 0f
    }

    /** Tick the calm timer; de-escalate one tier per sustained calm window. */
    fun update(dt: Float, drivingSafely: Boolean) {
        if (level == 0) return
        if (drivingSafely) {
            calmSeconds += dt
            if (calmSeconds >= CALM_WINDOW_SECONDS) {
                level = (level - 1).coerceAtLeast(0)
                calmSeconds = 0f
            }
        } else {
            calmSeconds = 0f
        }
    }

    fun reset() {
        level = 0
        calmSeconds = 0f
    }

    companion object {
        const val CALM_WINDOW_SECONDS = 18f
    }
}
