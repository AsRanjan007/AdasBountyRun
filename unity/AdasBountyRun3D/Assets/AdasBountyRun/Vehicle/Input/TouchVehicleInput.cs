using UnityEngine;

namespace ABR.Vehicle
{
    /// <summary>
    /// On-screen touch source: bottom-right quadrant = throttle, bottom-left =
    /// brake, and a horizontal drag anywhere in the upper half steers. A proper
    /// UI-button layer replaces this in build step 7; the interface stays the same.
    /// ASSUMPTION: legacy Input touch API.
    /// </summary>
    public sealed class TouchVehicleInput : IInputSource
    {
        private float _steer;
        private float _dragStartX;
        private int _dragFinger = -1;

        public bool Available => Input.touchSupported;

        public InputSample Read(float deltaTime)
        {
            var s = new InputSample();
            float w = Screen.width, h = Screen.height;

            for (int i = 0; i < Input.touchCount; i++)
            {
                Touch t = Input.GetTouch(i);
                bool bottom = t.position.y < h * 0.5f;
                if (bottom && t.position.x > w * 0.5f) s.Throttle = 1f;
                else if (bottom && t.position.x <= w * 0.5f) s.Brake = 1f;
                else
                {
                    // Upper-half drag steering.
                    if (t.phase == TouchPhase.Began) { _dragFinger = t.fingerId; _dragStartX = t.position.x; }
                    if (t.fingerId == _dragFinger)
                    {
                        float dx = (t.position.x - _dragStartX) / (w * 0.25f);
                        _steer = Mathf.Clamp(dx, -1f, 1f);
                        if (t.phase == TouchPhase.Ended || t.phase == TouchPhase.Canceled) _dragFinger = -1;
                    }
                }
            }
            if (_dragFinger == -1) _steer = Mathf.MoveTowards(_steer, 0f, deltaTime * 3f);
            s.Steer = _steer;
            return s;
        }
    }
}
