using UnityEngine;

namespace ABR.Vehicle
{
    /// <summary>
    /// Spins the cockpit steering-wheel mesh with the vehicle's steering input, so
    /// the in-cabin view reads as a real car. Reads <see cref="VehicleController.SteerNormalized"/>.
    /// </summary>
    public sealed class SteeringWheelVisual : MonoBehaviour
    {
        [SerializeField] private VehicleController vehicle;
        [SerializeField] private Transform rim;      // the disc to rotate
        [SerializeField] private float maxWheelDegrees = 210f;
        [SerializeField] private float smoothing = 12f;

        private Quaternion _base;
        private float _shown;

        private void Start()
        {
            if (rim == null) rim = transform;
            _base = rim.localRotation;
        }

        private void Update()
        {
            if (vehicle == null || rim == null) return;
            _shown = Mathf.Lerp(_shown, vehicle.SteerNormalized, 1f - Mathf.Exp(-smoothing * Time.deltaTime));
            rim.localRotation = _base * Quaternion.AngleAxis(-_shown * maxWheelDegrees, Vector3.up);
        }
    }
}
