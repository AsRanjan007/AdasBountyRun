using UnityEngine;
using ABR.Core;

namespace ABR.Vehicle
{
    /// <summary>
    /// Per-surface handling + feedback parameters. This is the data half of the
    /// "road condition couples to handling" requirement: the same asset is meant
    /// to feed the material blend (step 3 visuals) and the friction model (here).
    /// </summary>
    [CreateAssetMenu(menuName = "ABR/Surface Profile", fileName = "SurfaceProfile")]
    public sealed class SurfaceProfile : ScriptableObject
    {
        public SurfaceType type = SurfaceType.Asphalt;

        [Header("Handling")]
        [Range(0.2f, 1.2f)] public float grip = 1f;
        [Range(0f, 0.2f)] public float rollingResistance = 0.015f;
        [Range(0.3f, 1.5f)] public float forwardStiffness = 1f;
        [Range(0.3f, 1.5f)] public float sidewaysStiffness = 1f;

        [Header("Feedback")]
        [Range(0f, 1f)] public float roughness = 0.05f;
        [Tooltip("Extra grip lost per unit wetness on THIS surface.")]
        [Range(0f, 0.8f)] public float wetnessSensitivity = 0.35f;

        public SurfaceSample ToSample() => new SurfaceSample
        {
            Type = type,
            Grip = grip,
            RollingResistance = rollingResistance,
            ForwardStiffness = forwardStiffness,
            SidewaysStiffness = sidewaysStiffness,
            Roughness = roughness
        };
    }
}
