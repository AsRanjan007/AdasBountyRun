namespace ABR.Core
{
    /// <summary>
    /// Road surface classes. Each maps (via SurfaceDatabase) to a SurfaceProfile
    /// that drives BOTH the material blend and the wheel-friction parameter — the
    /// core "road condition couples to handling" requirement (build step 3).
    /// </summary>
    public enum SurfaceType
    {
        Asphalt = 0,   // dry, high grip
        WetAsphalt = 1,
        Gravel = 2,
        Dirt = 3,
        Pothole = 4,   // carries collision dip + grip loss + haptic
        SpeedBreaker = 5,
        Sand = 6,
        Ice = 7
    }
}
