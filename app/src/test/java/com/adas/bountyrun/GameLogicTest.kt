package com.adas.bountyrun

import com.adas.bountyrun.adas.SensorModel
import com.adas.bountyrun.config.GameSetup
import com.adas.bountyrun.config.VehicleSpec
import com.adas.bountyrun.core.DetectKind
import com.adas.bountyrun.core.GameSession
import com.adas.bountyrun.engine.CollisionManager
import com.adas.bountyrun.entities.RoadEntity
import com.adas.bountyrun.entities.PlayerCar
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vehicle physics, sensor perception and collision resolution (spec §27). */
class GameLogicTest {

    private fun spinUp(car: PlayerCar, seconds: Float, throttle: Float = 1f) {
        car.throttleInput = throttle
        var t = 0f
        while (t < seconds) { car.update(0.1f, 1000f); t += 0.1f }
    }

    @Test fun carAcceleratesUnderThrottle() {
        val car = PlayerCar(VehicleSpec.SEDAN_SAFE, gripFactor = 1f)
        spinUp(car, 2f)
        assertTrue("expected forward motion", car.speedKmh > 20f)
    }

    @Test fun carBrakesToRest() {
        val car = PlayerCar(VehicleSpec.SEDAN_SAFE, gripFactor = 1f)
        spinUp(car, 2f)
        car.throttleInput = 0f; car.brakeInput = 1f
        repeat(40) { car.update(0.1f, 1000f) }
        assertTrue("expected near stop", car.speedKmh < 2f)
    }

    @Test fun sensorFindsSlowerLeadVehicle() {
        val car = PlayerCar(VehicleSpec.SEDAN_SAFE, gripFactor = 1f)
        spinUp(car, 3f) // ~60 km/h
        val lead = RoadEntity().apply {
            spawn(40f, 0f, 20f, DetectKind.VEHICLE, RoadEntity.Behavior.LANE_FOLLOW,
                0.34f, 2.2f, 0, "lead")
        }
        val threat = SensorModel().forwardThreat(car, listOf(lead), car.speedKmh)
        assertTrue("threat detected", threat != null)
        assertTrue("closing on lead", threat!!.ttc in 0f..30f)
    }

    @Test fun highSpeedPedestrianIsCriticalGameOver() {
        val session = GameSession(GameSetup())
        val cm = CollisionManager(session)
        val car = PlayerCar(session.setup.vehicle, gripFactor = 1f)
        spinUp(car, 3f) // above the 30 km/h critical-casualty threshold
        val ped = RoadEntity().apply {
            spawn(0f, 0f, 0f, DetectKind.PEDESTRIAN, RoadEntity.Behavior.CROSSING,
                0.16f, 0.4f, 0, "Pedestrian")
        }
        val result = cm.resolve(car, ped, nowMs = 0)
        assertTrue("critical casualty", result.critical)
        assertTrue("game over triggered", session.isGameOver)
        assertTrue("bounty zeroed", session.bounty.bounty == 0)
    }
}
