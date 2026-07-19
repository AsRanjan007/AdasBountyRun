using UnityEngine;
using ABR.Core;

namespace ABR.Vehicle
{
    public enum InputMode { Auto, Touch, Tilt, Gamepad, Keyboard }

    /// <summary>
    /// Aggregates the concrete input sources into the single <see cref="IVehicleInput"/>
    /// the controller consumes. <see cref="InputMode.Auto"/> resolves per platform;
    /// keyboard is always mixed in inside the editor so FEEL can be validated on
    /// desktop. Values are combined by dominance (largest magnitude wins per axis).
    /// </summary>
    public sealed class CompositeVehicleInput : MonoBehaviour, IVehicleInput
    {
        [SerializeField] private InputMode mode = InputMode.Auto;
        [SerializeField] private bool tiltSteerOnMobile = false;

        private IInputSource _primary;
        private IInputSource _pedals;        // touch pedals when tilt-steering
        private KeyboardVehicleInput _keyboard;

        private InputSample _s;

        public float Throttle => _s.Throttle;
        public float Brake => _s.Brake;
        public float Steer => _s.Steer;
        public bool Handbrake => _s.Handbrake;
        public bool ShiftUpPressed => _s.ShiftUp;
        public bool ShiftDownPressed => _s.ShiftDown;

        private void Awake()
        {
            _keyboard = new KeyboardVehicleInput();
            var resolved = mode == InputMode.Auto ? ResolveAuto() : mode;
            switch (resolved)
            {
                case InputMode.Touch: _primary = new TouchVehicleInput(); break;
                case InputMode.Tilt: _primary = new TiltVehicleInput(); _pedals = new TouchVehicleInput(); break;
                case InputMode.Gamepad: _primary = new GamepadVehicleInput(); break;
                default: _primary = _keyboard; break;
            }
        }

        private InputMode ResolveAuto()
        {
            if (Application.isMobilePlatform)
                return tiltSteerOnMobile ? InputMode.Tilt : InputMode.Touch;
            return InputMode.Keyboard;
        }

        public void Sample(float deltaTime)
        {
            InputSample combined = _primary != null ? _primary.Read(deltaTime) : InputSample.Empty;
            if (_pedals != null) combined = Dominant(combined, _pedals.Read(deltaTime));
            // Keyboard override for editor testing.
            if (Application.isEditor) combined = Dominant(combined, _keyboard.Read(deltaTime));
            _s = combined;
        }

        /// <summary>Per-axis dominance so pedal + tilt (or + keyboard) compose cleanly.</summary>
        private static InputSample Dominant(InputSample a, InputSample b)
        {
            return new InputSample
            {
                Throttle = Mathf.Max(a.Throttle, b.Throttle),
                Brake = Mathf.Max(a.Brake, b.Brake),
                Steer = Mathf.Abs(a.Steer) >= Mathf.Abs(b.Steer) ? a.Steer : b.Steer,
                Handbrake = a.Handbrake || b.Handbrake,
                ShiftUp = a.ShiftUp || b.ShiftUp,
                ShiftDown = a.ShiftDown || b.ShiftDown
            };
        }
    }
}
