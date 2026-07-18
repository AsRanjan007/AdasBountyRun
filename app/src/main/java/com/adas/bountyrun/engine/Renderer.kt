package com.adas.bountyrun.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.adas.bountyrun.adas.WarningSeverity
import com.adas.bountyrun.config.DrivingSide
import com.adas.bountyrun.config.SpeedUnit
import com.adas.bountyrun.core.DetectKind
import com.adas.bountyrun.core.SessionPhase
import kotlin.math.max

/**
 * Draws the pseudo-3D world and the automotive HUD (spec §15/§16/§24). Pure
 * rendering — reads [GameWorld] state, never mutates it. Paints are cached to
 * avoid per-frame allocation (spec §28).
 */
class Renderer {

    var showSensorOverlay = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val roadPath = Path()

    private var w = 0f
    private var h = 0f
    private var horizonY = 0f
    private var bottomY = 0f
    private var centerX = 0f
    private var roadHalfPx = 0f
    private var scrollPhase = 0f

    private fun laneUnitPx(scale: Float) = (roadHalfPx / GameGeometry.ROAD_HALF_UNITS) * scale
    private fun projY(relZ: Float): Float {
        val s = GameGeometry.scaleAt(relZ)
        return horizonY + (bottomY - horizonY) * s
    }

    fun draw(c: Canvas, world: GameWorld, width: Int, height: Int, dt: Float) {
        w = width.toFloat(); h = height.toFloat()
        horizonY = h * 0.34f
        bottomY = h * 1.02f
        centerX = w * 0.5f
        roadHalfPx = w * 0.46f
        scrollPhase = (scrollPhase + world.player.speedKmh * dt * 0.06f) % 6f

        drawSky(c, world)
        drawGround(c, world)
        drawRoad(c, world)
        drawEntities(c, world)
        drawPlayer(c, world)
        drawWeatherOverlay(c, world)
        drawHud(c, world)
        if (showSensorOverlay) drawSensorOverlay(c, world)
        drawBannersAndOverlays(c, world)
    }

    // ---------- World ----------

    private fun drawSky(c: Canvas, world: GameWorld) {
        val light = world.ambientLight
        val top = blend(Color.rgb(6, 10, 20), Color.rgb(90, 150, 210), light)
        val bot = blend(Color.rgb(10, 14, 24), Color.rgb(200, 170, 130), light * world.visibility)
        p.shader = LinearGradient(0f, 0f, 0f, horizonY, top, bot, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, horizonY, p)
        p.shader = null
    }

    private fun drawGround(c: Canvas, world: GameWorld) {
        val light = world.ambientLight
        p.color = blend(Color.rgb(8, 12, 10), Color.rgb(40, 70, 45), light)
        c.drawRect(0f, horizonY, w, h, p)
    }

    private fun drawRoad(c: Canvas, world: GameWorld) {
        val nearHalf = roadHalfPx
        val farHalf = roadHalfPx * GameGeometry.scaleAt(GameGeometry.SPAWN_Z)
        roadPath.reset()
        roadPath.moveTo(centerX - nearHalf, bottomY)
        roadPath.lineTo(centerX + nearHalf, bottomY)
        roadPath.lineTo(centerX + farHalf, horizonY)
        roadPath.lineTo(centerX - farHalf, horizonY)
        roadPath.close()
        p.color = blend(Color.rgb(24, 24, 28), Color.rgb(58, 58, 64), world.ambientLight)
        c.drawPath(roadPath, p)

        // Lane dividers (dashed), scrolling with speed.
        p.color = Color.argb((200 * world.visibility).toInt().coerceIn(60, 255), 240, 220, 120)
        for (lane in 1 until GameGeometry.LANE_COUNT) {
            val x = GameGeometry.laneCenter(lane) - 0.5f  // boundary between lanes
            drawLaneLine(c, x)
        }
        // Solid edges.
        p.color = Color.argb((220 * world.visibility).toInt().coerceIn(80, 255), 255, 255, 255)
        drawSolidEdge(c, -GameGeometry.ROAD_HALF_UNITS)
        drawSolidEdge(c, GameGeometry.ROAD_HALF_UNITS)

        // Driving-side hint marker near the player's kerb.
        p.color = Color.argb(120, 46, 155, 230)
        val side = if (world.setup.country.drivingSide == DrivingSide.LEFT) -1f else 1f
        val s = GameGeometry.scaleAt(2f)
        c.drawCircle(centerX + side * laneUnitPx(s) * 1.3f, projY(2f), 5f, p)
    }

    private fun drawLaneLine(c: Canvas, laneX: Float) {
        var z = (scrollPhase % 6f)
        while (z < GameGeometry.SPAWN_Z) {
            val s1 = GameGeometry.scaleAt(z)
            val s2 = GameGeometry.scaleAt(z + 3f)
            val y1 = projY(z); val y2 = projY(z + 3f)
            val x1 = centerX + laneX * laneUnitPx(s1)
            val x2 = centerX + laneX * laneUnitPx(s2)
            p.strokeWidth = max(2f, 8f * s1)
            c.drawLine(x1, y1, x2, y2, p)
            z += 6f
        }
    }

    private fun drawSolidEdge(c: Canvas, laneX: Float) {
        val s1 = GameGeometry.scaleAt(0f); val s2 = GameGeometry.scaleAt(GameGeometry.SPAWN_Z)
        p.strokeWidth = 4f
        c.drawLine(centerX + laneX * laneUnitPx(s1), projY(0f),
            centerX + laneX * laneUnitPx(s2), projY(GameGeometry.SPAWN_Z), p)
    }

    private fun drawEntities(c: Canvas, world: GameWorld) {
        val list = world.detectables().filter { it.worldZ in GameGeometry.DESPAWN_Z..GameGeometry.SPAWN_Z }
            .sortedByDescending { it.worldZ }
        for (o in list) {
            val s = GameGeometry.scaleAt(o.worldZ)
            if (s <= 0.02f) continue
            val x = centerX + o.laneX * laneUnitPx(s)
            val y = projY(o.worldZ)
            when (o.kind) {
                DetectKind.PEDESTRIAN, DetectKind.CYCLIST -> drawPerson(c, x, y, s, o.kind)
                DetectKind.ANIMAL -> drawAnimal(c, x, y, s)
                DetectKind.POLICE -> drawCar(c, x, y, s, Color.rgb(20, 30, 80), police = true)
                DetectKind.EMERGENCY -> drawCar(c, x, y, s, Color.rgb(200, 60, 40))
                DetectKind.HAZARD -> drawHazard(c, x, y, s, (o as? com.adas.bountyrun.entities.RoadEntity)?.colorHint ?: Color.rgb(255, 180, 70))
                DetectKind.VEHICLE -> drawCar(c, x, y, s, (o as? com.adas.bountyrun.entities.RoadEntity)?.colorHint ?: Color.LTGRAY)
            }
        }
    }

    private fun drawCar(c: Canvas, x: Float, y: Float, s: Float, color: Int, police: Boolean = false) {
        val bw = 70f * s; val bh = 120f * s
        p.color = color
        val r = RectF(x - bw / 2, y - bh, x + bw / 2, y)
        c.drawRoundRect(r, 10f * s, 10f * s, p)
        // Rear window + tail lights.
        p.color = Color.argb(160, 20, 24, 30)
        c.drawRoundRect(RectF(x - bw * 0.4f, y - bh * 0.8f, x + bw * 0.4f, y - bh * 0.5f), 6f * s, 6f * s, p)
        p.color = Color.rgb(220, 40, 30)
        c.drawRect(x - bw * 0.45f, y - bh * 0.12f, x - bw * 0.2f, y - bh * 0.03f, p)
        c.drawRect(x + bw * 0.2f, y - bh * 0.12f, x + bw * 0.45f, y - bh * 0.03f, p)
        if (police) {
            val on = (System.currentTimeMillis() / 250) % 2 == 0L
            p.color = if (on) Color.rgb(40, 90, 255) else Color.rgb(255, 40, 40)
            c.drawRect(x - bw * 0.35f, y - bh - 10f * s, x + bw * 0.35f, y - bh - 3f * s, p)
        }
    }

    private fun drawPerson(c: Canvas, x: Float, y: Float, s: Float, kind: DetectKind) {
        p.color = if (kind == DetectKind.CYCLIST) Color.rgb(77, 208, 225) else Color.rgb(255, 241, 118)
        val hh = 70f * s
        c.drawCircle(x, y - hh, 9f * s, p)                        // head
        c.drawRoundRect(RectF(x - 7f * s, y - hh + 8f * s, x + 7f * s, y), 4f * s, 4f * s, p) // body
        if (kind == DetectKind.CYCLIST) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f * s
            c.drawCircle(x, y, 12f * s, p); p.style = Paint.Style.FILL
        }
    }

    private fun drawAnimal(c: Canvas, x: Float, y: Float, s: Float) {
        p.color = Color.rgb(161, 136, 127)
        c.drawRoundRect(RectF(x - 26f * s, y - 30f * s, x + 26f * s, y), 8f * s, 8f * s, p)
        c.drawCircle(x + 26f * s, y - 26f * s, 10f * s, p)
    }

    private fun drawHazard(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        p.color = color
        val path = Path()
        path.moveTo(x, y - 40f * s); path.lineTo(x - 22f * s, y); path.lineTo(x + 22f * s, y); path.close()
        c.drawPath(path, p)
        p.color = Color.WHITE
        c.drawRect(x - 14f * s, y - 20f * s, x + 14f * s, y - 14f * s, p)
    }

    private fun drawPlayer(c: Canvas, world: GameWorld) {
        val s = 1.25f
        val x = centerX + world.player.laneX * laneUnitPx(1f)
        val y = bottomY - 12f
        // Headlight cones at night.
        if (world.ambientLight < 0.45f) {
            p.color = Color.argb(70, 255, 250, 200)
            val path = Path()
            path.moveTo(x - 30f, y - 150f); path.lineTo(centerX - roadHalfPx * 0.5f, horizonY + 40f)
            path.lineTo(centerX + roadHalfPx * 0.5f, horizonY + 40f); path.lineTo(x + 30f, y - 150f); path.close()
            c.drawPath(path, p)
        }
        val bw = 92f * s; val bh = 150f * s
        p.color = Color.rgb(25, 34, 48)
        c.drawRoundRect(RectF(x - bw / 2, y - bh, x + bw / 2, y), 14f, 14f, p)
        p.color = Color.rgb(12, 18, 28)
        c.drawRoundRect(RectF(x - bw * 0.4f, y - bh * 0.78f, x + bw * 0.4f, y - bh * 0.45f), 8f, 8f, p)
        // Headlights (front is toward horizon, so draw at top of sprite).
        p.color = Color.rgb(120, 200, 255)
        c.drawRect(x - bw * 0.42f, y - bh, x - bw * 0.2f, y - bh * 0.9f, p)
        c.drawRect(x + bw * 0.2f, y - bh, x + bw * 0.42f, y - bh * 0.9f, p)
    }

    private fun drawWeatherOverlay(c: Canvas, world: GameWorld) {
        val vis = world.visibility
        if (vis < 0.95f) {
            p.color = Color.argb(((1f - vis) * 150).toInt().coerceIn(0, 200), 200, 205, 215)
            c.drawRect(0f, horizonY, w, h * 0.7f, p)
        }
    }

    // ---------- HUD (spec §16) ----------

    private fun drawHud(c: Canvas, world: GameWorld) {
        val unit = world.setup.country.speedUnit
        val session = world.session
        val adas = world.adas.state

        // Speed cluster (bottom-left).
        panel(c, 24f, h - 150f, 250f, 126f)
        val shown = unit.fromKmh(world.player.speedKmh).toInt()
        val limitShown = unit.fromKmh(session.speedLimitKmh.toFloat()).toInt()
        text.color = Color.WHITE; text.textSize = 58f; text.textAlign = Paint.Align.LEFT
        c.drawText("$shown", 44f, h - 78f, text)
        text.textSize = 22f; text.color = Color.rgb(199, 205, 212)
        c.drawText(unit.label, 44f, h - 48f, text)
        text.textSize = 20f
        c.drawText("LIMIT $limitShown", 150f, h - 88f, text)
        c.drawText("GEAR ${gearFor(world.player.speedKmh)}", 150f, h - 60f, text)

        // Bounty + level (top-left).
        panel(c, 24f, 20f, 320f, 96f)
        text.color = Color.rgb(243, 144, 25); text.textSize = 20f
        c.drawText("BOUNTY", 40f, 48f, text)
        text.color = Color.WHITE; text.textSize = 40f
        c.drawText("${session.bounty.bounty}", 40f, 92f, text)
        text.textSize = 22f; text.color = Color.rgb(46, 155, 230)
        c.drawText("LVL ${session.level.level}", 230f, 52f, text)
        text.textSize = 16f; text.color = Color.rgb(199, 205, 212)
        c.drawText("${(session.level.progress * 100).toInt()}%", 230f, 92f, text)

        // ADAS status + warning band (top-center).
        val sev = adas.severity
        panel(c, centerX - 170f, 20f, 340f, 70f, severityColor(sev, 60))
        text.textAlign = Paint.Align.CENTER
        text.color = severityColor(sev, 255); text.textSize = 22f
        c.drawText(if (adas.aebActive) "AEB" else adas.activeFeature, centerX, 48f, text)
        text.color = Color.WHITE; text.textSize = 16f
        val ttcStr = if (adas.ttc < 9f) "TTC ${"%.1f".format(adas.ttc)}s" else "TTC --"
        val fd = if (adas.followingDistanceM >= 0) "  DIST ${adas.followingDistanceM.toInt()}m" else ""
        c.drawText("$ttcStr$fd", centerX, 78f, text)
        text.textAlign = Paint.Align.LEFT

        // Wanted stars (top-right).
        if (session.wanted.isWanted) {
            panel(c, w - 220f, 20f, 196f, 56f, Color.argb(90, 240, 54, 44))
            text.color = Color.rgb(255, 210, 60); text.textSize = 28f
            c.drawText("★".repeat(session.wanted.level), w - 205f, 58f, text)
            text.color = Color.WHITE; text.textSize = 14f
            c.drawText("WANTED", w - 205f, 40f, text)
        }

        // Vehicle + sensor health (bottom-right).
        panel(c, w - 250f, h - 150f, 226f, 126f)
        text.color = Color.rgb(199, 205, 212); text.textSize = 16f
        c.drawText("VEHICLE", w - 232f, h - 122f, text)
        bar(c, w - 232f, h - 116f, 190f, world.player.healthFraction, Color.rgb(49, 208, 107))
        c.drawText("SENSORS", w - 232f, h - 78f, text)
        bar(c, w - 232f, h - 72f, 190f, world.player.sensorHealthFraction(), Color.rgb(46, 155, 230))
        c.drawText("LANE ${laneLabel(world)}", w - 232f, h - 36f, text)

        // Blind-spot indicators.
        if (adas.blindSpotSide != 0) {
            p.color = Color.rgb(240, 54, 44)
            val bx = if (adas.blindSpotSide < 0) 40f else w - 60f
            c.drawCircle(bx, h * 0.5f, 14f, p)
        }
    }

    private fun drawSensorOverlay(c: Canvas, world: GameWorld) {
        // Radar cone from the player.
        val x = centerX + world.player.laneX * laneUnitPx(1f)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f
        p.color = Color.argb(90, 46, 155, 230)
        val cone = Path()
        cone.moveTo(x, bottomY - 150f)
        cone.lineTo(centerX - roadHalfPx * 0.3f, horizonY)
        cone.lineTo(centerX + roadHalfPx * 0.3f, horizonY)
        cone.close()
        c.drawPath(cone, p)
        // Bounding boxes + labels.
        text.textSize = 14f; text.textAlign = Paint.Align.LEFT
        for (o in world.detectables()) {
            if (o.worldZ !in 0f..GameGeometry.SPAWN_Z) continue
            val s = GameGeometry.scaleAt(o.worldZ)
            val ox = centerX + o.laneX * laneUnitPx(s)
            val oy = projY(o.worldZ)
            val bw = 80f * s; val bh = 120f * s
            p.color = boxColor(o.kind)
            c.drawRect(ox - bw / 2, oy - bh, ox + bw / 2, oy, p)
            text.color = boxColor(o.kind)
            c.drawText("${o.kind}  ${o.worldZ.toInt()}m", ox - bw / 2, oy - bh - 4f, text)
        }
        p.style = Paint.Style.FILL
    }

    private fun drawBannersAndOverlays(c: Canvas, world: GameWorld) {
        // ADAS awareness incident message (spec §11/§3A).
        if (world.incidentMessage.isNotEmpty()) {
            panel(c, centerX - 360f, h * 0.5f - 60f, 720f, 120f, Color.argb(210, 17, 23, 34))
            text.textAlign = Paint.Align.CENTER
            text.color = Color.rgb(243, 144, 25); text.textSize = 24f
            c.drawText(world.incidentMessage, centerX, h * 0.5f - 18f, text)
            text.color = Color.rgb(199, 205, 212); text.textSize = 18f
            c.drawText(world.incidentPrevention, centerX, h * 0.5f + 22f, text)
            text.textAlign = Paint.Align.LEFT
        }

        if (world.session.phase == SessionPhase.GAME_OVER) {
            p.color = Color.argb(190, 5, 8, 14)
            c.drawRect(0f, 0f, w, h, p)
            text.textAlign = Paint.Align.CENTER
            text.color = Color.rgb(240, 54, 44); text.textSize = 52f
            c.drawText("GAME OVER", centerX, h * 0.42f, text)
            text.color = Color.WHITE; text.textSize = 26f
            c.drawText(world.session.gameOverReason.message, centerX, h * 0.52f, text)
            text.color = Color.rgb(199, 205, 212); text.textSize = 18f
            c.drawText("Bounty ${world.session.bounty.bounty}  •  Tap to see report", centerX, h * 0.60f, text)
            text.textAlign = Paint.Align.LEFT
        }
    }

    // ---------- HUD primitives ----------

    private fun panel(c: Canvas, x: Float, y: Float, wp: Float, hp: Float, fill: Int = Color.argb(150, 17, 23, 34)) {
        p.color = fill
        c.drawRoundRect(RectF(x, y, x + wp, y + hp), 12f, 12f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = Color.argb(60, 46, 155, 230)
        c.drawRoundRect(RectF(x, y, x + wp, y + hp), 12f, 12f, p)
        p.style = Paint.Style.FILL
    }

    private fun bar(c: Canvas, x: Float, y: Float, wp: Float, frac: Float, color: Int) {
        p.color = Color.argb(80, 90, 90, 100)
        c.drawRoundRect(RectF(x, y, x + wp, y + 12f), 6f, 6f, p)
        p.color = if (frac < 0.3f) Color.rgb(240, 54, 44) else color
        c.drawRoundRect(RectF(x, y, x + wp * frac.coerceIn(0f, 1f), y + 12f), 6f, 6f, p)
    }

    private fun severityColor(s: WarningSeverity, alpha: Int): Int = when (s) {
        WarningSeverity.SAFE -> Color.argb(alpha, 49, 208, 107)
        WarningSeverity.CAUTION -> Color.argb(alpha, 245, 208, 32)
        WarningSeverity.HIGH -> Color.argb(alpha, 243, 144, 25)
        WarningSeverity.DANGER -> Color.argb(alpha, 240, 54, 44)
    }

    private fun boxColor(k: DetectKind): Int = when (k) {
        DetectKind.PEDESTRIAN, DetectKind.CYCLIST -> Color.rgb(255, 235, 59)
        DetectKind.ANIMAL -> Color.rgb(255, 152, 0)
        DetectKind.POLICE -> Color.rgb(64, 120, 255)
        DetectKind.EMERGENCY -> Color.rgb(240, 54, 44)
        DetectKind.HAZARD -> Color.rgb(255, 112, 67)
        DetectKind.VEHICLE -> Color.rgb(46, 155, 230)
    }

    private fun gearFor(kmh: Float): String = when {
        kmh < 1f -> "N"; kmh < 20f -> "1"; kmh < 40f -> "2"; kmh < 65f -> "3"
        kmh < 90f -> "4"; kmh < 120f -> "5"; else -> "6"
    }

    private fun laneLabel(world: GameWorld): String {
        val x = world.player.laneX
        return when {
            x < -0.5f -> "LEFT"; x > 0.5f -> "RIGHT"; else -> "CENTER"
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val ar = Color.red(a) + ((Color.red(b) - Color.red(a)) * tt).toInt()
        val ag = Color.green(a) + ((Color.green(b) - Color.green(a)) * tt).toInt()
        val ab = Color.blue(a) + ((Color.blue(b) - Color.blue(a)) * tt).toInt()
        return Color.rgb(ar.coerceIn(0, 255), ag.coerceIn(0, 255), ab.coerceIn(0, 255))
    }
}
