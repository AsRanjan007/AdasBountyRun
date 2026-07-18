package com.adas.bountyrun

import com.adas.bountyrun.config.ScoringRules
import com.adas.bountyrun.core.BountyEvent
import com.adas.bountyrun.core.BountyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bounty calculation tests (spec §27). */
class BountyManagerTest {

    private val rules = ScoringRules.DEFAULT

    @Test fun startsAtConfiguredBounty() {
        assertEquals(10_000, BountyManager(rules).bounty)
    }

    @Test fun rewardAddsAndTracksEarned() {
        val bm = BountyManager(rules)
        bm.reward(300, "pedestrian")
        assertEquals(10_300, bm.bounty)
        assertEquals(300, bm.snapshotEarned())
    }

    @Test fun penaltySubtractsAndTracksLost() {
        val bm = BountyManager(rules)
        bm.penalty(500, "minor collision")
        assertEquals(9_500, bm.bounty)
        assertEquals(500, bm.snapshotLost())
    }

    @Test fun bountyNeverGoesNegative() {
        val bm = BountyManager(rules)
        bm.penalty(999_999, "huge")
        assertEquals(0, bm.bounty)
        assertTrue(bm.isDepleted)
    }

    @Test fun criticalCasualtyZeroesBounty() {
        val bm = BountyManager(rules)
        bm.apply(BountyEvent(0, "critical", critical = true))
        assertEquals(0, bm.bounty)
        assertTrue(bm.isDepleted)
    }

    @Test fun criticalRespectsRuleFlag() {
        val noZero = ScoringRules(criticalCasualtyZeroesBounty = false)
        val bm = BountyManager(noZero)
        bm.apply(BountyEvent(0, "critical", critical = true))
        assertEquals(10_000, bm.bounty)
        assertFalse(bm.isDepleted)
    }

    @Test fun startingOverrideCarriesBounty() {
        assertEquals(7_777, BountyManager(rules, startingOverride = 7_777).bounty)
    }
}
