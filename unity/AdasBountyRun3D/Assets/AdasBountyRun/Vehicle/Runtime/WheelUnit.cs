using UnityEngine;
using ABR.Core;

namespace ABR.Vehicle
{
    /// <summary>
    /// One driven/steered wheel: owns a <see cref="WheelCollider"/> and its visual
    /// mesh, applies torque/brake/steer, and re-derives its friction each physics
    /// step from the surface under it. Reports slip + surface for telemetry/haptics.
    /// </summary>
    [System.Serializable]
    public sealed class WheelUnit
    {
        public WheelCollider collider;
        public Transform visual;
        public bool steerable;
        public bool driven;
        public bool handbrakeAffected;

        private WheelFrictionCurve _fwd;
        private WheelFrictionCurve _side;

        public bool Grounded { get; private set; }
        public SurfaceSample Surface { get; private set; }
        public float CombinedSlip { get; private set; }
        public Vector3 ContactPoint { get; private set; }
        public float NormalLoad { get; private set; }

        public void Init()
        {
            if (collider == null) return;
            _fwd = collider.forwardFriction;
            _side = collider.sidewaysFriction;
            Surface = SurfaceSample.DefaultAsphalt;
        }

        public void ApplyMotor(float torque) { if (collider != null) collider.motorTorque = torque; }
        public void ApplyBrake(float torque) { if (collider != null) collider.brakeTorque = torque; }
        public void SetSteer(float angleDeg) { if (steerable && collider != null) collider.steerAngle = angleDeg; }

        /// <summary>
        /// Sample the ground, resolve the surface, and push new friction curves.
        /// Returns the effective grip applied (for debug/telemetry).
        /// </summary>
        public float UpdateFriction(ISurfaceProvider provider, IEnvironmentState env, VehicleProfile v)
        {
            if (collider == null) return 1f;

            Grounded = collider.GetGroundHit(out WheelHit hit);
            if (Grounded)
            {
                ContactPoint = hit.point;
                NormalLoad = hit.force;
                CombinedSlip = Mathf.Clamp01(Mathf.Sqrt(hit.forwardSlip * hit.forwardSlip +
                                                        hit.sidewaysSlip * hit.sidewaysSlip));
                Surface = provider != null ? provider.Sample(hit.point, hit.collider)
                                           : SurfaceSample.DefaultAsphalt;
            }
            else
            {
                CombinedSlip = 0f;
                NormalLoad = 0f;
                Surface = SurfaceSample.DefaultAsphalt;
            }

            float wetness = env != null ? env.Wetness : 0f;
            // wetnessSensitivity travels on the surface profile; approximate here
            // via the sample's roughness-independent default when unavailable.
            float wetSens = SurfaceWetnessSensitivity(Surface.Type);

            _fwd = SurfaceFrictionModel.Forward(_fwd, v, Surface, wetness, wetSens);
            _side = SurfaceFrictionModel.Sideways(_side, v, Surface, wetness, wetSens);
            collider.forwardFriction = _fwd;
            collider.sidewaysFriction = _side;

            return SurfaceFrictionModel.EffectiveGrip(Surface, wetness, wetSens);
        }

        /// <summary>Match the visual mesh to the simulated wheel pose.</summary>
        public void UpdateVisual()
        {
            if (collider == null || visual == null) return;
            collider.GetWorldPose(out Vector3 pos, out Quaternion rot);
            visual.SetPositionAndRotation(pos, rot);
        }

        // ASSUMPTION: sensitivity mirrors SurfaceProfile defaults; the controller
        // can inject the real per-profile value from the database if desired.
        private static float SurfaceWetnessSensitivity(SurfaceType t) => t switch
        {
            SurfaceType.Asphalt => 0.35f,
            SurfaceType.WetAsphalt => 0.15f,
            SurfaceType.Gravel => 0.25f,
            SurfaceType.Dirt => 0.5f,
            SurfaceType.Pothole => 0.3f,
            SurfaceType.Sand => 0.2f,
            SurfaceType.Ice => 0.1f,
            _ => 0.3f
        };
    }
}
