# Player Guide — ADAS Bounty Run

## Goal
Complete each route safely. Earn bounty by driving smart, protecting road users and using
ADAS correctly. Lose bounty for collisions, speeding and reckless behaviour. Cause an
accident and the police chase you.

> **ADAS assists the driver. It does not replace attentive and responsible driving.**

## Controls
| Control | Action |
|---------|--------|
| ◄ / ► (bottom-left) | Steer left / right |
| GAS (bottom-right) | Accelerate |
| BRAKE (bottom-right) | Brake |
| SENSORS | Toggle the engineering perception overlay (radar cone, bounding boxes, distances) |
| VOICE | Toggle spoken ADAS alerts |
| II | Pause |

## The HUD (spec §16)
- **Speed / Limit / Gear** (bottom-left) — stay at or below the limit.
- **Bounty / Level / Progress** (top-left).
- **ADAS band** (top-center) — active feature, **TTC** (time-to-collision) and following distance.
  Colours: 🟢 safe · 🟡 caution · 🟠 high risk · 🔴 immediate danger.
- **Wanted stars** (top-right) — appear during a pursuit.
- **Vehicle / Sensor health + Lane** (bottom-right). Damage degrades braking, steering and ADAS.
- **Blind-spot dots** flash when a vehicle is beside you and you steer toward it.

## Game modes (spec §3)
- **ADAS Awareness** — on-screen explanations after critical moments (great for learning).
- **Realistic Simulation** — react in real time, no hints.
- **Bounty Challenge** — chase the highest score.
- **Police Escape** — after an accident, decide to cooperate or flee.
- **ADAS Training** — focus on the ADAS features.
- **Free Drive** — pick map, vehicle, weather and time freely.

## Scoring (spec §8, all configurable)
**Rewards** — safe pedestrian avoidance +300 · cyclist +250 · animal +200 · correct AEB +350 ·
safe distance +100 · route without collision +1,000 · perfect level +1,500.

**Penalties** — minor collision −500 · major −1,500 · animal −2,000 · cyclist −3,000 ·
pedestrian −5,000 · red light −600 · speeding −400 · off-carriageway penalty · ignoring a
warning −300 · escaping police (continuous drain) · hitting an emergency vehicle −2,500.

A high-speed pedestrian/road-user casualty is **critical**: bounty drops to zero and the run ends.

## Police & wanted (spec §12)
Accidents raise your **wanted level (1–5)**: more patrols, then roadblocks, spike strips and a
helicopter. **Stopping safely to cooperate** lowers the heat and awards a safe-stop bonus.
Fleeing drains bounty every second and is never rewarded. You lose if the police contain you.

## The run ends when (spec §1)
1. Police catch you · 2. Bounty reaches zero · 3. Your vehicle is destroyed · 4. A critical casualty occurs.

## Level report grades (spec §17)
| Grade | Meaning |
|-------|---------|
| **S** | ADAS Safety Expert |
| **A** | Excellent |
| **B** | Good |
| **C** | Needs Improvement |
| **D** | Unsafe |
| **F** | Critical Risk |

The report also lists which ADAS technology could have prevented each incident. Clear a level
to raise the target speed by **+10 km/h** and carry your bounty into the next level.
