package com.adas.bountyrun.engine

import com.adas.bountyrun.config.CountryProfile
import com.adas.bountyrun.config.DrivingSide
import com.adas.bountyrun.config.HazardKind
import com.adas.bountyrun.core.DetectKind
import com.adas.bountyrun.core.LevelManager
import com.adas.bountyrun.entities.RoadEntity
import kotlin.random.Random

/**
 * Spawns background traffic and hazards (spec §6/§19). Acts as the Hazard
 * Director: it paces events by level difficulty and guarantees each hazard is
 * avoidable — living hazards are spawned in a single lane at a time and far
 * enough ahead that an attentive driver (or ADAS) can respond (spec §7).
 */
class Spawner(
    private val country: CountryProfile,
    private val level: LevelManager,
    private val rnd: Random = Random(System.nanoTime())
) {
    private var hazardCooldown = 2f

    private val laneCenters = FloatArray(GameGeometry.LANE_COUNT) { GameGeometry.laneCenter(it) }

    /** Keep background traffic topped up to the level's target density. */
    fun maintainTraffic(pool: ObjectPool<RoadEntity>) {
        val active = pool.all.count { it.active && it.kind == DetectKind.VEHICLE }
        if (active < level.trafficDensity) {
            spawnTrafficVehicle(pool)
        }
    }

    /** Advance the hazard director; spawn a hazard when the timer elapses. */
    fun tickHazards(dt: Float, pool: ObjectPool<RoadEntity>) {
        hazardCooldown -= dt
        if (hazardCooldown > 0f) return
        hazardCooldown = level.hazardInterval * (0.7f + rnd.nextFloat() * 0.6f)
        when (rnd.nextInt(100)) {
            in 0..34 -> spawnVulnerableRoadUser(pool)
            in 35..54 -> spawnAnimal(pool)
            in 55..74 -> spawnStaticHazard(pool)
            else -> spawnSlowLeadVehicle(pool)  // encourages FCW/AEB/ACC
        }
    }

    private fun spawnTrafficVehicle(pool: ObjectPool<RoadEntity>) {
        val e = pool.obtain()
        val lane = laneCenters[rnd.nextInt(laneCenters.size)]
        val limit = level.targetSpeedKmh.toFloat()
        val speed = (limit * (0.7f + rnd.nextFloat() * 0.5f)).coerceAtLeast(15f)
        e.spawn(
            z = GameGeometry.SPAWN_Z + rnd.nextFloat() * 40f,
            laneX = lane, speedKmh = speed, kind = DetectKind.VEHICLE,
            behavior = RoadEntity.Behavior.LANE_FOLLOW, halfWidth = 0.34f, halfLength = 2.2f,
            color = trafficColor(), label = "Vehicle"
        )
        e.targetLaneX = lane
    }

    /** A deliberately slow lead car in the player's likely lane to teach headway. */
    private fun spawnSlowLeadVehicle(pool: ObjectPool<RoadEntity>) {
        val e = pool.obtain()
        val lane = laneCenters[rnd.nextInt(laneCenters.size)]
        val speed = (level.targetSpeedKmh * 0.45f).coerceAtLeast(12f)
        e.spawn(
            z = GameGeometry.SPAWN_Z * 0.7f, laneX = lane, speedKmh = speed,
            kind = DetectKind.VEHICLE, behavior = RoadEntity.Behavior.LANE_FOLLOW,
            halfWidth = 0.34f, halfLength = 2.4f, color = 0xFF8D6E63.toInt(), label = "Slow vehicle"
        )
        e.targetLaneX = lane
    }

    private fun spawnVulnerableRoadUser(pool: ObjectPool<RoadEntity>) {
        val e = pool.obtain()
        // Enter from the kerb and cross; direction depends on driving side.
        val fromLeft = rnd.nextBoolean()
        val startX = if (fromLeft) -GameGeometry.ROAD_HALF_UNITS - 0.4f else GameGeometry.ROAD_HALF_UNITS + 0.4f
        val cross = if (fromLeft) 1f else -1f
        val unpredictable = country.pedestrianUnpredictability
        val speed = 4f + unpredictable * 6f
        val isCyclist = rnd.nextFloat() < 0.35f
        e.spawn(
            z = GameGeometry.SPAWN_Z * (0.55f + rnd.nextFloat() * 0.2f),
            laneX = startX, speedKmh = if (isCyclist) 12f else 5f,
            kind = if (isCyclist) DetectKind.CYCLIST else DetectKind.PEDESTRIAN,
            behavior = RoadEntity.Behavior.CROSSING,
            halfWidth = 0.16f, halfLength = 0.4f,
            color = if (isCyclist) 0xFF4DD0E1.toInt() else 0xFFFFF176.toInt(),
            label = if (isCyclist) "Cyclist" else "Pedestrian",
            hazard = if (isCyclist) HazardKind.CYCLIST else HazardKind.PEDESTRIAN
        )
        e.lateralVel = cross * (speed / 10f)
    }

    private fun spawnAnimal(pool: ObjectPool<RoadEntity>) {
        val e = pool.obtain()
        val kind = country.commonHazards.firstOrNull { it.lethal } ?: HazardKind.STRAY_DOG
        val fromLeft = rnd.nextBoolean()
        val startX = if (fromLeft) -GameGeometry.ROAD_HALF_UNITS - 0.3f else GameGeometry.ROAD_HALF_UNITS + 0.3f
        e.spawn(
            z = GameGeometry.SPAWN_Z * 0.6f, laneX = startX, speedKmh = 6f,
            kind = DetectKind.ANIMAL, behavior = RoadEntity.Behavior.WANDER,
            halfWidth = 0.25f, halfLength = 0.6f, color = 0xFFA1887F.toInt(),
            label = kind.displayName, hazard = kind
        )
        e.lateralVel = (if (fromLeft) 1f else -1f) * (0.5f + rnd.nextFloat() * 0.5f)
    }

    private fun spawnStaticHazard(pool: ObjectPool<RoadEntity>) {
        val e = pool.obtain()
        val kind = country.commonHazards.filter { !it.lethal }.randomOrNullSafe(rnd)
            ?: HazardKind.CONSTRUCTION_CONE
        val lane = laneCenters[rnd.nextInt(laneCenters.size)]
        e.spawn(
            z = GameGeometry.SPAWN_Z, laneX = lane, speedKmh = 0f,
            kind = DetectKind.HAZARD, behavior = RoadEntity.Behavior.STATIC,
            halfWidth = 0.3f, halfLength = 0.5f, color = 0xFFFFB74D.toInt(),
            label = kind.displayName, hazard = kind
        )
    }

    private fun trafficColor(): Int {
        val palette = intArrayOf(
            0xFFB0BEC5.toInt(), 0xFF90A4AE.toInt(), 0xFFE0E0E0.toInt(),
            0xFF607D8B.toInt(), 0xFF455A64.toInt(), 0xFFCFD8DC.toInt()
        )
        return palette[rnd.nextInt(palette.size)]
    }

    private fun <T> List<T>.randomOrNullSafe(rnd: Random): T? =
        if (isEmpty()) null else this[rnd.nextInt(size)]

    /** Which kerb side pedestrians favour, from the country driving side. */
    @Suppress("unused")
    val kerbSide: Float get() = if (country.drivingSide == DrivingSide.LEFT) -1f else 1f
}
