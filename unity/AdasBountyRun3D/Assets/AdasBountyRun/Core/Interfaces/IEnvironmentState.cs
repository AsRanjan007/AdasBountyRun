namespace ABR.Core
{
    /// <summary>
    /// The single environment coupling parameter (build step 4). One float drives
    /// visuals (wet reflectivity, rain) AND handling (friction). Kept as an
    /// interface so weather can later be driven by a dynamic weather system.
    /// </summary>
    public interface IEnvironmentState
    {
        /// <summary>Road wetness 0 (dry) .. 1 (flooded). Modulates grip everywhere.</summary>
        float Wetness { get; }
    }
}
