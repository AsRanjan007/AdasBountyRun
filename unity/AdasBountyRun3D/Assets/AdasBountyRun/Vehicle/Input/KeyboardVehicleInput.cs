using UnityEngine;

namespace ABR.Vehicle
{
    /// <summary>
    /// Keyboard source (WASD / arrows, Space = handbrake, LShift/LCtrl = shift).
    /// Primary tool for validating vehicle FEEL in the editor.
    /// ASSUMPTION: legacy Input Manager active (Project Settings → Player →
    /// Active Input Handling = "Input Manager (Old)" or "Both").
    /// </summary>
    public sealed class KeyboardVehicleInput : IInputSource
    {
        public bool Available => true;

        public InputSample Read(float deltaTime)
        {
            var s = new InputSample();
            if (Input.GetKey(KeyCode.W) || Input.GetKey(KeyCode.UpArrow)) s.Throttle = 1f;
            if (Input.GetKey(KeyCode.S) || Input.GetKey(KeyCode.DownArrow)) s.Brake = 1f;
            if (Input.GetKey(KeyCode.A) || Input.GetKey(KeyCode.LeftArrow)) s.Steer -= 1f;
            if (Input.GetKey(KeyCode.D) || Input.GetKey(KeyCode.RightArrow)) s.Steer += 1f;
            s.Handbrake = Input.GetKey(KeyCode.Space);
            s.ShiftUp = Input.GetKeyDown(KeyCode.LeftShift);
            s.ShiftDown = Input.GetKeyDown(KeyCode.LeftControl);
            return s;
        }
    }
}
