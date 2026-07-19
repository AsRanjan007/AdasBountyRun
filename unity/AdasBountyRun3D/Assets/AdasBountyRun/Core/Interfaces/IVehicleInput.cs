namespace ABR.Core
{
    /// <summary>
    /// Normalised driver input, decoupled from the input hardware (touch, tilt,
    /// gamepad). The vehicle controller depends only on this interface so input
    /// methods can be swapped or composited (build step 2 "input abstraction").
    /// </summary>
    public interface IVehicleInput
    {
        /// <summary>Throttle 0..1.</summary>
        float Throttle { get; }
        /// <summary>Brake 0..1.</summary>
        float Brake { get; }
        /// <summary>Steer -1 (full left) .. +1 (full right).</summary>
        float Steer { get; }
        /// <summary>Handbrake held.</summary>
        bool Handbrake { get; }
        /// <summary>One-shot up-shift request (manual box).</summary>
        bool ShiftUpPressed { get; }
        /// <summary>One-shot down-shift request (manual box).</summary>
        bool ShiftDownPressed { get; }

        /// <summary>Poll hardware; called once per frame before physics reads.</summary>
        void Sample(float deltaTime);
    }
}
