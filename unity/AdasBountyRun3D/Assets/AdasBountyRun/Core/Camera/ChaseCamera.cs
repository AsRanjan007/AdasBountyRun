using UnityEngine;

namespace ABR.Core
{
    /// <summary>
    /// Damped chase camera with speed-scaled FOV (a small slice of build step 6,
    /// included now so vehicle FEEL can be validated with a proper follow cam).
    /// Cinemachine will replace/augment this in the full visual pass.
    /// ASSUMPTION: attached to a Camera; target assigned by the scene builder.
    /// </summary>
    [RequireComponent(typeof(Camera))]
    public sealed class ChaseCamera : MonoBehaviour
    {
        [SerializeField] private Transform target;
        [SerializeField] private Rigidbody targetBody;
        [SerializeField] private Vector3 localOffset = new Vector3(0f, 2.4f, -6.5f);
        [SerializeField] private float positionDamping = 8f;
        [SerializeField] private float rotationDamping = 6f;
        [SerializeField] private float baseFov = 60f;
        [SerializeField] private float maxFov = 78f;
        [SerializeField] private float fovSpeedKmh = 140f; // speed at which maxFov is reached

        private Camera _cam;

        private void Awake() => _cam = GetComponent<Camera>();

        public void SetTarget(Transform t, Rigidbody body)
        {
            target = t;
            targetBody = body;
        }

        private void LateUpdate()
        {
            if (target == null) return;

            Vector3 desiredPos = target.TransformPoint(localOffset);
            transform.position = Vector3.Lerp(transform.position, desiredPos,
                1f - Mathf.Exp(-positionDamping * Time.deltaTime));

            Vector3 lookAt = target.position + target.forward * 6f + Vector3.up * 1.2f;
            Quaternion desiredRot = Quaternion.LookRotation(lookAt - transform.position, Vector3.up);
            transform.rotation = Quaternion.Slerp(transform.rotation, desiredRot,
                1f - Mathf.Exp(-rotationDamping * Time.deltaTime));

            if (_cam != null && targetBody != null)
            {
                float speedKmh = targetBody.velocity.magnitude * 3.6f;
                float t = Mathf.Clamp01(speedKmh / Mathf.Max(1f, fovSpeedKmh));
                _cam.fieldOfView = Mathf.Lerp(baseFov, maxFov, t);
            }
        }
    }
}
