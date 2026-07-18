package com.adas.bountyrun.core

/** Safety grades (spec §17). */
enum class SafetyGrade(val label: String, val description: String) {
    S("S", "ADAS Safety Expert"),
    A("A", "Excellent"),
    B("B", "Good"),
    C("C", "Needs Improvement"),
    D("D", "Unsafe"),
    F("F", "Critical Risk")
}

/** Immutable end-of-level report payload (spec §17). */
data class LevelReport(
    val level: Int,
    val bountyEarned: Int,
    val bountyLost: Int,
    val bountyRemaining: Int,
    val collisions: Int,
    val nearMisses: Int,
    val pedestriansProtected: Int,
    val cyclistsProtected: Int,
    val animalsProtected: Int,
    val safeBrakingEvents: Int,
    val laneViolations: Int,
    val speedingEvents: Int,
    val redLightViolations: Int,
    val adasWarnings: Int,
    val adasHandled: Int,
    val aebActivations: Int,
    val avgReactionMs: Long,
    val minFollowingDistanceM: Float,
    val highestCollisionRisk: Float,
    val policeResponse: String,
    val grade: SafetyGrade,
    val recommendation: String,
    val prevention: List<String>,
    val passed: Boolean
)

/**
 * Aggregates [SafetyEvent]s during a level and produces a [LevelReport] with a
 * computed grade (spec §17). This is the "ReportManager" from the architecture.
 */
class ReportManager {
    private val events = mutableListOf<SafetyEvent>()
    private val reactionSamplesMs = mutableListOf<Long>()
    private val preventionNotes = LinkedHashSet<String>()

    var minFollowingDistanceM: Float = Float.MAX_VALUE
        private set
    var highestCollisionRisk: Float = 0f
        private set

    fun clear() {
        events.clear()
        reactionSamplesMs.clear()
        preventionNotes.clear()
        minFollowingDistanceM = Float.MAX_VALUE
        highestCollisionRisk = 0f
    }

    fun log(event: SafetyEvent) { events.add(event) }

    fun logReaction(ms: Long) { if (ms in 1..5000) reactionSamplesMs.add(ms) }

    fun observeFollowingDistance(meters: Float) {
        if (meters in 0f..minFollowingDistanceM) minFollowingDistanceM = meters
    }

    fun observeCollisionRisk(risk01: Float) {
        if (risk01 > highestCollisionRisk) highestCollisionRisk = risk01
    }

    /** Record an ADAS feature that could have prevented an incident (spec §17). */
    fun notePrevention(text: String) { preventionNotes.add(text) }

    private fun count(type: SafetyType) = events.count { it.type == type }

    /** Public read of a safety-event count (used by the session for bonuses). */
    fun countOf(type: SafetyType): Int = count(type)

    fun build(level: Int, bountyEarned: Int, bountyLost: Int, bountyRemaining: Int,
              policeResponse: String, passed: Boolean): LevelReport {
        val collisions = count(SafetyType.COLLISION)
        val warnings = count(SafetyType.ADAS_WARNING)
        val handled = count(SafetyType.ADAS_HANDLED)
        val avgReaction = if (reactionSamplesMs.isEmpty()) 0L
            else reactionSamplesMs.sum() / reactionSamplesMs.size
        val grade = computeGrade(collisions, warnings, handled)
        return LevelReport(
            level = level,
            bountyEarned = bountyEarned,
            bountyLost = bountyLost,
            bountyRemaining = bountyRemaining,
            collisions = collisions,
            nearMisses = count(SafetyType.NEAR_MISS),
            pedestriansProtected = count(SafetyType.PEDESTRIAN_SAVED),
            cyclistsProtected = count(SafetyType.CYCLIST_SAVED),
            animalsProtected = count(SafetyType.ANIMAL_SAVED),
            safeBrakingEvents = count(SafetyType.SAFE_BRAKE),
            laneViolations = count(SafetyType.LANE_VIOLATION),
            speedingEvents = count(SafetyType.SPEEDING),
            redLightViolations = count(SafetyType.RED_LIGHT),
            adasWarnings = warnings,
            adasHandled = handled,
            aebActivations = count(SafetyType.AEB_ACTIVATION),
            avgReactionMs = avgReaction,
            minFollowingDistanceM = if (minFollowingDistanceM == Float.MAX_VALUE) 0f else minFollowingDistanceM,
            highestCollisionRisk = highestCollisionRisk,
            policeResponse = policeResponse,
            grade = grade,
            recommendation = recommendationFor(grade),
            prevention = preventionNotes.toList(),
            passed = passed
        )
    }

    /**
     * Grade heuristic (spec §17). Collisions dominate; unhandled warnings and
     * violations degrade the grade. Pure function so it is unit-testable.
     */
    fun computeGrade(collisions: Int, warnings: Int, handled: Int): SafetyGrade {
        val violations = count(SafetyType.LANE_VIOLATION) + count(SafetyType.SPEEDING) +
            count(SafetyType.RED_LIGHT)
        val handledRatio = if (warnings == 0) 1f else handled.toFloat() / warnings
        return when {
            collisions >= 3 -> SafetyGrade.F
            collisions == 2 -> SafetyGrade.D
            collisions == 1 -> SafetyGrade.C
            violations >= 3 -> SafetyGrade.C
            handledRatio < 0.6f -> SafetyGrade.B
            violations >= 1 || handledRatio < 0.9f -> SafetyGrade.A
            else -> SafetyGrade.S
        }
    }

    private fun recommendationFor(grade: SafetyGrade): String = when (grade) {
        SafetyGrade.S -> "Flawless run. Maintain safe following distance and keep respecting ADAS alerts."
        SafetyGrade.A -> "Excellent. Reduce minor lane drift and react a touch earlier to warnings."
        SafetyGrade.B -> "Good. Act on ADAS warnings sooner — several were handled late."
        SafetyGrade.C -> "Needs improvement. Brake earlier and slow down in dense zones to avoid collisions."
        SafetyGrade.D -> "Unsafe. Increase following distance and never ignore Forward Collision Warnings."
        SafetyGrade.F -> "Critical risk. Slow down, keep eyes on the road and let AEB assist — collisions were avoidable."
    }
}
