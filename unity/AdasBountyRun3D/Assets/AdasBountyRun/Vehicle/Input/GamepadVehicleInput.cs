using UnityEngine;

namespace ABR.Vehicle
{
    /// <summary>
    /// Gamepad source via legacy axes. Left stick / triggers.
    /// ASSUMPTION: default Input Manager axes ("Horizontal", plus triggers mapped
    /// to axis 9/10 on many pads). Trigger mapping varies by OS; a proper binding
    /// set is added with the Input System package in a later pass.
    /// </summary>
    public sealed class GamepadVehicleInput : IInputSource
    {
        public bool Available => Input.GetJoystickNames().Length > 0;

        public InputSample Read(float deltaTime)
        {
            var s = new InputSample();
            s.Steer = Mathf.Clamp(Input.GetAxis("Horizontal"), -1f, 1f);
            // Triggers: try common axes, fall back to A/B buttons.
            float rt = SafeAxis("RightTrigger");
            float lt = SafeAxis("LeftTrigger");
            s.Throttle = rt > 0.01f ? rt : (Input.GetKey(KeyCode.JoystickButton0) ? 1f : 0f);
            s.Brake = lt > 0.01f ? lt : (Input.GetKey(KeyCode.JoystickButton1) ? 1f : 0f);
            s.Handbrake = Input.GetKey(KeyCode.JoystickButton2);
            s.ShiftUp = Input.GetKeyDown(KeyCode.JoystickButton5);
            s.ShiftDown = Input.GetKeyDown(KeyCode.JoystickButton4);
            return s;
        }

        // Axis may not exist in the Input Manager; guard to avoid exceptions.
        private static float SafeAxis(string name)
        {
            try { return Mathf.Clamp01(Input.GetAxis(name)); }
            catch { return 0f; }
        }
    }
}
