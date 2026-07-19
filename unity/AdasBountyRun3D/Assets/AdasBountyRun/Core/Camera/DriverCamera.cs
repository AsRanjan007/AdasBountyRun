using UnityEngine;

namespace ABR.Core
{
    /// <summary>
    /// Driver camera with two modes (build step 7 preview so the car can be
    /// validated from the seat):
    ///  - <b>Cockpit</b> (default): rigidly mounted to a driver-eye anchor inside
    ///    the car, giving the in-cabin / first-person experience.
    ///  - <b>Chase</b>: damped third-person follow with speed-scaled FOV.
    /// Toggle with <see cref="toggleKey"/> (default C). Cinemachine can replace this
    /// in the full visual pass; the vehicle core doesn't depend on it.
    /// </summary>
    [RequireComponent(typeof(Camera))]
    public sealed class DriverCamera : MonoBehaviour
    {
        public enum Mode { Cockpit, Chase }

        [SerializeField] private Mode mode = Mode.Cockpit;
        [SerializeField] private Transform target;         // vehicle root
        [SerializeField] private Rigidbody targetBody;
        [SerializeField] private Transform cockpitAnchor;  // driver-eye child of the car
        [SerializeField] private Vector3 chaseOffset = new Vector3(0f, 2.6f, -6.8f);
        [SerializeField] private float positionDamping = 10f;
        [SerializeField] private float rotationDamping = 9f;
        [SerializeField] private float cockpitFov = 68f;
        [SerializeField] private float chaseBaseFov = 62f;
        [SerializeField] private float chaseMaxFov = 82f;
        [SerializeField] private float fovSpeedKmh = 150f;
        [SerializeField] private KeyCode toggleKey = KeyCode.C;

        private Camera _cam;

        private void Awake() => _cam = GetComponent<Camera>();

        public void Setup(Transform t, Rigidbody body, Transform cockpit, Mode startMode = Mode.Cockpit)
        {
            target = t; targetBody = body; cockpitAnchor = cockpit; mode = startMode;
            Apply(true);
        }

        public Mode CurrentMode => mode;

        private void Update()
        {
            if (Input.GetKeyDown(toggleKey))
                mode = mode == Mode.Cockpit ? Mode.Chase : Mode.Cockpit;
        }

        private void LateUpdate() => Apply(false);

        private void Apply(bool instant)
        {
            if (target == null) return;

            if (mode == Mode.Cockpit && cockpitAnchor != null)
            {
                // Rigid seat mount — moves exactly with the car for an in-cabin feel.
                transform.SetPositionAndRotation(cockpitAnchor.position, cockpitAnchor.rotation);
                if (_cam != null) _cam.fieldOfView = cockpitFov;
                return;
            }

            Vector3 desiredPos = target.TransformPoint(chaseOffset);
            Vector3 lookAt = target.position + target.forward * 6f + Vector3.up * 1.2f;
            Quaternion desiredRot = Quaternion.LookRotation(lookAt - desiredPos, Vector3.up);

            if (instant)
            {
                transform.SetPositionAndRotation(desiredPos, desiredRot);
            }
            else
            {
                transform.position = Vector3.Lerp(transform.position, desiredPos,
                    1f - Mathf.Exp(-positionDamping * Time.deltaTime));
                transform.rotation = Quaternion.Slerp(transform.rotation, desiredRot,
                    1f - Mathf.Exp(-rotationDamping * Time.deltaTime));
            }

            if (_cam != null && targetBody != null)
            {
                float kmh = targetBody.velocity.magnitude * 3.6f;
                _cam.fieldOfView = Mathf.Lerp(chaseBaseFov, chaseMaxFov, Mathf.Clamp01(kmh / Mathf.Max(1f, fovSpeedKmh)));
            }
        }
    }
}
