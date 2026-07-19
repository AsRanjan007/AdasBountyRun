# MANUAL_STEPS — things the Editor scripts cannot (or should not) do for you

Consolidated per the deliverable rule: *where automation is impossible, state it
explicitly — never silently skip.*

## One-time project setup (step 1)
1. **Open in Unity 2022.3.62f3 LTS** (your version). `ProjectSettings/ProjectVersion.txt`
   is pinned to it, and `Packages/manifest.json` pins the packages that ship with that
   editor (URP 14.0.12, Input System 1.7.0, Cinemachine 2.9.7, AI Navigation 1.1.6).
   - // ASSUMPTION: those versions resolve on 2022.3.62f3. If Package Manager offers a
     patch update, accept it — none of the step-1/2 code depends on a specific patch.
2. **Color space**: Project Settings ▸ Player ▸ Other Settings ▸ **Color Space = Linear**
   (URP expects linear; cannot be reliably set from a menu script pre-first-import).
3. Run **`ABR ▸ Setup ▸ 1 · Scaffold URP + Configs`**. This creates and assigns the URP
   asset and the config assets under `Assets/AdasBountyRun/_GeneratedConfigs/`.
   - **Fallback if URP auto-create logs a warning** (API differs on your version):
     Assets ▸ Create ▸ Rendering ▸ **URP Asset (with Universal Renderer)**, then assign it
     in Project Settings ▸ Graphics ▸ *Scriptable Render Pipeline Settings* and in each
     Quality level (Project Settings ▸ Quality ▸ Render Pipeline Asset).
4. **Active Input Handling**: Project Settings ▸ Player ▸ **Active Input Handling = Both**
   (or *Input Manager (Old)*). The step-2 input sources use the legacy `Input` class for
   fast FEEL iteration. // ASSUMPTION flagged in the input source files.

## Built-in modules
`Packages/manifest.json` explicitly lists the built-in modules the project needs —
crucially **`com.unity.modules.vehicles`** (WheelCollider/WheelHit). If you ever see
`CS1069: WheelCollider … forwarded to UnityEngine.VehiclesModule`, that module is
disabled: add it back here, or Window ▸ Package Manager ▸ *Built-in* ▸ enable **Vehicles**.

## Assembly definition references
Because the project uses `.asmdef` files, package assemblies are **not** auto-referenced.
`ABR.Editor.asmdef` explicitly references `Unity.RenderPipelines.Universal.Runtime` and
`Unity.RenderPipelines.Core.Runtime` (the scaffold script creates the URP asset). If you add
new code that uses a package type and hit `CS0234 … namespace does not exist`, add that
package's assembly to the relevant `.asmdef`'s **Assembly Definition References**.

## Step 2 — vehicle physics
5. Run **`ABR ▸ Setup ▸ 3 · Build Physics Test Scene`** (it also builds & wires the rig).
6. Press **Play** and validate feel. Tune `_GeneratedConfigs/DefaultVehicleProfile`
   (mass, COM, spring/damper, torque curve, gear ratios, steer, downforce) — all live in
   the Inspector, no code changes.
7. **Placeholder art**: the body is a cube and wheels are cylinders by design. Replace the
   `Body` mesh and `Wheel_*_Visual` meshes with a real car model when available (the
   `WheelCollider` transforms stay; only the visual children change).
8. **Android haptics** need the `VIBRATE` permission — already in the app manifest; for a
   standalone Unity Android build, Unity adds it automatically when the code calls the
   Vibrator, but confirm it appears in the generated `AndroidManifest.xml`.

## Deferred to later steps (not needed to validate FEEL)
- Lightmap baking, reflection-probe baking, occlusion-culling bake (steps 6/8) — bakes
  are Editor-bound; scripts can *configure* them but you press **Bake**.
- Cesium for Unity / EasyRoads3D licensing + import (step 3) — gated behind interfaces;
  swap points are noted in code (`ISurfaceProvider`, road generator to come).
- IL2CPP / ARM64 / keystore (step 9).

## Notes
- The Unity project lives under `unity/AdasBountyRun3D/` and is **completely separate**
  from the shipping native-Android game at the repo root — it does not affect the Gradle
  build.
- This code was written without a Unity Editor in the loop; fix any first-import Console
  errors (likely minor URP/API version differences) before reporting FEEL.
