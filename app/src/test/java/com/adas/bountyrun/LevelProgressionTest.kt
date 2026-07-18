package com.adas.bountyrun

import com.adas.bountyrun.config.LevelTable
import com.adas.bountyrun.core.LevelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Level speed progression tests: +10 km/h per level (spec §7/§27). */
class LevelProgressionTest {

    private val table = LevelTable.DEFAULT

    @Test fun firstLevelIsBaseSpeed() {
        assertEquals(30, table.targetSpeedKmh(1))
    }

    @Test fun eachLevelAddsTenKmh() {
        assertEquals(40, table.targetSpeedKmh(2))
        assertEquals(50, table.targetSpeedKmh(3))
        assertEquals(120, table.targetSpeedKmh(10))
    }

    @Test fun advanceIncreasesTargetByStep() {
        val lm = LevelManager(table, startLevel = 1)
        val before = lm.targetSpeedKmh
        lm.advance()
        assertEquals(before + table.speedStepKmh, lm.targetSpeedKmh)
    }

    @Test fun addDistanceCompletesAtGoal() {
        val lm = LevelManager(table)
        assertTrue(!lm.addDistance(table.levelDistanceMeters - 1f))
        assertTrue(lm.addDistance(2f))
    }

    @Test fun difficultyRampsZeroToOne() {
        assertEquals(0f, table.difficulty(1), 0.001f)
        assertEquals(1f, table.difficulty(table.totalLevels), 0.001f)
    }

    @Test fun hazardIntervalShrinksWithLevel() {
        assertTrue(table.hazardIntervalSeconds(10) < table.hazardIntervalSeconds(1))
    }
}
