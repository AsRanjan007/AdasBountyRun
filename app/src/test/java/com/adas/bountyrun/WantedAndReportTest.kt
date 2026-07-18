package com.adas.bountyrun

import com.adas.bountyrun.config.CountryProfile
import com.adas.bountyrun.config.DrivingSide
import com.adas.bountyrun.core.ReportManager
import com.adas.bountyrun.core.SafetyEvent
import com.adas.bountyrun.core.SafetyGrade
import com.adas.bountyrun.core.SafetyType
import com.adas.bountyrun.core.WantedLevelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wanted-level escalation, report grading and country config tests (spec §27). */
class WantedAndReportTest {

    @Test fun wantedEscalatesAndCaps() {
        val w = WantedLevelManager()
        repeat(7) { w.escalate() }
        assertEquals(5, w.level)
        assertEquals(6, w.patrolCount)
        assertTrue(w.hasHelicopter)
    }

    @Test fun cooperateReducesWanted() {
        val w = WantedLevelManager()
        w.escalate(); w.escalate()
        w.cooperate()
        assertEquals(1, w.level)
    }

    @Test fun sustainedCalmDecaysWanted() {
        val w = WantedLevelManager()
        w.escalate()
        w.update(WantedLevelManager.CALM_WINDOW_SECONDS + 1f, drivingSafely = true)
        assertEquals(0, w.level)
    }

    @Test fun cleanRunGradesS() {
        val rm = ReportManager()
        val grade = rm.computeGrade(collisions = 0, warnings = 2, handled = 2)
        assertEquals(SafetyGrade.S, grade)
    }

    @Test fun collisionsDropGrade() {
        val rm = ReportManager()
        assertEquals(SafetyGrade.C, rm.computeGrade(collisions = 1, warnings = 0, handled = 0))
        assertEquals(SafetyGrade.D, rm.computeGrade(collisions = 2, warnings = 0, handled = 0))
        assertEquals(SafetyGrade.F, rm.computeGrade(collisions = 3, warnings = 0, handled = 0))
    }

    @Test fun reportCountsEvents() {
        val rm = ReportManager()
        rm.log(SafetyEvent(SafetyType.PEDESTRIAN_SAVED, 0))
        rm.log(SafetyEvent(SafetyType.PEDESTRIAN_SAVED, 0))
        rm.log(SafetyEvent(SafetyType.COLLISION, 0))
        val report = rm.build(1, 500, 100, 10400, "None", true)
        assertEquals(2, report.pedestriansProtected)
        assertEquals(1, report.collisions)
    }

    @Test fun indiaIsLeftHandTraffic() {
        val india = CountryProfile.INDIA
        assertEquals(DrivingSide.LEFT, india.drivingSide)
        assertTrue(india.rightHandDrive)
    }

    @Test fun allTenCountriesRegistered() {
        assertEquals(10, CountryProfile.ALL.size)
        assertEquals(CountryProfile.INDIA, CountryProfile.byCode("IN"))
    }
}
