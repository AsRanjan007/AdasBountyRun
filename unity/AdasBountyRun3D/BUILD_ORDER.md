# BUILD_ORDER — ADAS Bounty Run 3D (Unity/URP)

Build and validate in this exact sequence. **Do not proceed to N+1 until N is
self-contained and validated.** Vehicle FEEL is the gate.

| # | Module | Status | Runs automatically | You must do (see MANUAL_STEPS.md) |
|---|--------|--------|--------------------|-----------------------------------|
| 1 | **Project scaffold** | ✅ delivered | `ABR ▸ Setup ▸ 1` creates URP asset + config assets; asmdefs + folders in repo | Open project in Unity 2022.3.62f3 LTS; set Color Space = Linear; confirm URP assigned |
| 2 | **Vehicle physics** (FEEL gate) | ✅ delivered | `ABR ▸ Setup ▸ 2` builds a wired rig; `ABR ▸ Setup ▸ 3` builds the test scene | Press Play, drive, **validate feel**, tune `DefaultVehicleProfile` |
| 3 | Road + environment (spline / Cesium) | ⏳ interfaces stubbed | — | (next milestone) |
| 4 | Weather / time-of-day | ⏳ `IEnvironmentState` + wetness coupling in place | — | (next) |
| 5 | Traffic + scenario AI | ⏳ package (`ai.navigation`) added | — | (next) |
| 6 | Visual fidelity (post-process, probes) | ⏳ | — | (next) |
| 7 | UX / HUD / audio | ⏳ `IVehicleTelemetry` in place; `FeelDebugHud` interim | — | (next) |
| 8 | Mobile perf pass | ⏳ | — | (next) |
| 9 | Build (IL2CPP / ARM64) | ⏳ | — | (next) |

## What "delivered" means here
Steps **1 and 2 only**, per the prompt's instruction to *stop and let you validate
FEEL before continuing*. The decoupling seams for steps 3–9 already exist as
interfaces (`ISurfaceProvider`, `IEnvironmentState`, `IVehicleTelemetry`,
`IHapticsService`, `IVehicleInput`, `IInputSource`) so later modules slot in
without touching the vehicle core.

## Validate step 2 (do this now)
1. Open the project in Unity 2022.3.62f3 LTS.
2. `ABR ▸ Setup ▸ 1 · Scaffold URP + Configs`
3. `ABR ▸ Setup ▸ 3 · Build Physics Test Scene`  (this also runs the rig builder)
4. Press **Play**. Drive with **W/A/S/D** (Space = handbrake). Press **Z/X** to raise/lower wetness.
5. Drive across the **wet / gravel / sand / ice / pothole / speed-breaker** patches and
   watch the on-screen `GRIP`, `SLIP`, `SURFACE`, `WETNESS` readouts change — this is
   the road-condition ↔ handling coupling working.

If the feel is right, say so and I'll build step 3 (road system). If not, tell me what's
wrong (too floaty / tips over / no grip loss / etc.) and I'll tune the profile + model.

## ⚠️ Honest status
This C# is written to Unity API conventions but was authored **outside the Unity
Editor and has not been compiled against UnityEngine** in this environment. Expect to
fix a small number of API/version nits on first import (they'll surface in the Console).
Every editor-bound action is scripted (`[MenuItem]`) or listed in MANUAL_STEPS.md — nothing
is silently skipped.
