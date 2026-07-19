using UnityEngine;
using ABR.Core;

namespace ABR.Vehicle
{
    /// <summary>
    /// THE core coupling (build steps 2 & 4): converts a per-wheel
    /// <see cref="SurfaceSample"/> + global wetness into concrete
    /// <see cref="WheelFrictionCurve"/> values for a WheelCollider.
    ///
    /// Pure/stateless so its behaviour is deterministic and testable. Wetness
    /// lowers grip, scaled by each surface's <c>wetnessSensitivity</c> — one
    /// parameter, coupled everywhere.
    /// </summary>
    public static class SurfaceFrictionModel
    {
        /// <summary>
        /// Effective grip after weather, in [0.15..~1.3].
        /// </summary>
        public static float EffectiveGrip(SurfaceSample s, float wetness, float wetnessSensitivity)
        {
            float wetLoss = Mathf.Clamp01(wetness) * wetnessSensitivity;
            return Mathf.Max(0.15f, s.Grip * (1f - wetLoss));
        }

        /// <summary>Build the longitudinal friction curve for a wheel.</summary>
        public static WheelFrictionCurve Forward(
            WheelFrictionCurve baseCurve, VehicleProfile v, SurfaceSample s,
            float wetness, float wetnessSensitivity)
        {
            float grip = EffectiveGrip(s, wetness, wetnessSensitivity);
            baseCurve.extremumSlip = 0.4f;
            baseCurve.extremumValue = grip;
            baseCurve.asymptoteSlip = 0.8f;
            baseCurve.asymptoteValue = grip * 0.75f;
            baseCurve.stiffness = v.baseForwardStiffness * s.ForwardStiffness;
            return baseCurve;
        }

        /// <summary>Build the lateral friction curve for a wheel.</summary>
        public static WheelFrictionCurve Sideways(
            WheelFrictionCurve baseCurve, VehicleProfile v, SurfaceSample s,
            float wetness, float wetnessSensitivity)
        {
            float grip = EffectiveGrip(s, wetness, wetnessSensitivity);
            baseCurve.extremumSlip = 0.25f;
            baseCurve.extremumValue = grip;
            baseCurve.asymptoteSlip = 0.5f;
            baseCurve.asymptoteValue = grip * 0.8f;
            baseCurve.stiffness = v.baseSidewaysStiffness * s.SidewaysStiffness;
            return baseCurve;
        }

        /// <summary>
        /// Retarding force magnitude (N) from rolling resistance for one wheel
        /// carrying <paramref name="normalLoad"/> newtons.
        /// </summary>
        public static float RollingResistanceForce(SurfaceSample s, float normalLoad)
            => s.RollingResistance * Mathf.Max(0f, normalLoad);
    }
}
