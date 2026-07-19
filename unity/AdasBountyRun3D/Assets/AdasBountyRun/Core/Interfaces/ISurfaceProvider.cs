using UnityEngine;

namespace ABR.Core
{
    /// <summary>
    /// Resolves the road condition under a wheel. Implementations may read a
    /// component marker (test scene), a splat/texture map (procedural road), or a
    /// streamed real-geography source (Cesium) — all behind this one seam so the
    /// friction model never knows where the condition came from (build step 3).
    /// </summary>
    public interface ISurfaceProvider
    {
        /// <summary>
        /// Sample the surface at a wheel contact. <paramref name="hitCollider"/> is
        /// the collider the wheel is resting on (may be null when airborne).
        /// </summary>
        SurfaceSample Sample(Vector3 contactPoint, Collider hitCollider);
    }
}
