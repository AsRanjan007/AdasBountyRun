package com.adas.bountyrun.core

import com.adas.bountyrun.config.GameSetup

/** Reasons a run ends (spec §1 / §31). */
enum class GameOverReason(val message: String) {
    NONE(""),
    POLICE_CAUGHT("Police caught you"),
    BOUNTY_ZERO("Bounty reached zero"),
    VEHICLE_DESTROYED("Vehicle completely damaged"),
    CRITICAL_CASUALTY("A critical casualty occurred"),
    ALL_LEVELS_CLEARED("All levels cleared — you are an ADAS Safety Expert!")
}

/** High-level session phase. */
enum class SessionPhase { DRIVING, LEVEL_COMPLETE, GAME_OVER }

/**
 * The GameManager of the architecture diagram (spec §20/§30): the single hub the
 * engine talks to. It owns the per-run managers and enforces the game-over
 * conditions. Gameplay math lives in the engine; policy lives here.
 */
class GameSession(val setup: GameSetup) {

    val bounty = BountyManager(setup.scoring, startingOverride = setup.carryBounty)
    val level = LevelManager(setup.levelTable, setup.startLevel)
    val wanted = WantedLevelManager()
    val report = ReportManager()

    var phase: SessionPhase = SessionPhase.DRIVING
        private set
    var gameOverReason: GameOverReason = GameOverReason.NONE
        private set

    /** Speed limit for the current level, in km/h (spec §7 progression). */
    val speedLimitKmh: Int get() = level.targetSpeedKmh

    /**
     * Evaluate the terminal conditions each frame (spec §1). [vehicleDisabled]
     * and [policeCaught] are supplied by the engine; casualty is signalled via
     * [criticalCasualty].
     */
    fun evaluate(vehicleDisabled: Boolean, policeCaught: Boolean) {
        if (phase == SessionPhase.GAME_OVER) return
        when {
            policeCaught -> gameOver(GameOverReason.POLICE_CAUGHT)
            bounty.isDepleted -> gameOver(GameOverReason.BOUNTY_ZERO)
            vehicleDisabled -> gameOver(GameOverReason.VEHICLE_DESTROYED)
        }
    }

    fun criticalCasualty(reason: String) {
        bounty.criticalCasualty(reason)
        gameOver(GameOverReason.CRITICAL_CASUALTY)
    }

    private fun gameOver(reason: GameOverReason) {
        gameOverReason = reason
        phase = SessionPhase.GAME_OVER
    }

    /** Called by the engine when the level distance goal is reached. */
    fun completeLevel(): LevelReport {
        // Route + perfect-completion bonuses are collision-conditional (spec §8).
        val collisions = report.countOf(com.adas.bountyrun.core.SafetyType.COLLISION)
        if (collisions == 0) {
            bounty.reward(setup.scoring.routeWithoutCollision, "Route completed without collision")
        }
        val clean = collisions == 0 &&
            report.countOf(SafetyType.SPEEDING) == 0 &&
            report.countOf(SafetyType.LANE_VIOLATION) == 0 &&
            report.countOf(SafetyType.RED_LIGHT) == 0
        if (clean) bounty.reward(setup.scoring.perfectLevelCompletion, "Perfect level completion")

        val passed = bounty.bounty > 0
        val police = if (wanted.isWanted) "Active pursuit (level ${wanted.level})" else "No police response"
        val built = report.build(
            level = level.level,
            bountyEarned = bounty.snapshotEarned(),
            bountyLost = bounty.snapshotLost(),
            bountyRemaining = bounty.bounty,
            policeResponse = police,
            passed = passed
        )
        phase = if (level.isLastLevel) {
            gameOverReason = GameOverReason.ALL_LEVELS_CLEARED
            SessionPhase.LEVEL_COMPLETE
        } else SessionPhase.LEVEL_COMPLETE
        return built
    }

    /** Prepare state for the next level (spec §7 step 12). */
    fun startNextLevel() {
        level.advance()
        report.clear()
        phase = SessionPhase.DRIVING
    }

    val isRunning: Boolean get() = phase == SessionPhase.DRIVING
    val isGameOver: Boolean get() = phase == SessionPhase.GAME_OVER
}
