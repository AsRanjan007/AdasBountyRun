using UnityEngine;

namespace ABR.Core
{
    /// <summary>
    /// Minimal environment state holder (build step 4 is fuller). Exposes the
    /// single <see cref="Wetness"/> coupling parameter. A dynamic weather system
    /// will later drive this same value; consumers only see the interface.
    /// </summary>
    public sealed class EnvironmentState : MonoBehaviour, IEnvironmentState
    {
        [SerializeField, Range(0f, 1f)]
        [Tooltip("0 = dry, 1 = flooded. Drives friction now; visuals in step 4.")]
        private float wetness = 0f;

        public float Wetness => wetness;

        /// <summary>Runtime setter for the future weather system / debug HUD.</summary>
        public void SetWetness(float value) => wetness = Mathf.Clamp01(value);
    }
}
