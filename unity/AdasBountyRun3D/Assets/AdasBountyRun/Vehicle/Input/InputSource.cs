namespace ABR.Vehicle
{
    /// <summary>One frame of raw control values from a single input method.</summary>
    public struct InputSample
    {
        public float Throttle;   // 0..1
        public float Brake;      // 0..1
        public float Steer;      // -1..1
        public bool Handbrake;
        public bool ShiftUp;
        public bool ShiftDown;

        public static InputSample Empty => new InputSample();
        public float Magnitude => System.Math.Abs(Throttle) + System.Math.Abs(Brake) + System.Math.Abs(Steer);
    }

    /// <summary>
    /// A discrete input method (touch / tilt / gamepad / keyboard). Kept behind an
    /// interface so <see cref="CompositeVehicleInput"/> can mix or select them and
    /// new methods (wheel peripheral, remote) can be added without edits elsewhere.
    /// </summary>
    public interface IInputSource
    {
        bool Available { get; }
        InputSample Read(float deltaTime);
    }
}
