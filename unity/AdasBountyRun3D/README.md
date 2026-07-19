# ADAS Bounty Run 3D — Unity/URP driving core (foundation)

A **separate, parallel Unity (URP) project** that builds the realistic 3D driving
foundation requested in the enhancement brief: real road-condition physics where the
**surface couples to handling**, on an Android-first, mid-tier-device budget.

> This lives under `unity/AdasBountyRun3D/` and does **not** touch the shipping native
> Android game at the repo root. They are two tracks: the native Kotlin game is the
> playable product today; this Unity project is the ground-up 3D rebuild, started at
> the physics gate.

## Scope of this delivery
Per the brief's non-negotiable order — *"Start with step 1 and the vehicle physics test
scene. Stop and let me validate FEEL before continuing."* — this delivers **Steps 1–2**:

- **Step 1 — Project scaffold**: folder structure, assembly definitions, URP + package
  manifest, and an Editor script that creates the URP asset and config assets.
- **Step 2 — Vehicle physics (the FEEL gate)**: Rigidbody + 4 WheelColliders, suspension,
  slip-curve friction, weight transfer (COM + suspension), engine torque curve, gearbox,
  Ackermann steering, downforce, **surface-friction coupling** (asphalt/wet/gravel/
  pothole/ice…), touch/tilt/gamepad/keyboard input, and Android haptics — all built and
  wired by Editor automation, plus a self-contained test scene.

Steps 3–9 (road/spline + Cesium, weather, traffic AI, post-FX, HUD/audio, perf, build)
are **not** built yet; their decoupling interfaces already exist so they slot in cleanly.

## Quick start
```
1. Open unity/AdasBountyRun3D/ in Unity 2022.3.62f3 LTS.
2. Set Player ▸ Color Space = Linear, Active Input Handling = Both (see MANUAL_STEPS.md).
3. Menu:  ABR ▸ Setup ▸ 1 · Scaffold URP + Configs
4. Menu:  ABR ▸ Setup ▸ 3 · Build Physics Test Scene
5. Press Play. Drive W/A/S/D (Space = handbrake). Z/X change road wetness.
```
Drive across the wet / gravel / sand / ice / pothole / speed-breaker patches and watch
the `GRIP` / `SLIP` / `SURFACE` / `WETNESS` readout change — that is road condition
driving handling, the core requirement.

## Architecture (decoupled by asmdef + interface)
```
ABR.Core     — interfaces & shared types (no dependencies)
  IVehicleInput, ISurfaceProvider, IEnvironmentState, IHapticsService, IVehicleTelemetry
  SurfaceType, SurfaceSample, EnvironmentState, ChaseCamera
ABR.Vehicle  — depends on ABR.Core
  Config/  VehicleProfile, SurfaceProfile, SurfaceDatabase  (ScriptableObjects; no magic numbers)
  Runtime/ VehicleController, WheelUnit, Drivetrain, SurfaceFrictionModel, SurfaceMarker, MarkerSurfaceProvider
  Input/   InputSource, Keyboard/Touch/Tilt/Gamepad sources, CompositeVehicleInput
  Haptics/ AndroidHaptics, NoopHaptics, HapticsFactory
  UI/      FeelDebugHud (IMGUI; interim)
ABR.Editor   — Editor-only; depends on Core + Vehicle
  ProjectScaffoldEditor, VehicleRigBuilderEditor, TestSceneBuilderEditor  ([MenuItem] automation)
```

## Deliverable rules honoured
- Every system in its own **asmdef**, decoupled via **interfaces** (swap implementations).
- **No hardcoded magic numbers** — all tuning is in ScriptableObject configs.
- Each module ships **code + Editor automation + a README** stating auto vs manual.
- Top-level **BUILD_ORDER.md** and consolidated **MANUAL_STEPS.md**.
- Assumptions flagged inline as `// ASSUMPTION:`.

## ⚠️ Honesty
This C# was written **outside a Unity Editor** (none is available in the build sandbox),
so it is **not compiler-verified against UnityEngine**. It follows Unity API conventions
and is structured for a clean import, but expect to resolve a few minor version/API nits
in the Console on first open. Nothing editor-bound is silently skipped — it is either
scripted or listed in MANUAL_STEPS.md.
