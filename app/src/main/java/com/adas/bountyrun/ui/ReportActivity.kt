package com.adas.bountyrun.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.adas.bountyrun.R
import com.adas.bountyrun.config.GameSetup
import com.adas.bountyrun.core.LevelReport
import com.adas.bountyrun.core.SafetyGrade

/**
 * End-of-level ADAS report (spec §17). Renders every tracked metric, the safety
 * grade S..F and the ADAS prevention insights, then offers progression: continue
 * to the next level (carrying bounty forward), retry, or return to the menu.
 */
class ReportActivity : AppCompatActivity() {

    companion object {
        /** Simple hand-off between activities (no persistence needed). */
        var pendingReport: LevelReport? = null
        var wasGameOver: Boolean = false
        var gameOverMessage: String = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val report = pendingReport
        if (report == null) { goToMenu(); return }
        render(report)
    }

    private fun render(r: LevelReport) {
        findViewById<TextView>(R.id.tvTitle).text =
            if (wasGameOver) "GAME OVER — LEVEL ${r.level}" else "LEVEL ${r.level} COMPLETE"

        findViewById<TextView>(R.id.tvGrade).text = r.grade.label
        findViewById<TextView>(R.id.tvGradeLabel).text = r.grade.description
        findViewById<TextView>(R.id.tvRecommendation).text =
            if (wasGameOver) gameOverMessage else r.recommendation

        findViewById<TextView>(R.id.tvStats).text = buildStats(r)

        val prevention = if (r.prevention.isEmpty())
            "Clean run — no incidents required ADAS intervention."
        else r.prevention.joinToString("\n") { "• $it" }
        findViewById<TextView>(R.id.tvPrevention).text = prevention

        val primary = findViewById<Button>(R.id.btnPrimary)
        when {
            wasGameOver -> { primary.text = "RETRY"; primary.setOnClickListener { retry() } }
            r.grade == SafetyGrade.S && r.level >= GameSetup.current.levelTable.totalLevels ->
                { primary.text = "FINISH"; primary.setOnClickListener { goToMenu() } }
            r.level >= GameSetup.current.levelTable.totalLevels ->
                { primary.text = "FINISH"; primary.setOnClickListener { goToMenu() } }
            else -> { primary.text = "NEXT LEVEL"; primary.setOnClickListener { nextLevel(r) } }
        }
        findViewById<Button>(R.id.btnMenu).setOnClickListener { goToMenu() }
    }

    private fun buildStats(r: LevelReport): String {
        fun row(label: String, value: Any) = label.padEnd(26) + value.toString()
        return buildString {
            appendLine(row("Bounty earned", "+${r.bountyEarned}"))
            appendLine(row("Bounty lost", "-${r.bountyLost}"))
            appendLine(row("Bounty remaining", r.bountyRemaining))
            appendLine(row("Collisions", r.collisions))
            appendLine(row("Near misses", r.nearMisses))
            appendLine(row("Pedestrians protected", r.pedestriansProtected))
            appendLine(row("Cyclists protected", r.cyclistsProtected))
            appendLine(row("Animals protected", r.animalsProtected))
            appendLine(row("Safe braking events", r.safeBrakingEvents))
            appendLine(row("Lane violations", r.laneViolations))
            appendLine(row("Speeding events", r.speedingEvents))
            appendLine(row("Red-light violations", r.redLightViolations))
            appendLine(row("ADAS warnings", r.adasWarnings))
            appendLine(row("ADAS warnings handled", r.adasHandled))
            appendLine(row("AEB activations", r.aebActivations))
            appendLine(row("Avg reaction", "${r.avgReactionMs} ms"))
            appendLine(row("Min following distance", "${"%.1f".format(r.minFollowingDistanceM)} m"))
            appendLine(row("Highest collision risk", "${(r.highestCollisionRisk * 100).toInt()}%"))
            append(row("Police response", r.policeResponse))
        }
    }

    private fun nextLevel(r: LevelReport) {
        GameSetup.current.startLevel = r.level + 1
        GameSetup.current.carryBounty = r.bountyRemaining
        startActivity(Intent(this, GameActivity::class.java)); finish()
    }

    private fun retry() {
        GameSetup.current.startLevel = 1
        GameSetup.current.carryBounty = null
        startActivity(Intent(this, GameActivity::class.java)); finish()
    }

    private fun goToMenu() {
        GameSetup.current.startLevel = 1
        GameSetup.current.carryBounty = null
        startActivity(Intent(this, MenuActivity::class.java)); finish()
    }
}
