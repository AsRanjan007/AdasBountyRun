package com.adas.bountyrun.core

import com.adas.bountyrun.config.ScoringRules

/**
 * Owns the running bounty score (spec §8). All positive/negative events flow
 * through [apply] so the value stays authoritative and observable. Reads its
 * numbers from the injected [ScoringRules] — nothing is hard-coded here.
 */
class BountyManager(
    private val rules: ScoringRules,
    startingOverride: Int? = null
) {
    var bounty: Int = startingOverride ?: rules.startingBounty
        private set

    /** Totals for the end-of-level report. */
    var earned: Int = 0
        private set
    var lost: Int = 0
        private set

    private var listener: ((Int, BountyEvent) -> Unit)? = null

    fun setListener(l: (Int, BountyEvent) -> Unit) { listener = l }

    /** True once the bounty is depleted (a game-over condition, spec §1). */
    val isDepleted: Boolean get() = bounty <= 0

    /**
     * Apply a scoring event. Positive deltas add, negative deltas subtract.
     * A [BountyEvent.critical] event zeroes the bounty when the rules say so.
     */
    fun apply(event: BountyEvent): Int {
        if (event.critical && rules.criticalCasualtyZeroesBounty) {
            lost += bounty
            bounty = 0
        } else {
            bounty += event.delta
            if (event.delta >= 0) earned += event.delta else lost += -event.delta
            if (bounty < 0) bounty = 0
        }
        listener?.invoke(bounty, event)
        return bounty
    }

    // Convenience helpers keep call sites readable and centralise reason strings.
    fun reward(delta: Int, reason: String) = apply(BountyEvent(delta, reason))
    fun penalty(delta: Int, reason: String) = apply(BountyEvent(-delta, reason))
    fun criticalCasualty(reason: String) = apply(BountyEvent(0, reason, critical = true))

    fun snapshotEarned() = earned
    fun snapshotLost() = lost
}
