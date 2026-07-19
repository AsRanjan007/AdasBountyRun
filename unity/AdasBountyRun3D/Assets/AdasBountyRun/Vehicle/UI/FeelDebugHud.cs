using UnityEngine;
using ABR.Core;

namespace ABR.Vehicle
{
    /// <summary>
    /// Minimal IMGUI overlay for validating vehicle FEEL (build step 2 gate):
    /// speed, gear, RPM, current surface, applied grip and peak slip. No UI Canvas
    /// wiring required — attach to the vehicle. Replaced by the real HUD in step 7.
    /// </summary>
    public sealed class FeelDebugHud : MonoBehaviour
    {
        [SerializeField] private VehicleController vehicle;
        [SerializeField] private EnvironmentState environment;

        private GUIStyle _style;

        private void Reset() => vehicle = GetComponent<VehicleController>();

        private void OnGUI()
        {
            if (vehicle == null) return;
            if (_style == null)
                _style = new GUIStyle(GUI.skin.label) { fontSize = 22, normal = { textColor = Color.white } };

            IVehicleTelemetry t = vehicle;
            var rect = new Rect(20, 20, 520, 260);
            GUI.color = new Color(0f, 0f, 0f, 0.55f);
            GUI.DrawTexture(rect, Texture2D.whiteTexture);
            GUI.color = Color.white;

            float y = 30f;
            void Line(string s) { GUI.Label(new Rect(34, y, 500, 30), s, _style); y += 30f; }

            Line($"SPEED   {t.SpeedKmh,6:0} km/h");
            Line($"GEAR    {GearLabel(t.Gear)}    RPM {t.EngineRpm,5:0}");
            Line($"THROTTLE {t.ThrottleValue:0.00}");
            Line($"SURFACE {t.CurrentSurface}");
            Line($"GRIP    {vehicle.AppliedGrip:0.00}   SLIP {t.PeakSlip:0.00}");
            if (environment != null)
                Line($"WETNESS {environment.Wetness:0.00}   (Z/X to change)");
            Line("Drive: W/S throttle-brake · A/D steer · Space handbrake");
            Line("Camera: C toggles cockpit / chase");
        }

        private void Update()
        {
            if (environment == null) return;
            if (Input.GetKey(KeyCode.Z)) environment.SetWetness(environment.Wetness + Time.deltaTime * 0.4f);
            if (Input.GetKey(KeyCode.X)) environment.SetWetness(environment.Wetness - Time.deltaTime * 0.4f);
        }

        private static string GearLabel(int g) => g < 0 ? "R" : g == 0 ? "N" : g.ToString();
    }
}
