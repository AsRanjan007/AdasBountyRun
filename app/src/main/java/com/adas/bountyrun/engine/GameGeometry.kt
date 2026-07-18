package com.adas.bountyrun.engine

/**
 * Shared world constants and the pseudo-3D projection maths (spec §10/§24).
 *
 * Coordinate model:
 *  - Z is metres ahead of the player camera (player sits at relative Z = 0).
 *  - laneX is a lateral position in "lane units"; lane width = 1.0. A 3-lane
 *    road has lane centres at -1, 0, +1 and drivable range [-1.5, 1.5].
 */
object GameGeometry {
    const val LANE_COUNT = 3
    const val LANE_WIDTH_UNITS = 1f
    const val ROAD_HALF_UNITS = LANE_COUNT / 2f       // 1.5 for 3 lanes
    const val FOCAL = 42f                              // perspective focal length (m)
    const val SPAWN_Z = 150f                           // where objects appear ahead
    const val DESPAWN_Z = -30f                         // behind the player -> recycle
    const val KMH_TO_MS = 1f / 3.6f

    /** Perspective scale for an object [relZ] metres ahead (1 near, ->0 far). */
    fun scaleAt(relZ: Float): Float {
        val z = relZ.coerceAtLeast(-FOCAL + 1f)
        return FOCAL / (FOCAL + z)
    }

    /** Lane centre in lane units for a 0-based [laneIndex] on a 3-lane road. */
    fun laneCenter(laneIndex: Int): Float = laneIndex - (LANE_COUNT - 1) / 2f
}
