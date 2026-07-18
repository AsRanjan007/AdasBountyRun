# Developer Guide — ADAS Bounty Run

## 1. Environment

- Android Studio (Koala+), Android SDK **34**, JDK **17**.
- `minSdk 26`, `targetSdk 34`, Kotlin 1.9.24, AGP 8.5.2, view binding enabled.

```bash
./gradlew assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew installDebug         # deploy to device/emulator (landscape, API 26+)
./gradlew testDebugUnitTest    # JVM unit tests (pure logic)
```

The game logic in `config/`, `core/`, `adas/`, `entities/` and most of `engine/` has **no
Android dependency**, so it compiles and unit-tests on the plain JVM. Only `Renderer`,
`GameView`, `VoiceManager` and the `ui/` activities touch the Android framework.

## 2. Design principles

- **Configuration-driven.** No gameplay number is hard-coded — change `ScoringRules`,
  `LevelTable`, `AdasConfig`, `VehicleSpec` or `CountryProfile` and behaviour follows.
- **One update pipeline.** All simulation flows through `GameWorld.update`; rendering is
  read-only. Keep new systems inside the pipeline in the documented order.
- **Pool everything spawned.** Use `ObjectPool<RoadEntity>`; never allocate entities per frame.
- **Fairness.** The Hazard Director must always leave a valid avoidance path (spec §7).

## 3. Common extensions

### Add a country (spec §4)
Add a `CountryProfile` in `CountryProfile.kt` and append it to `ALL`:
```kotlin
val CANADA = CountryProfile("CA", "Canada", DrivingSide.RIGHT, SpeedUnit.KMH, false,
    mapOf(LocalVehicle.CAR to 1.4f, LocalVehicle.TRUCK to 0.8f),
    listOf(HazardKind.DEER, HazardKind.BROKEN_DOWN_VEHICLE), 0.25f)
```
Driving side, units and the local traffic/hazard mix propagate automatically.

### Add a vehicle (spec §22)
Add a `VehicleSpec` (top speed, accel, service/AEB braking, steer rate, health) to
`VehicleSpec.ALL`. The menu lists it and the physics/damage model consume it directly.

### Tune or add an ADAS feature (spec §9)
- Thresholds live in `AdasConfig` (e.g. `fcwWarnTtc`, `aebTriggerTtc`, `accHeadwaySeconds`).
- Add an `AdasFeature` enum value and handle it inside `AdasManager.update`. Emit warnings
  with `emit(...)`, apply assists via `player.assistBrake/assistSteer`, and log
  `SafetyType.ADAS_WARNING/ADAS_HANDLED` so the report reflects it.

### Rebalance scoring (spec §8)
Edit `ScoringRules` (or construct a custom instance in `GameSetup`). All reward/penalty call
sites read from it, so a single change rebalances the whole game.

### Enable another environment (spec §5)
Flip `mvp = true` on an `EnvironmentType`. Environment-specific spawning/props can be added
in `Spawner` keyed on `setup.environment`.

## 4. Testing (spec §27)

Unit tests live in `app/src/test/java/com/adas/bountyrun/`:

| File | Covers |
|------|--------|
| `BountyManagerTest` | rewards, penalties, clamping, critical zeroing, carry-over |
| `LevelProgressionTest` | +10 km/h/level, advance, distance goal, difficulty ramp |
| `WantedAndReportTest` | wanted escalate/cooperate/decay, S–F grading, country config |
| `GameLogicTest` | vehicle accel/brake physics, sensor threat/TTC, critical-casualty collision |

Add tests for new pure logic here; they run without an emulator. For UI/instrumented flows
use `androidx.test` under `app/src/androidTest` (dependencies already declared).

## 5. Edge cases handled (spec §27)

- Pedestrian appears while braking · animal crosses at night · truck stops suddenly
  (slow lead vehicle) — all fed by the Hazard Director with avoidable geometry.
- Sensor damage/weather shorten perception range (`SensorModel.effectiveRange`).
- Bounty reaching zero **during** a pursuit ends the run (`GameSession.evaluate`).
- Vehicle disabled before police arrive → `VEHICLE_DESTROYED` game-over.
- `dt` is clamped so a stall cannot teleport entities through the player.

## 6. Unity-migration notes (spec §1)

The Kotlin layout is intentionally engine-shaped:
- `config/*` data classes → **ScriptableObjects**.
- Managers (`*Manager`, `GameSession`) → **MonoBehaviour singletons / services**.
- `RoadEntity`/`PlayerCar`/`PoliceCar` → **prefabs** with the same fields; `update(dt)` → `FixedUpdate`.
- `SensorModel`/`AdasManager` port directly (pure math). `Renderer` is replaced by URP/HDRP.
- `IDetectable`, `IDamageable`, `ICollisionRisk` map to the spec's C# interfaces verbatim.
