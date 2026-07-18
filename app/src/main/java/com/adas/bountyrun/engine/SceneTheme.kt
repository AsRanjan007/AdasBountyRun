package com.adas.bountyrun.engine

import android.graphics.Color
import com.adas.bountyrun.config.EnvironmentType
import com.adas.bountyrun.config.TimeOfDay
import com.adas.bountyrun.config.Weather

/** Type of roadside decoration drawn along each edge (issue #2). */
enum class RoadsideProp { NONE, GUARDRAIL, BUILDINGS, TREES, POLES, ROCKS, PALMS, SEA }

/**
 * Per-environment visual identity (issue #2). Highway, City, Countryside, etc.
 * each get a distinct sky, terrain, road surface and roadside scenery so the
 * player's environment selection is clearly visible. Colours are the daytime
 * base; the renderer dims them by [TimeOfDay] ambient light and tints by weather.
 */
data class SceneTheme(
    val label: String,
    val skyTop: Int,
    val skyBottom: Int,
    val groundNear: Int,
    val groundFar: Int,
    val roadColor: Int,
    val shoulderColor: Int,
    val laneMarking: Int,
    val edgeMarking: Int,
    val leftProp: RoadsideProp,
    val rightProp: RoadsideProp,
    val median: Boolean,
    val cityscape: Boolean,
    val trafficLights: Boolean,
    val crossings: Boolean,
    val streetlights: Boolean
) {
    companion object {
        fun of(env: EnvironmentType, time: TimeOfDay, weather: Weather): SceneTheme = when (env) {
            EnvironmentType.CITY -> SceneTheme(
                "City",
                skyTop = Color.rgb(74, 120, 168), skyBottom = Color.rgb(150, 170, 190),
                groundNear = Color.rgb(48, 50, 54), groundFar = Color.rgb(60, 64, 70),
                roadColor = Color.rgb(46, 47, 52), shoulderColor = Color.rgb(90, 92, 98),
                laneMarking = Color.rgb(240, 220, 120), edgeMarking = Color.rgb(235, 235, 235),
                leftProp = RoadsideProp.BUILDINGS, rightProp = RoadsideProp.BUILDINGS,
                median = false, cityscape = true, trafficLights = true, crossings = true, streetlights = true
            )
            EnvironmentType.COUNTRYSIDE -> SceneTheme(
                "Countryside",
                skyTop = Color.rgb(96, 150, 200), skyBottom = Color.rgb(200, 220, 210),
                groundNear = Color.rgb(70, 110, 52), groundFar = Color.rgb(96, 140, 70),
                roadColor = Color.rgb(58, 56, 52), shoulderColor = Color.rgb(120, 100, 70),
                laneMarking = Color.rgb(235, 225, 150), edgeMarking = Color.rgb(230, 230, 230),
                leftProp = RoadsideProp.TREES, rightProp = RoadsideProp.TREES,
                median = false, cityscape = false, trafficLights = false, crossings = false, streetlights = false
            )
            EnvironmentType.MOUNTAIN_ROAD -> SceneTheme(
                "Mountain",
                skyTop = Color.rgb(110, 150, 190), skyBottom = Color.rgb(210, 220, 225),
                groundNear = Color.rgb(70, 92, 66), groundFar = Color.rgb(96, 116, 96),
                roadColor = Color.rgb(56, 54, 54), shoulderColor = Color.rgb(110, 100, 88),
                laneMarking = Color.rgb(235, 225, 150), edgeMarking = Color.rgb(230, 230, 230),
                leftProp = RoadsideProp.ROCKS, rightProp = RoadsideProp.TREES,
                median = false, cityscape = false, trafficLights = false, crossings = false, streetlights = false
            )
            EnvironmentType.COASTAL_ROAD -> SceneTheme(
                "Coastal",
                skyTop = Color.rgb(90, 160, 210), skyBottom = Color.rgb(200, 230, 240),
                groundNear = Color.rgb(200, 190, 150), groundFar = Color.rgb(150, 200, 210),
                roadColor = Color.rgb(60, 60, 64), shoulderColor = Color.rgb(200, 185, 140),
                laneMarking = Color.rgb(240, 220, 120), edgeMarking = Color.rgb(235, 235, 235),
                leftProp = RoadsideProp.PALMS, rightProp = RoadsideProp.SEA,
                median = false, cityscape = false, trafficLights = false, crossings = false, streetlights = false
            )
            EnvironmentType.DUSTY_ROAD -> SceneTheme(
                "Dusty Road",
                skyTop = Color.rgb(180, 160, 120), skyBottom = Color.rgb(210, 190, 150),
                groundNear = Color.rgb(170, 140, 96), groundFar = Color.rgb(190, 165, 120),
                roadColor = Color.rgb(96, 86, 70), shoulderColor = Color.rgb(160, 135, 95),
                laneMarking = Color.rgb(210, 195, 140), edgeMarking = Color.rgb(210, 205, 190),
                leftProp = RoadsideProp.ROCKS, rightProp = RoadsideProp.ROCKS,
                median = false, cityscape = false, trafficLights = false, crossings = false, streetlights = false
            )
            EnvironmentType.RACING_TRACK -> SceneTheme(
                "Racing Track",
                skyTop = Color.rgb(70, 110, 160), skyBottom = Color.rgb(150, 175, 195),
                groundNear = Color.rgb(64, 120, 70), groundFar = Color.rgb(80, 140, 84),
                roadColor = Color.rgb(40, 40, 44), shoulderColor = Color.rgb(200, 60, 60),
                laneMarking = Color.rgb(240, 240, 240), edgeMarking = Color.rgb(240, 240, 240),
                leftProp = RoadsideProp.GUARDRAIL, rightProp = RoadsideProp.GUARDRAIL,
                median = false, cityscape = false, trafficLights = false, crossings = false, streetlights = false
            )
            EnvironmentType.FLYOVER_TOLL -> SceneTheme(
                "Flyover & Toll",
                skyTop = Color.rgb(80, 125, 170), skyBottom = Color.rgb(160, 178, 195),
                groundNear = Color.rgb(70, 74, 80), groundFar = Color.rgb(92, 96, 104),
                roadColor = Color.rgb(70, 70, 76), shoulderColor = Color.rgb(120, 122, 128),
                laneMarking = Color.rgb(240, 220, 120), edgeMarking = Color.rgb(235, 235, 235),
                leftProp = RoadsideProp.GUARDRAIL, rightProp = RoadsideProp.GUARDRAIL,
                median = true, cityscape = true, trafficLights = false, crossings = false, streetlights = true
            )
            EnvironmentType.NIGHT_ROAD -> SceneTheme(
                "Night Road",
                skyTop = Color.rgb(10, 14, 30), skyBottom = Color.rgb(30, 36, 56),
                groundNear = Color.rgb(20, 26, 22), groundFar = Color.rgb(28, 34, 30),
                roadColor = Color.rgb(34, 34, 40), shoulderColor = Color.rgb(70, 72, 78),
                laneMarking = Color.rgb(220, 205, 120), edgeMarking = Color.rgb(220, 220, 220),
                leftProp = RoadsideProp.POLES, rightProp = RoadsideProp.POLES,
                median = false, cityscape = true, trafficLights = false, crossings = false, streetlights = true
            )
            else -> SceneTheme( // HIGHWAY (default)
                "Highway",
                skyTop = Color.rgb(64, 118, 178), skyBottom = Color.rgb(176, 200, 220),
                groundNear = Color.rgb(58, 96, 54), groundFar = Color.rgb(84, 126, 74),
                roadColor = Color.rgb(52, 53, 58), shoulderColor = Color.rgb(96, 98, 104),
                laneMarking = Color.rgb(240, 220, 120), edgeMarking = Color.rgb(240, 240, 240),
                leftProp = RoadsideProp.GUARDRAIL, rightProp = RoadsideProp.GUARDRAIL,
                median = true, cityscape = false, trafficLights = false, crossings = false, streetlights = true
            )
        }
    }
}
