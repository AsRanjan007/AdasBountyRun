# ABR.Core

Dependency-free interfaces and shared value types. Everything else depends *inward* on
this; it depends on nothing (except UnityEngine for `Vector3`/`MonoBehaviour`).

- `IVehicleInput` — normalised driver input (throttle/brake/steer/handbrake/shift).
- `ISurfaceProvider` — resolves the road surface under a wheel.
- `IEnvironmentState` — the single `Wetness` coupling parameter (visuals + friction).
- `IHapticsService` — impact + surface rumble.
- `IVehicleTelemetry` — read-only vehicle state for HUD/audio/ADAS.
- `SurfaceType`, `SurfaceSample` — pure data.
- `EnvironmentState` (holds wetness), `ChaseCamera` (damped follow + speed FOV).

**Automatic:** nothing to bake. **Manual:** none. Pure code layer.
