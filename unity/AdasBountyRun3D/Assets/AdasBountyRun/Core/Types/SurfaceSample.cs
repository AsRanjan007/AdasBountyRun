namespace ABR.Core
{
    /// <summary>
    /// The resolved condition under a single wheel this physics step. Produced by
    /// an <see cref="ISurfaceProvider"/> and consumed by the friction model.
    /// Pure data so it can be unit-tested without Unity.
    /// </summary>
    public struct SurfaceSample
    {
        public SurfaceType Type;
        /// <summary>Base grip multiplier for this surface (1 = dry asphalt).</summary>
        public float Grip;
        /// <summary>Rolling resistance factor [0..1], scales a retarding force.</summary>
        public float RollingResistance;
        /// <summary>Longitudinal tyre stiffness scalar.</summary>
        public float ForwardStiffness;
        /// <summary>Lateral tyre stiffness scalar.</summary>
        public float SidewaysStiffness;
        /// <summary>Surface roughness [0..1] for haptics + tyre-noise selection.</summary>
        public float Roughness;

        public static SurfaceSample DefaultAsphalt => new SurfaceSample
        {
            Type = SurfaceType.Asphalt,
            Grip = 1f,
            RollingResistance = 0.015f,
            ForwardStiffness = 1f,
            SidewaysStiffness = 1f,
            Roughness = 0.05f
        };
    }
}
