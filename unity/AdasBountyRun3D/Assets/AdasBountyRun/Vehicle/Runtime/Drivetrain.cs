using UnityEngine;

namespace ABR.Vehicle
{
    /// <summary>
    /// Engine + gearbox model. Computes engine RPM from driven-wheel speed and the
    /// current gear, looks up torque from the profile's curve, and outputs the
    /// per-wheel drive torque. Handles automatic or manual shifting. Stateless
    /// except for the current gear so it stays easy to reason about.
    /// </summary>
    public sealed class Drivetrain
    {
        private readonly VehicleProfile _v;

        public int Gear { get; private set; } = 1;   // -1 reverse, 0 neutral, 1..N
        public float EngineRpm { get; private set; }

        public Drivetrain(VehicleProfile v) { _v = v; }

        public float CurrentRatio()
        {
            if (Gear == 0) return 0f;
            if (Gear < 0) return -_v.reverseRatio;
            int idx = Mathf.Clamp(Gear - 1, 0, _v.gearRatios.Length - 1);
            return _v.gearRatios[idx];
        }

        public void ShiftUp()
        {
            if (Gear < _v.gearRatios.Length) Gear++;
        }

        public void ShiftDown()
        {
            if (Gear > -1) Gear--;
        }

        /// <summary>
        /// Update RPM from the average driven-wheel RPM and, if automatic, shift.
        /// </summary>
        public void UpdateEngine(float avgDrivenWheelRpm, float throttle)
        {
            float ratio = Mathf.Abs(CurrentRatio()) * _v.finalDrive;
            float fromWheels = Mathf.Abs(avgDrivenWheelRpm) * ratio;
            // Blend toward the wheel-derived RPM; idle when in neutral/clutch.
            float target = Gear == 0 ? _v.idleRpm : Mathf.Max(_v.idleRpm, fromWheels);
            EngineRpm = Mathf.Lerp(EngineRpm, target, 0.25f);
            EngineRpm = Mathf.Clamp(EngineRpm, _v.idleRpm, _v.maxRpm);

            if (_v.automaticGearbox) AutoShift(throttle);
        }

        private void AutoShift(float throttle)
        {
            if (Gear >= 1)
            {
                if (EngineRpm > _v.shiftUpRpm && Gear < _v.gearRatios.Length) Gear++;
                else if (EngineRpm < _v.shiftDownRpm && Gear > 1) Gear--;
            }
        }

        /// <summary>
        /// Drive torque (Nm) per driven wheel for the given throttle, split across
        /// <paramref name="drivenWheelCount"/>.
        /// </summary>
        public float DriveTorquePerWheel(float throttle, int drivenWheelCount)
        {
            if (Gear == 0 || drivenWheelCount <= 0) return 0f;
            float rpmNorm = Mathf.InverseLerp(_v.idleRpm, _v.maxRpm, EngineRpm);
            float curve = Mathf.Clamp01(_v.torqueCurve.Evaluate(rpmNorm));
            float ratio = CurrentRatio() * _v.finalDrive * _v.drivetrainEfficiency;
            float engineTorque = _v.maxEngineTorque * curve * throttle;
            return engineTorque * ratio / drivenWheelCount;
        }

        public void SetGear(int g) => Gear = Mathf.Clamp(g, -1, _v.gearRatios.Length);
    }
}
