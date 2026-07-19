# ABR.Editor  (Editor-only assembly)

`[MenuItem]` automation for everything Inspector/scene-bound — because this codebase was
authored without opening the Editor.

- `ABR ▸ Setup ▸ 1 · Scaffold URP + Configs` — creates & assigns the URP asset, generates
  `VehicleProfile` + `SurfaceProfile`s + `SurfaceDatabase`.
- `ABR ▸ Setup ▸ 2 · Build Vehicle Rig (at origin)` — constructs a wired Rigidbody +
  4 WheelCollider car with controller/input/HUD.
- `ABR ▸ Setup ▸ 3 · Build Physics Test Scene` — flat plane + surface patches
  (asphalt/wet/gravel/sand/ice/pothole/speed-breaker) + environment + chase cam + the rig,
  saved to `Assets/AdasBountyRun/Scenes/PhysicsTest.unity`.

**Automatic:** URP asset, configs, rig, scene. **Manual fallback:** if URP auto-create
logs a warning on your Unity version, create the URP asset by hand (see ../../MANUAL_STEPS.md).
Bakes (lighting/probes/occlusion) are deferred to later steps.
