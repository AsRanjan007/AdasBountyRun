using System.Collections.Generic;
using UnityEngine;
using ABR.Core;

namespace ABR.Vehicle
{
    /// <summary>
    /// Lookup from <see cref="SurfaceType"/> to its <see cref="SurfaceProfile"/>.
    /// One asset shared by the whole game so surface tuning is centralised. The
    /// scaffold editor script populates it with sensible defaults.
    /// </summary>
    [CreateAssetMenu(menuName = "ABR/Surface Database", fileName = "SurfaceDatabase")]
    public sealed class SurfaceDatabase : ScriptableObject
    {
        public List<SurfaceProfile> profiles = new List<SurfaceProfile>();

        private Dictionary<SurfaceType, SurfaceProfile> _map;

        public SurfaceProfile Get(SurfaceType type)
        {
            if (_map == null || _map.Count != profiles.Count) Rebuild();
            return _map.TryGetValue(type, out var p) ? p : null;
        }

        public SurfaceSample Sample(SurfaceType type)
        {
            var p = Get(type);
            return p != null ? p.ToSample() : SurfaceSample.DefaultAsphalt;
        }

        private void Rebuild()
        {
            _map = new Dictionary<SurfaceType, SurfaceProfile>();
            foreach (var p in profiles)
                if (p != null && !_map.ContainsKey(p.type)) _map.Add(p.type, p);
        }

        private void OnEnable() => _map = null;
    }
}
