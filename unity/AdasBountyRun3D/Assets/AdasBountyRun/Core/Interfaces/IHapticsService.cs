namespace ABR.Core
{
    /// <summary>
    /// Android haptics abstraction (build step 2). Impacts (collisions, potholes)
    /// and continuous surface rumble. A no-op implementation is used in the editor
    /// and on non-Android platforms.
    /// </summary>
    public interface IHapticsService
    {
        /// <summary>One-shot impact; <paramref name="strength"/> 0..1.</summary>
        void Impact(float strength);

        /// <summary>Continuous surface feedback per frame; <paramref name="intensity"/> 0..1.</summary>
        void Surface(float intensity, float deltaTime);
    }
}
