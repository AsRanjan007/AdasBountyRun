using UnityEngine;

namespace ABR.Vehicle
{
    /// <summary>
    /// Gyro/accelerometer tilt steering. Throttle/brake still come from touch
    /// pedals (composited), so this source only reports steer. Calibrates a neutral
    /// tilt on first read.
    /// ASSUMPTION: device accelerometer available; legacy Input.acceleration.
    /// </summary>
    public sealed class TiltVehicleInput : IInputSource
    {
        private float _neutralX = float.NaN;
        [Tooltip("Tilt (g) that maps to full lock.")]
        public float fullLockTilt = 0.4f;

        public bool Available => SystemInfo.supportsAccelerometer;

        public InputSample Read(float deltaTime)
        {
            var s = new InputSample();
            float x = Input.acceleration.x;
            if (float.IsNaN(_neutralX)) _neutralX = x;
            float rel = x - _neutralX;
            s.Steer = Mathf.Clamp(rel / Mathf.Max(0.05f, fullLockTilt), -1f, 1f);
            return s;
        }

        public void Recalibrate() => _neutralX = Input.acceleration.x;
    }
}
