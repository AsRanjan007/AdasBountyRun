using UnityEngine;
using ABR.Core;

namespace ABR.Vehicle
{
    /// <summary>
    /// The vehicle FEEL gate (build step 2). Rigidbody + 4 WheelColliders driven
    /// by a data-only <see cref="VehicleProfile"/>, with surface-coupled friction,
    /// Ackermann steering, weight transfer (via COM + suspension), an engine/gearbox
    /// and aero. Everything hardware-specific (input, haptics, surface source) is
    /// injected through interfaces so implementations can be swapped.
    /// </summary>
    [RequireComponent(typeof(Rigidbody))]
    public sealed class VehicleController : MonoBehaviour, IVehicleTelemetry
    {
        [Header("Config (ScriptableObjects — no magic numbers)")]
        public VehicleProfile profile;
        public SurfaceDatabase surfaceDatabase;

        [Header("Wheels (FL, FR, RL, RR) — assigned by the rig builder")]
        public WheelUnit[] wheels = new WheelUnit[4];

        [Header("Physics substeps")]
        public float substepSpeedThreshold = 5f;
        public int substepsBelow = 12;
        public int substepsAbove = 15;

        private Rigidbody _rb;
        private Drivetrain _drivetrain;
        private ISurfaceProvider _surface;
        private IEnvironmentState _env;
        private IHapticsService _haptics;
        private IVehicleInput _input;

        private float _currentSteer;
        private float _appliedGrip = 1f;

        // ---- IVehicleTelemetry ----
        public float SpeedKmh => _rb != null ? _rb.velocity.magnitude * 3.6f : 0f;
        public float EngineRpm => _drivetrain != null ? _drivetrain.EngineRpm : 0f;
        public int Gear => _drivetrain != null ? _drivetrain.Gear : 0;
        public float ThrottleValue { get; private set; }
        public float PeakSlip { get; private set; }
        public SurfaceType CurrentSurface { get; private set; } = SurfaceType.Asphalt;
        public float AppliedGrip => _appliedGrip;

        /// <summary>Current steering as -1..1 (for the cockpit steering-wheel visual).</summary>
        public float SteerNormalized =>
            (profile != null && profile.maxSteerAngle > 0.01f)
                ? Mathf.Clamp(_currentSteer / profile.maxSteerAngle, -1f, 1f) : 0f;

        private void Awake()
        {
            _rb = GetComponent<Rigidbody>();
            if (profile != null)
            {
                _rb.mass = profile.mass;
                _rb.centerOfMass = profile.centerOfMassOffset;
                _drivetrain = new Drivetrain(profile);
            }

            // Injected services with safe fallbacks.
            _input = GetComponent<IVehicleInput>();
            _env = FindEnvironment();
            _surface = new MarkerSurfaceProvider(surfaceDatabase);
            _haptics = HapticsFactory.Create();

            foreach (var wheel in wheels)
            {
                wheel?.Init();
                if (wheel?.collider != null)
                    wheel.collider.ConfigureVehicleSubsteps(substepSpeedThreshold, substepsBelow, substepsAbove);
            }
        }

        private void FixedUpdate()
        {
            if (profile == null || _rb == null) return;

            _input?.Sample(Time.fixedDeltaTime);
            float throttle = _input?.Throttle ?? 0f;
            float brake = _input?.Brake ?? 0f;
            float steerInput = _input?.Steer ?? 0f;
            bool handbrake = _input?.Handbrake ?? false;
            ThrottleValue = throttle;

            HandleManualShift();
            ApplySteering(steerInput);
            UpdateSurfaceFriction();
            ApplyDrivetrain(throttle);
            ApplyBrakes(brake, handbrake);
            ApplyRollingResistanceAndAero();
            UpdateTelemetryAndHaptics();
        }

        private void Update()
        {
            foreach (var wheel in wheels) wheel?.UpdateVisual();
        }

        // ---- Steering with Ackermann + speed-based reduction ----
        private void ApplySteering(float steerInput)
        {
            float speedFactor = 1f - profile.speedSteerReduction * Mathf.Clamp01(SpeedKmh / 140f);
            float targetSteer = steerInput * profile.maxSteerAngle * speedFactor;
            float fill = steerInput == 0f ? profile.steerReturnSpeed : profile.steerFillSpeed;
            _currentSteer = Mathf.MoveTowards(_currentSteer, targetSteer, fill * profile.maxSteerAngle * Time.fixedDeltaTime);

            // Ackermann: inner wheel turns more than the outer.
            float left, right;
            AckermannAngles(_currentSteer, out left, out right);
            if (wheels.Length >= 2)
            {
                if (wheels[0] != null) wheels[0].SetSteer(left);
                if (wheels[1] != null) wheels[1].SetSteer(right);
            }
        }

        private void AckermannAngles(float steerDeg, out float left, out float right)
        {
            if (Mathf.Abs(steerDeg) < 0.01f) { left = right = 0f; return; }
            float wb = Mathf.Max(0.5f, profile.wheelbase);
            float half = profile.trackWidth * 0.5f;
            float radius = wb / Mathf.Tan(steerDeg * Mathf.Deg2Rad);
            float inner = Mathf.Atan(wb / (Mathf.Abs(radius) - half)) * Mathf.Rad2Deg;
            float outer = Mathf.Atan(wb / (Mathf.Abs(radius) + half)) * Mathf.Rad2Deg;
            if (steerDeg > 0f) { right = inner; left = outer; }   // turning right: right is inner
            else { left = -inner; right = -outer; }
        }

        private void UpdateSurfaceFriction()
        {
            float grip = 1f; float peakSlip = 0f; SurfaceType surf = SurfaceType.Asphalt;
            foreach (var wheel in wheels)
            {
                if (wheel == null) continue;
                grip = wheel.UpdateFriction(_surface, _env, profile);
                if (wheel.CombinedSlip > peakSlip) peakSlip = wheel.CombinedSlip;
                if (wheel.Grounded) surf = wheel.Surface.Type;
            }
            _appliedGrip = grip;
            PeakSlip = peakSlip;
            CurrentSurface = surf;
        }

        private void ApplyDrivetrain(float throttle)
        {
            int drivenCount = 0; float avgRpm = 0f;
            foreach (var wheel in wheels)
                if (wheel != null && wheel.driven && wheel.collider != null)
                {
                    drivenCount++; avgRpm += Mathf.Abs(wheel.collider.rpm);
                }
            if (drivenCount > 0) avgRpm /= drivenCount;

            _drivetrain.UpdateEngine(avgRpm, throttle);
            float perWheel = _drivetrain.DriveTorquePerWheel(throttle, drivenCount);
            foreach (var wheel in wheels)
                if (wheel != null && wheel.driven) wheel.ApplyMotor(perWheel);
        }

        private void ApplyBrakes(float brake, bool handbrake)
        {
            float service = brake * profile.maxBrakeTorque;
            foreach (var wheel in wheels)
            {
                if (wheel == null) continue;
                float t = service;
                if (handbrake && wheel.handbrakeAffected) t = Mathf.Max(t, profile.handbrakeTorque);
                wheel.ApplyBrake(t);
            }
        }

        private void ApplyRollingResistanceAndAero()
        {
            // Rolling resistance opposes motion, scaled by surface + load.
            foreach (var wheel in wheels)
            {
                if (wheel == null || !wheel.Grounded) continue;
                float rr = SurfaceFrictionModel.RollingResistanceForce(wheel.Surface, wheel.NormalLoad);
                Vector3 dir = -_rb.velocity.normalized;
                if (_rb.velocity.sqrMagnitude > 0.1f)
                    _rb.AddForceAtPosition(dir * rr, wheel.ContactPoint, ForceMode.Force);
            }
            // Downforce ∝ v² presses the car down; aero drag opposes motion.
            float v2 = _rb.velocity.sqrMagnitude;
            _rb.AddForce(-transform.up * (profile.downforceCoefficient * v2 * 0.5f), ForceMode.Force);
            _rb.AddForce(-_rb.velocity.normalized * (profile.dragCoefficient * v2 * 0.5f), ForceMode.Force);
        }

        private void HandleManualShift()
        {
            if (profile.automaticGearbox) return;
            if (_input != null && _input.ShiftUpPressed) _drivetrain.ShiftUp();
            if (_input != null && _input.ShiftDownPressed) _drivetrain.ShiftDown();
        }

        private void UpdateTelemetryAndHaptics()
        {
            if (_haptics == null) return;
            // Surface roughness + slip drive continuous rumble; strong slip = impact.
            float roughness = 0f;
            foreach (var wheel in wheels)
                if (wheel != null && wheel.Grounded) roughness = Mathf.Max(roughness, wheel.Surface.Roughness);
            float intensity = Mathf.Clamp01(roughness * 0.6f + PeakSlip * 0.4f);
            _haptics.Surface(intensity, Time.fixedDeltaTime);
        }

        private IEnvironmentState FindEnvironment()
        {
            var comp = Object.FindObjectOfType<EnvironmentState>();
            return comp; // may be null -> friction model treats wetness as 0
        }

        private void OnCollisionEnter(Collision c)
        {
            float strength = Mathf.Clamp01(c.relativeVelocity.magnitude / 15f);
            _haptics?.Impact(strength);
        }
    }
}
