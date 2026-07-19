using UnityEngine;

namespace ABR.Vehicle
{
    /// <summary>
    /// All tunable vehicle dynamics live here (build-order rule: no hardcoded
    /// magic numbers). One asset per car; the rig builder and controller read it.
    /// ASSUMPTION: SI-ish units — mass kg, torque Nm, angles degrees, distances m.
    /// </summary>
    [CreateAssetMenu(menuName = "ABR/Vehicle Profile", fileName = "VehicleProfile")]
    public sealed class VehicleProfile : ScriptableObject
    {
        [Header("Chassis")]
        public float mass = 1350f;
        [Tooltip("Centre-of-mass offset from the body origin (lower = more stable).")]
        public Vector3 centerOfMassOffset = new Vector3(0f, -0.35f, 0.1f);
        public float wheelbase = 2.6f;
        public float trackWidth = 1.55f;

        [Header("Wheels & Suspension")]
        public float wheelRadius = 0.34f;
        public float wheelMass = 20f;
        public float suspensionDistance = 0.18f;
        public float springStrength = 32000f;
        public float damperStrength = 4500f;
        [Range(0f, 1f)] public float suspensionTarget = 0.5f;
        [Tooltip("Force application point below wheel centre; lower reduces roll.")]
        public float forceAppPointDistance = 0.05f;

        [Header("Engine")]
        public float maxEngineTorque = 380f;
        public float idleRpm = 900f;
        public float maxRpm = 6800f;
        [Tooltip("Normalised torque vs normalised RPM (x:0..1 rpm, y:0..1 torque).")]
        public AnimationCurve torqueCurve = new AnimationCurve(
            new Keyframe(0.0f, 0.55f), new Keyframe(0.35f, 0.9f),
            new Keyframe(0.6f, 1.0f), new Keyframe(0.85f, 0.9f), new Keyframe(1.0f, 0.7f));

        [Header("Drivetrain")]
        public DriveType driveType = DriveType.RearWheel;
        public float[] gearRatios = { 3.4f, 2.2f, 1.55f, 1.15f, 0.92f, 0.78f };
        public float reverseRatio = 3.6f;
        public float finalDrive = 3.9f;
        public float shiftUpRpm = 6200f;
        public float shiftDownRpm = 2600f;
        public bool automaticGearbox = true;
        [Range(0.85f, 1f)] public float drivetrainEfficiency = 0.9f;

        [Header("Steering (Ackermann)")]
        public float maxSteerAngle = 32f;
        [Tooltip("Steer angle reduction at speed (0 = none, 1 = strong).")]
        [Range(0f, 1f)] public float speedSteerReduction = 0.55f;
        public float steerReturnSpeed = 4f;
        public float steerFillSpeed = 3.2f;

        [Header("Brakes")]
        public float maxBrakeTorque = 3000f;
        public float handbrakeTorque = 4200f;

        [Header("Aero")]
        [Tooltip("Downforce = coeff * speed^2, pressed on the COM.")]
        public float downforceCoefficient = 3.2f;
        public float dragCoefficient = 0.35f;

        [Header("Tyre base friction (before surface/weather coupling)")]
        public float baseForwardStiffness = 1.6f;
        public float baseSidewaysStiffness = 1.9f;

        public float MaxForwardRatio => (gearRatios.Length > 0 ? gearRatios[0] : 3f) * finalDrive;
    }

    public enum DriveType { RearWheel, FrontWheel, AllWheel }
}
