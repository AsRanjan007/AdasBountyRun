using UnityEngine;
using ABR.Core;

namespace ABR.Vehicle
{
    /// <summary>
    /// Default <see cref="ISurfaceProvider"/>: reads a <see cref="SurfaceMarker"/>
    /// off the collider under the wheel and resolves it through the shared
    /// <see cref="SurfaceDatabase"/>. Falls back to asphalt when unmarked.
    ///
    /// Swap points (build step 3): a TextureSurfaceProvider (splatmap) or a
    /// CesiumSurfaceProvider (streamed geo) can replace this without touching the
    /// controller — they implement the same interface.
    /// </summary>
    public sealed class MarkerSurfaceProvider : ISurfaceProvider
    {
        private readonly SurfaceDatabase _db;

        public MarkerSurfaceProvider(SurfaceDatabase db)
        {
            _db = db;
        }

        public SurfaceSample Sample(Vector3 contactPoint, Collider hitCollider)
        {
            if (hitCollider == null || _db == null)
                return SurfaceSample.DefaultAsphalt;

            // Marker may be on the collider or a parent (composite meshes).
            var marker = hitCollider.GetComponent<SurfaceMarker>();
            if (marker == null) marker = hitCollider.GetComponentInParent<SurfaceMarker>();
            var type = marker != null ? marker.surface : SurfaceType.Asphalt;
            return _db.Sample(type);
        }
    }
}
