# ABR.Vehicle

The FEEL gate. Data-driven vehicle dynamics + the road-condition ↔ handling coupling.

- **Config (ScriptableObjects, no magic numbers):** `VehicleProfile`, `SurfaceProfile`,
  `SurfaceDatabase`.
- **Runtime:** `VehicleController` (orchestrator, `IVehicleTelemetry`), `WheelUnit`
  (per-wheel collider + friction + visual), `Drivetrain` (engine/gearbox), `SurfaceFrictionModel`
  (pure coupling: surface + wetness → `WheelFrictionCurve`), `SurfaceMarker` +
  `MarkerSurfaceProvider` (surface source, swappable for splatmap/Cesium later).
- **Input:** `CompositeVehicleInput` mixing keyboard/touch/tilt/gamepad `IInputSource`s.
- **Haptics:** `AndroidHaptics` / `NoopHaptics` via `HapticsFactory`.
- **UI:** `FeelDebugHud` (interim IMGUI telemetry).

**Automatic (via ABR.Editor):** the rig is built and wired by `VehicleRigBuilderEditor`.
**Manual:** tune the profile assets in the Inspector; swap placeholder cube/cylinder
meshes for a real model. See ../../MANUAL_STEPS.md.
