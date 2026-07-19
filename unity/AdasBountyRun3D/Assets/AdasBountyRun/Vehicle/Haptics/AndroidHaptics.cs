using UnityEngine;
using ABR.Core;

namespace ABR.Vehicle
{
    /// <summary>No-op haptics for editor / non-Android platforms.</summary>
    public sealed class NoopHaptics : IHapticsService
    {
        public void Impact(float strength) { }
        public void Surface(float intensity, float deltaTime) { }
    }

    /// <summary>
    /// Android haptics via the platform Vibrator (build step 2). Uses one-shot
    /// amplitude vibrations for impacts and throttled pulses for surface rumble.
    /// ASSUMPTION: VIBRATE permission present (it is, in the app manifest).
    /// </summary>
    public sealed class AndroidHaptics : IHapticsService
    {
        private AndroidJavaObject _vibrator;
        private readonly bool _hasAmplitude;
        private float _surfaceCooldown;

        public AndroidHaptics()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
                {
                    _vibrator = activity.Call<AndroidJavaObject>("getSystemService", "vibrator");
                }
                using (var version = new AndroidJavaClass("android.os.Build$VERSION"))
                {
                    _hasAmplitude = version.GetStatic<int>("SDK_INT") >= 26;
                }
            }
            catch { _vibrator = null; }
#endif
        }

        public void Impact(float strength)
        {
            if (_vibrator == null || strength <= 0.02f) return;
            long ms = (long)Mathf.Lerp(15f, 120f, Mathf.Clamp01(strength));
            Vibrate(ms, Mathf.RoundToInt(Mathf.Lerp(80f, 255f, strength)));
        }

        public void Surface(float intensity, float deltaTime)
        {
            if (_vibrator == null) return;
            _surfaceCooldown -= deltaTime;
            if (intensity < 0.25f || _surfaceCooldown > 0f) return;
            _surfaceCooldown = 0.12f;                       // throttle to avoid buzz
            Vibrate(18, Mathf.RoundToInt(Mathf.Lerp(40f, 160f, intensity)));
        }

        private void Vibrate(long ms, int amplitude)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                if (_hasAmplitude)
                {
                    using (var effectClass = new AndroidJavaClass("android.os.VibrationEffect"))
                    {
                        var effect = effectClass.CallStatic<AndroidJavaObject>(
                            "createOneShot", ms, Mathf.Clamp(amplitude, 1, 255));
                        _vibrator.Call("vibrate", effect);
                    }
                }
                else
                {
                    _vibrator.Call("vibrate", ms);
                }
            }
            catch { /* ignore vibration failures */ }
#endif
        }
    }

    /// <summary>Chooses the right haptics implementation for the platform.</summary>
    public static class HapticsFactory
    {
        public static IHapticsService Create()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            return new AndroidHaptics();
#else
            return new NoopHaptics();
#endif
        }
    }
}
