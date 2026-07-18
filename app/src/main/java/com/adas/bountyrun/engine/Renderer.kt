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
import com.adas.bountyrun.config.HazardKind
import com.adas.bountyrun.core.DetectKind
import com.adas.bountyrun.core.SessionPhase
import com.adas.bountyrun.entities.RoadEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pseudo-3D world + automotive HUD renderer (spec §15/§16/§24).
 *
 * Draws an environment-specific scene (issue #2), shaded/recognisable road users
 * (issue #1), and rich bounty feedback — coaching banner, floating scores and a
 * screen flash (issues #4/#5). Pure rendering: reads [GameWorld], never mutates.
 * Paints are cached and drawing works in a scaled virtual space so the whole UI
 * auto-scales across phones, tablets and automotive displays.
 */
class Renderer {

    var showSensorOverlay = false
    var formFactorScale = 1f
    var uiScale = 1f
        private set

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val roadPath = Path()
    private val tmpPath = Path()

    private var w = 0f
    private var h = 0f
    private var horizonY = 0f
    private var bottomY = 0f
    private var centerX = 0f
    private var roadHalfPx = 0f
    private var scrollPhase = 0f
    private var sceneryScroll = 0f
    private var light = 1f

    private fun laneUnitPx(scale: Float) = (roadHalfPx / GameGeometry.ROAD_HALF_UNITS) * scale
    private fun projY(relZ: Float): Float {
        val s = GameGeometry.scaleAt(relZ)
        return horizonY + (bottomY - horizonY) * s
    }

    fun draw(c: Canvas, world: GameWorld, width: Int, height: Int, dt: Float) {
        uiScale = ((height / REFERENCE_HEIGHT).coerceIn(MIN_SCALE, MAX_SCALE) * formFactorScale)
            .coerceIn(MIN_SCALE, MAX_SCALE * 1.4f)

        c.save()
        c.scale(uiScale, uiScale)
        w = width / uiScale
        h = height / uiScale
        horizonY = h * 0.36f
        bottomY = h * 1.02f
        centerX = w * 0.5f
        roadHalfPx = w * 0.46f
        light = world.ambientLight.coerceIn(0.18f, 1f)

        val theme = SceneTheme.of(world.setup.environment, world.setup.timeOfDay, world.setup.weather)
        val speed = world.player.speedKmh
        scrollPhase = (scrollPhase + speed * dt * 0.06f) % 6f
        sceneryScroll += speed * GameGeometry.KMH_TO_MS * dt

        drawSky(c, theme)
        drawHorizonBackdrop(c, theme)
        drawGround(c, theme)
        drawRoad(c, theme)
        drawRoadsideProps(c, theme)
        if (theme.crossings) drawCrossings(c, theme)
        drawEntities(c, world)
        drawPlayer(c, world)
        drawWeatherOverlay(c, world)
        drawBountyFlash(c, world)
        drawFloatingScores(c, world)
        drawHud(c, world)
        if (showSensorOverlay) drawSensorOverlay(c, world)
        drawBanners(c, world)
        c.restore()
    }

    // ---------------- Sky / terrain ----------------

    private fun drawSky(c: Canvas, t: SceneTheme) {
        p.shader = LinearGradient(0f, 0f, 0f, horizonY,
            dim(t.skyTop), dim(t.skyBottom), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, horizonY, p)
        p.shader = null
        // Sun/moon glow low on the horizon adds depth.
        p.color = argb(if (light < 0.45f) 40 else 90, 255, 245, 210)
        c.drawCircle(w * 0.7f, horizonY * 0.7f, 46f, p)
    }

    /** City skyline / mountains silhouetted along the far horizon (issue #2). */
    private fun drawHorizonBackdrop(c: Canvas, t: SceneTheme) {
        if (t.cityscape) {
            p.color = dim(blend(t.skyBottom, Color.rgb(40, 46, 60), 0.7f))
            var x = 0f
            var i = 0
            while (x < w) {
                val bw = 26f + (hash(i) * 46f)
                val bh = 30f + (hash(i * 7 + 3) * (horizonY * 0.55f))
                c.drawRect(x, horizonY - bh, x + bw, horizonY, p)
                // lit windows at night
                if (light < 0.5f) {
                    p.color = argb(150, 255, 224, 130)
                    var wy = horizonY - bh + 6f
                    while (wy < horizonY - 4f) {
                        c.drawRect(x + 4f, wy, x + 8f, wy + 3f, p)
                        c.drawRect(x + bw - 9f, wy, x + bw - 5f, wy + 3f, p)
                        wy += 9f
                    }
                    p.color = dim(blend(t.skyBottom, Color.rgb(40, 46, 60), 0.7f))
                }
                x += bw + 6f
                i++
            }
        } else if (t.leftProp == RoadsideProp.ROCKS || t.label == "Mountain") {
            p.color = dim(Color.rgb(90, 104, 116))
            for (i in 0..6) {
                val cx = w * (i / 6f)
                tmpPath.reset()
                tmpPath.moveTo(cx - 120f, horizonY)
                tmpPath.lineTo(cx, horizonY - 60f - hash(i) * 80f)
                tmpPath.lineTo(cx + 120f, horizonY)
                tmpPath.close()
                c.drawPath(tmpPath, p)
            }
        }
    }

    private fun drawGround(c: Canvas, t: SceneTheme) {
        p.shader = LinearGradient(0f, horizonY, 0f, h,
            dim(t.groundFar), dim(t.groundNear), Shader.TileMode.CLAMP)
        c.drawRect(0f, horizonY, w, h, p)
        p.shader = null
    }

    private fun drawRoad(c: Canvas, t: SceneTheme) {
        val nearHalf = roadHalfPx
        val farHalf = roadHalfPx * GameGeometry.scaleAt(GameGeometry.SPAWN_Z)
        // Shoulders (slightly wider trapezoid under the road).
        p.color = dim(t.shoulderColor)
        drawTrapezoid(c, nearHalf * 1.18f, farHalf * 1.18f)
        // Road surface.
        p.color = dim(t.roadColor)
        drawTrapezoid(c, nearHalf, farHalf)

        // Median (highway/flyover) as a subtle central divider strip.
        if (t.median) {
            p.color = dim(blend(t.roadColor, Color.rgb(150, 150, 90), 0.35f))
            val mw = 0.06f
            tmpPath.reset()
            val s0 = GameGeometry.scaleAt(0f); val s1 = GameGeometry.scaleAt(GameGeometry.SPAWN_Z)
            tmpPath.moveTo(centerX - laneUnitPx(s0) * mw, bottomY)
            tmpPath.lineTo(centerX + laneUnitPx(s0) * mw, bottomY)
            tmpPath.lineTo(centerX + laneUnitPx(s1) * mw, horizonY)
            tmpPath.lineTo(centerX - laneUnitPx(s1) * mw, horizonY)
            tmpPath.close()
            c.drawPath(tmpPath, p)
        }

        // Lane dividers (dashed) between the 3 lanes.
        p.color = dim(t.laneMarking)
        for (lane in 1 until GameGeometry.LANE_COUNT) drawLaneLine(c, lane - 0.5f)
        // Solid edge lines.
        p.color = dim(t.edgeMarking)
        drawSolidEdge(c, -GameGeometry.ROAD_HALF_UNITS)
        drawSolidEdge(c, GameGeometry.ROAD_HALF_UNITS)
    }

    private fun drawTrapezoid(c: Canvas, nearHalf: Float, farHalf: Float) {
        roadPath.reset()
        roadPath.moveTo(centerX - nearHalf, bottomY)
        roadPath.lineTo(centerX + nearHalf, bottomY)
        roadPath.lineTo(centerX + farHalf, horizonY)
        roadPath.lineTo(centerX - farHalf, horizonY)
        roadPath.close()
        c.drawPath(roadPath, p)
    }

    private fun drawLaneLine(c: Canvas, laneX: Float) {
        var z = scrollPhase % 6f
        while (z < GameGeometry.SPAWN_Z) {
            val s1 = GameGeometry.scaleAt(z); val s2 = GameGeometry.scaleAt(z + 3f)
            p.strokeWidth = max(2f, 9f * s1)
            c.drawLine(centerX + laneX * laneUnitPx(s1), projY(z),
                centerX + laneX * laneUnitPx(s2), projY(z + 3f), p)
            z += 6f
        }
    }

    private fun drawSolidEdge(c: Canvas, laneX: Float) {
        val s1 = GameGeometry.scaleAt(0f); val s2 = GameGeometry.scaleAt(GameGeometry.SPAWN_Z)
        p.strokeWidth = 5f
        c.drawLine(centerX + laneX * laneUnitPx(s1), projY(0f),
            centerX + laneX * laneUnitPx(s2), projY(GameGeometry.SPAWN_Z), p)
    }

    /** Zebra crossings for the City theme. */
    private fun drawCrossings(c: Canvas, t: SceneTheme) {
        val spacing = 130f
        val phase = sceneryScroll % spacing
        var i = 4
        while (i >= 0) {
            val z = i * spacing - phase
            if (z in 2f..GameGeometry.SPAWN_Z * 0.7f) {
                val s = GameGeometry.scaleAt(z)
                p.color = argb((220 * light).toInt().coerceIn(60, 255), 235, 235, 235)
                var stripe = -1.3f
                while (stripe <= 1.3f) {
                    val x = centerX + stripe * laneUnitPx(s)
                    c.drawRect(x - 5f * s, projY(z) - 8f * s, x + 5f * s, projY(z) + 8f * s, p)
                    stripe += 0.5f
                }
            }
            i--
        }
    }

    // ---------------- Roadside scenery ----------------

    private fun drawRoadsideProps(c: Canvas, t: SceneTheme) {
        drawPropLine(c, t.leftProp, -1f, t)
        drawPropLine(c, t.rightProp, 1f, t)
    }

    private fun drawPropLine(c: Canvas, prop: RoadsideProp, side: Float, t: SceneTheme) {
        if (prop == RoadsideProp.NONE) return
        val spacing = when (prop) {
            RoadsideProp.GUARDRAIL -> 10f
            RoadsideProp.BUILDINGS -> 20f
            RoadsideProp.TREES, RoadsideProp.PALMS -> 24f
            RoadsideProp.POLES -> 42f
            RoadsideProp.ROCKS -> 18f
            RoadsideProp.SEA -> 0f
            else -> 20f
        }
        if (prop == RoadsideProp.SEA) { drawSea(c, side); return }
        val base = (sceneryScroll / spacing).toInt()
        val phase = sceneryScroll % spacing
        var i = 14
        while (i >= 0) {
            val z = i * spacing - phase
            if (z in -4f..GameGeometry.SPAWN_Z) {
                val s = GameGeometry.scaleAt(z)
                val edge = side * (GameGeometry.ROAD_HALF_UNITS + 0.35f)
                val x = centerX + edge * laneUnitPx(s)
                val y = projY(z)
                when (prop) {
                    RoadsideProp.GUARDRAIL -> drawGuardrailPost(c, x, y, s)
                    RoadsideProp.BUILDINGS -> drawBuilding(c, x, y, s, side, base + i, t)
                    RoadsideProp.TREES -> drawTree(c, x, y, s)
                    RoadsideProp.PALMS -> drawPalm(c, x, y, s)
                    RoadsideProp.POLES -> drawStreetlight(c, x, y, s, side)
                    RoadsideProp.ROCKS -> drawRock(c, x, y, s, base + i)
                    else -> {}
                }
            }
            i--
        }
        // A continuous guardrail rail line for highways/tracks.
        if (prop == RoadsideProp.GUARDRAIL) {
            val edge = side * (GameGeometry.ROAD_HALF_UNITS + 0.35f)
            val s0 = GameGeometry.scaleAt(0f); val s1 = GameGeometry.scaleAt(GameGeometry.SPAWN_Z)
            p.color = dim(Color.rgb(170, 174, 180)); p.strokeWidth = max(2f, 6f * s0)
            c.drawLine(centerX + edge * laneUnitPx(s0), projY(0f) - 26f,
                centerX + edge * laneUnitPx(s1), projY(GameGeometry.SPAWN_Z) - 4f, p)
        }
    }

    private fun drawGuardrailPost(c: Canvas, x: Float, y: Float, s: Float) {
        p.color = dim(Color.rgb(140, 144, 150))
        c.drawRect(x - 2f * s, y - 34f * s, x + 2f * s, y, p)
    }

    private fun drawBuilding(c: Canvas, x: Float, y: Float, s: Float, side: Float, idx: Int, t: SceneTheme) {
        val bw = (70f + hash(idx) * 60f) * s
        val bh = (120f + hash(idx * 3 + 1) * 260f) * s
        val bx = x + side * bw * 0.5f
        val tone = 60 + (hash(idx * 5) * 90).toInt()
        p.color = dim(Color.rgb(tone, tone + 6, tone + 14))
        c.drawRect(bx - bw / 2, y - bh, bx + bw / 2, y, p)
        // window grid (skip for distant buildings to save fill-rate)
        if (s < 0.16f) return
        val lit = light < 0.5f
        p.color = if (lit) argb(170, 255, 220, 120) else dim(Color.rgb(tone - 24, tone - 20, tone - 8))
        var wy = y - bh + 8f * s
        while (wy < y - 8f * s) {
            var wx = bx - bw / 2 + 6f * s
            while (wx < bx + bw / 2 - 6f * s) {
                if (!lit || hash((wx + wy).toInt()) > 0.45f) c.drawRect(wx, wy, wx + 5f * s, wy + 7f * s, p)
                wx += 12f * s
            }
            wy += 15f * s
        }
    }

    private fun drawTree(c: Canvas, x: Float, y: Float, s: Float) {
        p.color = dim(Color.rgb(86, 60, 38))
        c.drawRect(x - 3f * s, y - 26f * s, x + 3f * s, y, p)
        p.color = dim(Color.rgb(46, 96, 44))
        c.drawCircle(x, y - 40f * s, 20f * s, p)
        c.drawCircle(x - 12f * s, y - 30f * s, 14f * s, p)
        c.drawCircle(x + 12f * s, y - 30f * s, 14f * s, p)
    }

    private fun drawPalm(c: Canvas, x: Float, y: Float, s: Float) {
        p.color = dim(Color.rgb(96, 74, 46))
        c.drawRect(x - 3f * s, y - 60f * s, x + 3f * s, y, p)
        p.color = dim(Color.rgb(40, 120, 56))
        for (a in -2..2) {
            tmpPath.reset()
            tmpPath.moveTo(x, y - 60f * s)
            tmpPath.quadTo(x + a * 20f * s, y - 78f * s, x + a * 34f * s, y - 54f * s)
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f * s
            c.drawPath(tmpPath, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun drawStreetlight(c: Canvas, x: Float, y: Float, s: Float, side: Float) {
        p.color = dim(Color.rgb(120, 122, 128))
        c.drawRect(x - 2.5f * s, y - 70f * s, x + 2.5f * s, y, p)
        c.drawRect(x - side * 22f * s, y - 70f * s, x, y - 66f * s, p)
        // lamp + pool of light at night
        val lx = x - side * 22f * s
        p.color = if (light < 0.5f) argb(230, 255, 236, 170) else dim(Color.rgb(200, 200, 190))
        c.drawCircle(lx, y - 66f * s, 5f * s, p)
        if (light < 0.5f) {
            p.color = argb(45, 255, 240, 180)
            c.drawCircle(lx, y - 6f * s, 34f * s, p)
        }
    }

    private fun drawRock(c: Canvas, x: Float, y: Float, s: Float, idx: Int) {
        p.color = dim(Color.rgb(120, 104, 84))
        lump(c, x, y, (14f + hash(idx) * 16f) * s)
    }

    /** A rounded rock/debris cluster using the current paint colour. */
    private fun lump(c: Canvas, x: Float, y: Float, r: Float) {
        c.drawCircle(x, y - r * 0.5f, r, p)
        c.drawCircle(x - r * 0.6f, y - r * 0.2f, r * 0.7f, p)
        c.drawCircle(x + r * 0.6f, y - r * 0.2f, r * 0.7f, p)
    }

    private fun drawSea(c: Canvas, side: Float) {
        val s1 = GameGeometry.scaleAt(GameGeometry.SPAWN_Z)
        tmpPath.reset()
        val nearX = centerX + side * (roadHalfPx * 1.2f)
        val farX = centerX + side * (roadHalfPx * 1.2f * s1)
        tmpPath.moveTo(nearX, bottomY)
        tmpPath.lineTo(if (side < 0) 0f else w, bottomY)
        tmpPath.lineTo(if (side < 0) 0f else w, horizonY)
        tmpPath.lineTo(farX, horizonY)
        tmpPath.close()
        p.color = dim(Color.rgb(46, 118, 150))
        c.drawPath(tmpPath, p)
    }

    // ---------------- Road users (issue #1) ----------------

    private fun drawEntities(c: Canvas, world: GameWorld) {
        val list = world.detectables()
            .filter { it.worldZ in GameGeometry.DESPAWN_Z..GameGeometry.SPAWN_Z }
            .sortedByDescending { it.worldZ }
        for (o in list) {
            val s = GameGeometry.scaleAt(o.worldZ)
            if (s <= 0.02f) continue
            val x = centerX + o.laneX * laneUnitPx(s)
            val y = projY(o.worldZ)
            when (o.kind) {
                DetectKind.PEDESTRIAN -> drawPedestrian(c, x, y, s)
                DetectKind.CYCLIST -> drawCyclist(c, x, y, s)
                DetectKind.ANIMAL -> drawAnimal(c, x, y, s, (o as? RoadEntity)?.hazardKind)
                DetectKind.POLICE -> drawVehicle(c, x, y, s, Color.rgb(30, 42, 96), police = true)
                DetectKind.EMERGENCY -> drawVehicle(c, x, y, s, Color.rgb(200, 60, 40), police = true)
                DetectKind.HAZARD -> drawHazardObj(c, x, y, s, o as? RoadEntity)
                DetectKind.VEHICLE -> drawVehicle(c, x, y, s, (o as? RoadEntity)?.colorHint ?: Color.LTGRAY)
            }
        }
    }

    private fun shadow(c: Canvas, x: Float, y: Float, halfW: Float) {
        p.color = argb(80, 0, 0, 0)
        c.drawOval(RectF(x - halfW, y - halfW * 0.28f, x + halfW, y + halfW * 0.28f), p)
    }

    /** A car/SUV seen from behind: gradient body, cabin, tail lights, wheels. */
    private fun drawVehicle(c: Canvas, x: Float, y: Float, s: Float, color: Int, police: Boolean = false) {
        val bw = 74f * s; val bh = 118f * s
        shadow(c, x, y, bw * 0.62f)
        // wheels
        p.color = dim(Color.rgb(18, 18, 20))
        c.drawRect(x - bw * 0.52f, y - bh * 0.5f, x - bw * 0.4f, y - bh * 0.05f, p)
        c.drawRect(x + bw * 0.4f, y - bh * 0.5f, x + bw * 0.52f, y - bh * 0.05f, p)
        // body with vertical sheen
        val top = blend(color, Color.WHITE, 0.25f); val botC = blend(color, Color.BLACK, 0.35f)
        p.shader = LinearGradient(0f, y - bh, 0f, y, dim(top), dim(botC), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(x - bw / 2, y - bh, x + bw / 2, y), 12f * s, 12f * s, p)
        p.shader = null
        // roof + rear window
        p.color = dim(blend(color, Color.BLACK, 0.2f))
        c.drawRoundRect(RectF(x - bw * 0.42f, y - bh * 0.96f, x + bw * 0.42f, y - bh * 0.55f), 8f * s, 8f * s, p)
        p.color = dim(Color.rgb(24, 30, 40))
        c.drawRoundRect(RectF(x - bw * 0.36f, y - bh * 0.9f, x + bw * 0.36f, y - bh * 0.6f), 5f * s, 5f * s, p)
        // tail lights
        val glow = if (light < 0.5f) 255 else 210
        p.color = argb(255, glow, 40, 30)
        c.drawRoundRect(RectF(x - bw * 0.46f, y - bh * 0.16f, x - bw * 0.24f, y - bh * 0.04f), 3f * s, 3f * s, p)
        c.drawRoundRect(RectF(x + bw * 0.24f, y - bh * 0.16f, x + bw * 0.46f, y - bh * 0.04f), 3f * s, 3f * s, p)
        if (police) {
            val on = (System.currentTimeMillis() / 250) % 2 == 0L
            p.color = if (on) Color.rgb(40, 90, 255) else Color.rgb(255, 40, 40)
            c.drawRoundRect(RectF(x - bw * 0.3f, y - bh - 9f * s, x + bw * 0.3f, y - bh - 2f * s), 3f * s, 3f * s, p)
        }
    }

    private fun drawPedestrian(c: Canvas, x: Float, y: Float, s: Float) {
        shadow(c, x, y, 14f * s)
        val skin = dim(Color.rgb(232, 190, 150)); val shirt = dim(Color.rgb(70, 130, 220))
        val hh = 78f * s
        // legs
        p.color = dim(Color.rgb(45, 52, 66)); p.strokeWidth = 5f * s; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(x - 4f * s, y - hh * 0.42f, x - 5f * s, y, p)
        c.drawLine(x + 4f * s, y - hh * 0.42f, x + 5f * s, y, p)
        // torso
        p.color = shirt
        c.drawRoundRect(RectF(x - 8f * s, y - hh * 0.72f, x + 8f * s, y - hh * 0.36f), 4f * s, 4f * s, p)
        // arms
        p.color = shirt; p.strokeWidth = 4f * s
        c.drawLine(x - 8f * s, y - hh * 0.66f, x - 12f * s, y - hh * 0.4f, p)
        c.drawLine(x + 8f * s, y - hh * 0.66f, x + 12f * s, y - hh * 0.4f, p)
        // head
        p.color = skin
        c.drawCircle(x, y - hh * 0.82f, 9f * s, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawCyclist(c: Canvas, x: Float, y: Float, s: Float) {
        shadow(c, x, y, 20f * s)
        // wheels
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f * s; p.color = dim(Color.rgb(30, 30, 34))
        c.drawCircle(x - 14f * s, y - 8f * s, 11f * s, p)
        c.drawCircle(x + 14f * s, y - 8f * s, 11f * s, p)
        p.style = Paint.Style.FILL
        // frame
        p.color = dim(Color.rgb(210, 90, 40)); p.style = Paint.Style.STROKE; p.strokeWidth = 3f * s
        c.drawLine(x - 14f * s, y - 8f * s, x + 14f * s, y - 8f * s, p)
        c.drawLine(x, y - 30f * s, x, y - 8f * s, p)
        p.style = Paint.Style.FILL
        // rider
        val shirt = dim(Color.rgb(77, 208, 225))
        p.color = shirt
        c.drawRoundRect(RectF(x - 6f * s, y - 48f * s, x + 6f * s, y - 26f * s), 4f * s, 4f * s, p)
        p.color = dim(Color.rgb(232, 190, 150))
        c.drawCircle(x, y - 54f * s, 7f * s, p)
    }

    private fun drawAnimal(c: Canvas, x: Float, y: Float, s: Float, kind: HazardKind?) {
        shadow(c, x, y, 30f * s)
        val big = kind == HazardKind.CATTLE || kind == HazardKind.DEER || kind == HazardKind.GOAT
        val col = when (kind) {
            HazardKind.CATTLE -> Color.rgb(210, 200, 188)
            HazardKind.DEER -> Color.rgb(150, 110, 72)
            else -> Color.rgb(150, 120, 92)
        }
        p.color = dim(col)
        val bw = (if (big) 46f else 34f) * s; val bh = (if (big) 26f else 18f) * s
        c.drawRoundRect(RectF(x - bw, y - bh * 2, x + bw * 0.4f, y - bh), 8f * s, 8f * s, p) // body
        c.drawCircle(x + bw * 0.6f, y - bh * 2.1f, bh * 0.8f, p)                             // head
        p.strokeWidth = 4f * s; p.color = dim(blend(col, Color.BLACK, 0.2f))
        c.drawLine(x - bw * 0.7f, y - bh, x - bw * 0.7f, y, p)
        c.drawLine(x - bw * 0.1f, y - bh, x - bw * 0.1f, y, p)
        c.drawLine(x + bw * 0.2f, y - bh, x + bw * 0.2f, y, p)
    }

    private fun drawHazardObj(c: Canvas, x: Float, y: Float, s: Float, e: RoadEntity?) {
        when (e?.hazardKind) {
            HazardKind.POTHOLE, HazardKind.OPEN_MANHOLE, HazardKind.OIL_SPILL, HazardKind.WATERLOGGING -> {
                p.color = argb(230, 12, 12, 16)
                c.drawOval(RectF(x - 26f * s, y - 10f * s, x + 26f * s, y + 10f * s), p)
            }
            HazardKind.SPEED_BREAKER -> {
                p.color = dim(Color.rgb(230, 200, 60))
                c.drawRoundRect(RectF(x - 40f * s, y - 8f * s, x + 40f * s, y + 6f * s), 6f * s, 6f * s, p)
                p.color = dim(Color.rgb(30, 30, 30))
                var sx = x - 34f * s
                while (sx < x + 30f * s) { c.drawRect(sx, y - 8f * s, sx + 8f * s, y + 6f * s, p); sx += 16f * s }
            }
            HazardKind.ROAD_BARRIER -> {
                shadow(c, x, y, 34f * s)
                var sx = x - 34f * s; var red = true
                while (sx < x + 34f * s) {
                    p.color = if (red) dim(Color.rgb(210, 60, 50)) else dim(Color.rgb(235, 235, 235))
                    c.drawRect(sx, y - 30f * s, sx + 12f * s, y, p); sx += 12f * s; red = !red
                }
            }
            HazardKind.BROKEN_DOWN_VEHICLE, HazardKind.PARKED_VEHICLE ->
                drawVehicle(c, x, y, s, Color.rgb(120, 122, 128))
            HazardKind.ROCK, HazardKind.FALLEN_CARGO, HazardKind.TREE_BRANCH, HazardKind.LANDSLIDE -> {
                shadow(c, x, y, 26f * s)
                p.color = dim(Color.rgb(110, 92, 70)); lump(c, x, y, 22f * s)
            }
            else -> { // construction cone default
                shadow(c, x, y, 18f * s)
                tmpPath.reset()
                tmpPath.moveTo(x, y - 44f * s); tmpPath.lineTo(x - 20f * s, y); tmpPath.lineTo(x + 20f * s, y); tmpPath.close()
                p.color = dim(Color.rgb(240, 130, 40)); c.drawPath(tmpPath, p)
                p.color = Color.WHITE; c.drawRect(x - 13f * s, y - 24f * s, x + 13f * s, y - 16f * s, p)
            }
        }
    }

    private fun drawPlayer(c: Canvas, world: GameWorld) {
        val x = centerX + world.player.laneX * laneUnitPx(1f)
        val y = bottomY - 14f
        // Headlight beams at night.
        if (light < 0.45f) {
            p.color = argb(70, 255, 250, 200)
            tmpPath.reset()
            tmpPath.moveTo(x - 30f, y - 150f)
            tmpPath.lineTo(centerX - roadHalfPx * 0.55f, horizonY + 40f)
            tmpPath.lineTo(centerX + roadHalfPx * 0.55f, horizonY + 40f)
            tmpPath.lineTo(x + 30f, y - 150f); tmpPath.close()
            c.drawPath(tmpPath, p)
        }
        val bw = 100f; val bh = 156f
        shadow(c, x, y, bw * 0.62f)
        // wheels
        p.color = Color.rgb(16, 16, 18)
        c.drawRect(x - bw * 0.54f, y - bh * 0.5f, x - bw * 0.4f, y - bh * 0.04f, p)
        c.drawRect(x + bw * 0.4f, y - bh * 0.5f, x + bw * 0.54f, y - bh * 0.04f, p)
        // body sheen
        p.shader = LinearGradient(0f, y - bh, 0f, y,
            Color.rgb(58, 78, 110), Color.rgb(18, 26, 40), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(x - bw / 2, y - bh, x + bw / 2, y), 18f, 18f, p)
        p.shader = null
        // cabin + rear glass
        p.color = Color.rgb(20, 28, 42)
        c.drawRoundRect(RectF(x - bw * 0.42f, y - bh * 0.94f, x + bw * 0.42f, y - bh * 0.5f), 12f, 12f, p)
        p.color = Color.rgb(70, 96, 130)
        c.drawRoundRect(RectF(x - bw * 0.36f, y - bh * 0.88f, x + bw * 0.36f, y - bh * 0.58f), 8f, 8f, p)
        // brake lights: brighten under braking
        val braking = world.player.brakeInput > 0.05f || world.adas.state.aebActive
        p.color = if (braking) Color.rgb(255, 40, 30) else Color.rgb(150, 30, 24)
        c.drawRoundRect(RectF(x - bw * 0.46f, y - bh * 0.14f, x - bw * 0.22f, y - bh * 0.02f), 4f, 4f, p)
        c.drawRoundRect(RectF(x + bw * 0.22f, y - bh * 0.14f, x + bw * 0.46f, y - bh * 0.02f), 4f, 4f, p)
        if (braking) {
            p.color = argb(120, 255, 60, 40)
            c.drawRoundRect(RectF(x - bw * 0.5f, y - bh * 0.2f, x + bw * 0.5f, y + 6f), 6f, 6f, p)
        }
    }

    private fun drawWeatherOverlay(c: Canvas, world: GameWorld) {
        val vis = world.visibility
        if (vis < 0.95f) {
            p.color = argb(((1f - vis) * 150).toInt().coerceIn(0, 200), 205, 210, 220)
            c.drawRect(0f, horizonY, w, h * 0.72f, p)
        }
    }

    // ---------------- Bounty feedback (issues #4/#5) ----------------

    private fun drawBountyFlash(c: Canvas, world: GameWorld) {
        val f = world.bountyFlash
        if (f == 0f) return
        val a = (abs(f) * 90f).toInt().coerceIn(0, 120)
        val col = if (f > 0) Color.argb(a, 40, 210, 100) else Color.argb(a, 220, 50, 40)
        val bandW = w * 0.14f
        p.shader = LinearGradient(0f, 0f, bandW, 0f, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, bandW, h, p)
        p.shader = LinearGradient(w, 0f, w - bandW, 0f, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(w - bandW, 0f, w, h, p)
        p.shader = null
    }

    private fun drawFloatingScores(c: Canvas, world: GameWorld) {
        text.textAlign = Paint.Align.CENTER
        for (pop in world.scorePopups) {
            if (!pop.active) continue
            val t = pop.age / 1.6f
            val alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
            val x = centerX + pop.laneX * laneUnitPx(1f)
            val y = bottomY - 210f - t * 150f
            val label = when {
                pop.critical -> "CRITICAL!"
                pop.delta >= 0 -> "+${pop.delta}"
                else -> "${pop.delta}"
            }
            text.textSize = 40f + t * 10f
            // shadow for legibility
            text.color = Color.argb((alpha * 0.6f).toInt(), 0, 0, 0)
            c.drawText(label, x + 2f, y + 2f, text)
            text.color = when {
                pop.critical || pop.delta < 0 -> Color.argb(alpha, 255, 80, 66)
                else -> Color.argb(alpha, 70, 230, 120)
            }
            c.drawText(label, x, y, text)
        }
        text.textAlign = Paint.Align.LEFT
    }

    private fun drawBountyBanner(c: Canvas, world: GameWorld) {
        if (world.bountyBannerTimer <= 0f) return
        val fade = min(1f, world.bountyBannerTimer / 0.4f)
        val a = (fade * 235).toInt().coerceIn(0, 235)
        val pos = world.bountyPositive
        val accent = when {
            world.bountyCritical -> Color.rgb(220, 50, 40)
            pos -> Color.rgb(49, 208, 107)
            else -> Color.rgb(243, 144, 25)
        }
        val bw = 560f; val bh = 74f
        val x = centerX - bw / 2; val y = 100f
        p.color = Color.argb((a * 0.9f).toInt(), 14, 20, 30)
        c.drawRoundRect(RectF(x, y, x + bw, y + bh), 12f, 12f, p)
        p.color = Color.argb(a, Color.red(accent), Color.green(accent), Color.blue(accent))
        c.drawRoundRect(RectF(x, y, x + 8f, y + bh), 4f, 4f, p)
        // reason + delta + tip
        text.textAlign = Paint.Align.LEFT
        text.color = Color.argb(a, Color.red(accent), Color.green(accent), Color.blue(accent))
        text.textSize = 24f
        val icon = if (world.bountyCritical) "✕ " else if (pos) "✓ " else "⚠ "
        c.drawText(icon + world.bountyReason, x + 22f, y + 30f, text)
        text.color = Color.argb(a, 210, 214, 220); text.textSize = 16f
        c.drawText(world.bountyTip, x + 22f, y + 56f, text)
        if (!world.bountyCritical) {
            text.textAlign = Paint.Align.RIGHT
            text.color = Color.argb(a, Color.red(accent), Color.green(accent), Color.blue(accent))
            text.textSize = 34f
            val d = world.bountyDelta
            c.drawText(if (d >= 0) "+$d" else "$d", x + bw - 20f, y + 46f, text)
        }
        text.textAlign = Paint.Align.LEFT
    }

    // ---------------- HUD (spec §16) ----------------

    private fun drawHud(c: Canvas, world: GameWorld) {
        val unit = world.setup.country.speedUnit
        val session = world.session
        val adas = world.adas.state

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

        panel(c, 24f, 20f, 320f, 96f)
        text.color = Color.rgb(243, 144, 25); text.textSize = 20f
        c.drawText("BOUNTY", 40f, 48f, text)
        text.color = Color.WHITE; text.textSize = 40f
        c.drawText("${session.bounty.bounty}", 40f, 92f, text)
        text.textSize = 22f; text.color = Color.rgb(46, 155, 230)
        c.drawText("LVL ${session.level.level}", 230f, 52f, text)
        text.textSize = 16f; text.color = Color.rgb(199, 205, 212)
        c.drawText("${(session.level.progress * 100).toInt()}%", 230f, 92f, text)
        text.textSize = 13f; text.color = Color.rgb(150, 156, 164)
        c.drawText(SceneTheme.of(world.setup.environment, world.setup.timeOfDay, world.setup.weather).label, 40f, 112f, text)

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

        if (session.wanted.isWanted) {
            panel(c, w - 220f, 20f, 196f, 56f, Color.argb(90, 240, 54, 44))
            text.color = Color.rgb(255, 210, 60); text.textSize = 28f
            c.drawText("★".repeat(session.wanted.level), w - 205f, 58f, text)
            text.color = Color.WHITE; text.textSize = 14f
            c.drawText("WANTED", w - 205f, 40f, text)
        }

        panel(c, w - 250f, h - 150f, 226f, 126f)
        text.color = Color.rgb(199, 205, 212); text.textSize = 16f
        c.drawText("VEHICLE", w - 232f, h - 122f, text)
        bar(c, w - 232f, h - 116f, 190f, world.player.healthFraction, Color.rgb(49, 208, 107))
        c.drawText("SENSORS", w - 232f, h - 78f, text)
        bar(c, w - 232f, h - 72f, 190f, world.player.sensorHealthFraction(), Color.rgb(46, 155, 230))
        c.drawText("LANE ${laneLabel(world)}", w - 232f, h - 36f, text)

        if (adas.blindSpotSide != 0) {
            p.color = Color.rgb(240, 54, 44)
            val bx = if (adas.blindSpotSide < 0) 40f else w - 60f
            c.drawCircle(bx, h * 0.5f, 14f, p)
        }
    }

    private fun drawSensorOverlay(c: Canvas, world: GameWorld) {
        val x = centerX + world.player.laneX * laneUnitPx(1f)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f
        p.color = Color.argb(90, 46, 155, 230)
        tmpPath.reset()
        tmpPath.moveTo(x, bottomY - 150f)
        tmpPath.lineTo(centerX - roadHalfPx * 0.3f, horizonY)
        tmpPath.lineTo(centerX + roadHalfPx * 0.3f, horizonY); tmpPath.close()
        c.drawPath(tmpPath, p)
        text.textSize = 14f; text.textAlign = Paint.Align.LEFT
        for (o in world.detectables()) {
            if (o.worldZ !in 0f..GameGeometry.SPAWN_Z) continue
            val s = GameGeometry.scaleAt(o.worldZ)
            val ox = centerX + o.laneX * laneUnitPx(s); val oy = projY(o.worldZ)
            val bw = 80f * s; val bh = 120f * s
            p.color = boxColor(o.kind)
            c.drawRect(ox - bw / 2, oy - bh, ox + bw / 2, oy, p)
            text.color = boxColor(o.kind)
            c.drawText("${o.kind}  ${o.worldZ.toInt()}m", ox - bw / 2, oy - bh - 4f, text)
        }
        p.style = Paint.Style.FILL
    }

    private fun drawBanners(c: Canvas, world: GameWorld) {
        drawBountyBanner(c, world)

        if (world.incidentMessage.isNotEmpty()) {
            panel(c, centerX - 360f, h * 0.52f - 60f, 720f, 120f, Color.argb(210, 17, 23, 34))
            text.textAlign = Paint.Align.CENTER
            text.color = Color.rgb(243, 144, 25); text.textSize = 24f
            c.drawText(world.incidentMessage, centerX, h * 0.52f - 18f, text)
            text.color = Color.rgb(199, 205, 212); text.textSize = 18f
            c.drawText(world.incidentPrevention, centerX, h * 0.52f + 22f, text)
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

    // ---------------- HUD primitives / helpers ----------------

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
        return when { x < -0.5f -> "LEFT"; x > 0.5f -> "RIGHT"; else -> "CENTER" }
    }

    /** Dim a colour by the scene's ambient light (night handling). */
    private fun dim(color: Int): Int {
        val f = light
        return Color.rgb(
            (Color.red(color) * f).toInt().coerceIn(0, 255),
            (Color.green(color) * f).toInt().coerceIn(0, 255),
            (Color.blue(color) * f).toInt().coerceIn(0, 255)
        )
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int) = Color.argb(a, r, g, b)

    private fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt().coerceIn(0, 255),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt().coerceIn(0, 255),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt().coerceIn(0, 255)
        )
    }

    /** Deterministic 0..1 pseudo-random for stable scenery variation. */
    private fun hash(n: Int): Float {
        var x = (n * 1103515245 + 12345)
        x = (x ushr 16) and 0x7fff
        return x / 32767f
    }

    companion object {
        private const val REFERENCE_HEIGHT = 1080f
        private const val MIN_SCALE = 0.75f
        private const val MAX_SCALE = 2.2f
    }
}
