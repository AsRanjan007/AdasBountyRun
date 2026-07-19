using UnityEngine;
using ABR.Core;

namespace ABR.Vehicle
{
    /// <summary>
    /// Tags a collider with its road surface. Placed by the test-scene builder on
    /// asphalt/wet/gravel/pothole patches. In the full road system the spline mesh
    /// writes these per-segment; the provider reads them the same way, so nothing
    /// downstream changes when the road source changes (interface-decoupled).
    /// </summary>
    public sealed class SurfaceMarker : MonoBehaviour
    {
        public SurfaceType surface = SurfaceType.Asphalt;
    }
}
