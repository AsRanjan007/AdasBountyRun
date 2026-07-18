# Architecture — ADAS Bounty Run

This document maps the implementation onto the master specification (§20, §29–§31).

## 1. System diagram (spec §30)

```
Player Input (touch controls)
     │
     ▼
GameView (SurfaceView + fixed-step loop)
     │  sets steer/throttle/brake
     ▼
GameWorld.update(dt) ───────────────────────────────────────────┐
     │                                                           │
     ├─► AdasManager  ──► SensorModel (perception)               │
     │        ├─ Warning UI/Voice events ─► GameActivity (TTS)   │
     │        ├─ Automatic braking  ─► PlayerCar.assistBrake     │
     │        ├─ Steering assist     ─► PlayerCar.assistSteer    │
     │        └─ Safety event logger ─► ReportManager            │
     │                                                           │
     ├─► PlayerCar.update (physics + damage)                     │
     ├─► Spawner / Hazard Director ─► RoadEntity pool            │
     ├─► Entity + AI update (traffic/pedestrian/animal)          │
     ├─► CollisionManager ─► BountyManager + DamageManager       │
     ├─► PoliceManager ─► WantedLevelManager                     │
     └─► GameSession.evaluate (game-over conditions) ◄───────────┘
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
   LevelManager            ReportManager ─► ReportActivity (S–F grade)
```

Rendering (`Renderer`) reads world state each frame but never mutates it.

## 2. Update pipeline (one frame)

`GameWorld.update(dt, nowMs)` runs a strict order so assists take effect the same frame:

1. **Sensor quality** recomputed from weather × sensor damage.
2. **ADAS** (`AdasManager.update`) — perceive → warn → apply brake/steer assists.
3. **Speeding enforcement** (bounty penalty + report event).
4. **Physics** (`PlayerCar.update`) — longitudinal + lateral integration.
5. **Lane discipline** enforcement.
6. **Level progress** — odometer; completes the level at the distance goal.
7. **Spawning** — traffic top-up + Hazard Director tick (fair, avoidable spawns).
8. **Entities + AI** — move, track threat exposure, resolve collisions, award avoidance.
9. **Police pursuit** — deploy/update units, escape penalty, cooperation reward.
10. **Terminal conditions** — `GameSession.evaluate`.

## 3. Manager responsibilities (spec §20)

| Class | Responsibility |
|-------|----------------|
| `GameSession` | The GameManager hub: owns per-run managers, enforces the 4 game-over conditions, drives level completion and progression. |
| `BountyManager` | Authoritative score; applies configurable rewards/penalties; critical casualty zeroing. |
| `LevelManager` | Current level, target speed (+10 km/h/level), distance/progress, difficulty ramp. |
| `WantedLevelManager` | 5-tier wanted state machine; escalate/cooperate/decay; patrol counts, roadblocks, helicopter. |
| `ReportManager` | Aggregates `SafetyEvent`s → `LevelReport`; computes S–F grade + recommendations. |
| `AdasManager` | Runs every enabled ADAS feature; issues warnings; drives AEB/LKA/ESA/ACC; feeds bounty + report. |
| `SensorModel` | Perception: forward in-path threat, TTC, closing speed, blind-spot detection; range degraded by weather/damage. |
| `CollisionManager` | Impact → damage + bounty + police escalation + educational message. |
| `PoliceManager` | Deploys/updates `PoliceCar` units to the wanted tier; continuous escape drain. |
| `Spawner` | Traffic + Hazard Director; country-weighted mix; guarantees avoidable hazards. |
| `GameWorld` | Owns all live objects; runs the update pipeline; exposes render/report state. |
| `GameView` | SurfaceView host + game-loop thread + input; posts results to the UI thread. |
| `Renderer` | Pseudo-3D world + automotive HUD + sensor overlay + banners. |

## 4. Interfaces (spec §20)

```
IDetectable    — worldZ, laneX, speedKmh, halfWidth/Length, active, kind   (RoadEntity, PoliceCar)
ICollisionRisk — kind, hazardKind                                          (RoadEntity)
IDamageable    — applyDamage(part), isDisabled                             (PlayerCar)
BountyEvent / SafetyEvent — value objects routed to Bounty/Report managers
```

`DetectKind` (VEHICLE, PEDESTRIAN, CYCLIST, ANIMAL, HAZARD, POLICE, EMERGENCY) and
`SafetyType` classify perception and reporting.

## 5. Configuration model (spec §20 ScriptableObjects → Kotlin data classes)

All tunables live in `config/` and are injected, never hard-coded:

- `ScoringRules` — every reward/penalty (spec §8).
- `LevelTable` — base speed, +10 step, difficulty/night/hazard/traffic curves (spec §7).
- `CountryProfile` — driving side, units, RHD, vehicle mix, hazards (spec §4; India fully specified).
- `VehicleSpec` — dynamics + health (spec §13).
- `AdasConfig` — enabled features + thresholds (FCW TTC, AEB TTC, ACC headway, LDW/LKA offsets).
- `Weather` / `TimeOfDay` / `EnvironmentType` / `GameMode` / `HazardKind` — world enums with physics modifiers.

## 6. Gameplay sequence (spec §31)

```
Splash ─► Menu (Country ▶ auto driving-side ▶ Environment ▶ Vehicle ▶ ADAS ▶ Weather/Time ▶ Mode)
      ─► Game (drive ▶ ADAS warns ▶ player responds)
            ├─ safe  ─► +bounty
            └─ unsafe ─► collision ─► −bounty ─► police
                              ├─ stop safely ─► cooperate (heat ↓)
                              └─ flee ─► pursuit ─► caught? ─► GAME OVER
      ─► level distance met ─► ADAS Report (S–F) ─► +10 km/h ─► next level
Game-over when: police catch · bounty 0 · vehicle destroyed · critical casualty.
```

## 7. Class relationships (condensed)

```
GameActivity ──has──► GameView ──owns──► GameWorld
GameWorld ──owns──► GameSession, PlayerCar, AdasManager, CollisionManager,
                    PoliceManager, Spawner, ObjectPool<RoadEntity>
GameSession ──owns──► BountyManager, LevelManager, WantedLevelManager, ReportManager
AdasManager ──uses──► SensorModel, PlayerCar, IDetectable[]
PoliceManager ──owns──► PoliceCar[]
Renderer ──reads──► GameWorld
```

## 8. Performance (spec §28)

Object pooling for road users (`ObjectPool`), no per-frame allocation in the hot path,
`dt` clamping for stall resilience, distance-culled rendering, and cached `Paint`s. The
loop targets ~60 FPS and degrades gracefully on slower devices.
