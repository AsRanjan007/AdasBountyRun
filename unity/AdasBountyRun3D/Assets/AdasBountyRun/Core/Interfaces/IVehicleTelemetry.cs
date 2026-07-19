namespace ABR.Core
{
    /// <summary>
    /// Read-only vehicle state for HUD, audio (RPM-driven engine), ADAS and the
    /// feel-debug overlay. Exposed as an interface so the HUD/audio modules never
    /// reference the concrete controller.
    /// </summary>
    public interface IVehicleTelemetry
    {
        float SpeedKmh { get; }
        float EngineRpm { get; }
        int Gear { get; }              // -1 = reverse, 0 = neutral, 1..N forward
        float ThrottleValue { get; }
        /// <summary>Peak wheel slip 0..1 across the axle set (traction loss cue).</summary>
        float PeakSlip { get; }
        SurfaceType CurrentSurface { get; }
    }
}
