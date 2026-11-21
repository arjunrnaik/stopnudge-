# WakeMe (v2) — Future Implementation Roadmap

This document outlines the planned feature additions, technical architecture, and implementation strategies for upcoming releases of **WakeMe**, based on commuter UX research and real-world public transit challenges (Namma Metro & BMTC buses).

---

## 🎯 Implementation Priority Matrix

| Priority | Feature | Category | Target Release |
| :--- | :--- | :--- | :--- |
| **P1** | **"1-Station / 2-Stations Before" Mode** | Commute Core | v2.1 |
| **P1** | **Luggage & Belongings Nudge** | UX & Peace of Mind | v2.1 |
| **P1** | **Adaptive Battery Governor** | Performance | v2.1 |
| **P2** | **Speed-Aware Dynamic Geofencing** | Smart Geofencing | v2.2 |
| **P2** | **Lock-Screen Live Activity Notification** | UI / System | v2.2 |
| **P2** | **Anti-Sleep Verification Challenge** | Alarm Reliability | v2.2 |
| **P3** | **Underground Tunnel Dead-Reckoning** | Offline Transit | v2.3 |
| **P3** | **Multi-Leg Journey (Hop-Off Alarms)** | Multi-Modal Transit | v2.3 |
| **P3** | **"Almost Home" Auto Safety Ping** | Safety & Social | v2.4 |
| **P3** | **Wear OS / Smartwatch Silent Wrist Tap** | Wearables | v2.4 |

---

## 🚀 Detailed Feature Specifications

### 1. "1-Station / 2-Stations Before" Pre-Arrival Mode
- **Problem**: Metro commuters don't think in raw meters (e.g. 700m). They think: *"Wake me at Indiranagar so I have time to gather my bag, walk through the crowded coach, and stand near the doors before Halasuru."*
- **Implementation Strategy**:
  - Add a mode selector in Screen 2: `[ At Station ]` | `[ 1 Stop Before ]` | `[ 2 Stops Before ]` | `[ Custom Radius ]`.
  - Using `NammaMetroNetwork.ACTIVE_STATIONS`, look up the preceding station on the traveler's active line.
  - Automatically place the geofence boundary centered on the precursor station.

---

### 2. Luggage & Belongings Nudge on Alarm Dismiss
- **Problem**: Jolted awake as the train doors open, half-asleep commuters frequently leave behind bags, umbrellas, keys, or metro cards on overhead racks or adjacent seats.
- **Implementation Strategy**:
  - On alarm trigger and dismissal, show a high-contrast checklist dialog with optional Text-to-Speech (TTS):
    > *"Wake up! Don't forget your laptop bag, umbrella, and metro card!"*
  - Configurable in Profile/Settings: allow users to customize their checklist (e.g., *Lunchbox*, *Laptop Bag*, *Headphones*).

---

### 3. Adaptive Battery Governor (Smart GPS Polling)
- **Problem**: Continuous 1-second high-accuracy GPS polling drains 25%–40% battery during a 45-minute commute. Commuters traveling home in the evening often have < 20% battery remaining.
- **Implementation Strategy**:
  - Dynamically scale the location request interval based on remaining distance to destination:
    - **Distance > 10 km**: Poll once every 2–3 minutes using coarse / balanced network provider.
    - **Distance 3 km – 10 km**: Poll once every 30 seconds using fused location provider.
    - **Distance < 2 km**: Switch to high-accuracy continuous GPS (1–2 second intervals).
  - **Expected Impact**: Reduces background battery consumption by **up to 75–80%**.

---

### 4. Speed-Aware Dynamic Geofencing (Time vs. Distance)
- **Problem**: A fixed 500m radius behaves inconsistently across transit modes:
  - On a high-speed metro at 70 km/h: 500m is crossed in **25 seconds** (too brief to wake up and exit).
  - In heavy junction traffic at 4 km/h: 500m takes **15 minutes** (wakes the user far too early).
- **Implementation Strategy**:
  - Dynamically calculate the trigger distance using current velocity:
    $$\text{Effective Radius} = \max\left(\text{User Radius},\; \text{Speed (m/s)} \times 90\text{ seconds}\right)$$
  - Ensures a guaranteed **minimum 90-second preparation buffer** regardless of whether traveling on a fast train or crawl-speed bus.

---

### 5. Live Lock-Screen Dynamic Notification & Progress Bar
- **Problem**: Users want to glance at their phone without unlocking it to know how many stops or kilometers are left.
- **Implementation Strategy**:
  - Use Android Foreground Service ongoing notifications with custom remote views or `MediaStyle` notification:
    - Current transit speed: `52 km/h`
    - Remaining distance: `3.2 km remaining (approx. 4 mins)`
    - Real-time segmented progress bar.
    - Lock-screen action buttons: `[ +1 km Snooze ]` and `[ End Trip ]`.

---

### 6. Anti-Sleep Grogginess Verification Challenge
- **Problem**: Deep sleepers often tap "Dismiss" while half-conscious, fall back asleep, and wake up at the terminus station.
- **Implementation Strategy**:
  - Optional toggle in trip setup: `Require Shake to Dismiss` or `Swipe Slider`.
  - When enabled, alarm cannot be silenced by a single accidental tap; requires shaking the phone 3–5 times (detected via `Sensor.TYPE_ACCELEROMETER`) or walking 10 steps (via `Sensor.TYPE_STEP_DETECTOR`).

---

### 7. Underground Tunnel Dead-Reckoning Mode
- **Problem**: Deep underground metro sections (e.g. Majestic $\leftrightarrow$ Cubbon Park) lose GPS satellite line-of-sight completely. Standard geofencing freezes until the train emerges above ground.
- **Implementation Strategy**:
  - Implement an **Inertial Station Watchdog**:
    - When GPS accuracy degrades above 50m while traveling on an underground corridor, transition to dead-reckoning.
    - Use calibrated BMRCL inter-station travel times (average 105 to 130 seconds per station) combined with accelerometer motion detection to increment virtual station progress.
    - Trigger the alarm predictably even with zero satellite visibility.

---

### 8. Multi-Leg Journey (Hop-Off & Transfer Alarms)
- **Problem**: Everyday commutes in Bengaluru involve transfers (e.g. Purple Line Metro to KR Pura $\rightarrow$ BMTC Feeder Bus to Marathahalli).
- **Implementation Strategy**:
  - Multi-stage destination configuration:
    - **Leg 1**: Metro interchange station (Transfer wake-up: *"Change to BMTC Feeder Bus"*).
    - **Leg 2**: Final bus stop (Arrival wake-up: *"You have reached your final destination"*).
  - Automatically transitions to Leg 2 tracking once Leg 1 is dismissed.

---

### 9. "Almost Home" Auto WhatsApp / SMS Safety Ping
- **Problem**: Family members or flatmates often ask for commute updates, especially late at night.
- **Implementation Strategy**:
  - When the final pre-arrival geofence is triggered:
    - Pre-fill an automated SMS or WhatsApp Intent:
      > *"Hey! I just crossed Indiranagar station, reaching home in about 7 minutes!"*
    - One-tap send or optional background SMS (with explicit user permission).

---

### 10. Wear OS / Smartwatch Silent Wrist Tap
- **Problem**: Loud ringtones inside quiet metro coaches or buses can be disturbing or embarrassing to other passengers.
- **Implementation Strategy**:
  - Wear OS companion app or Android Wearable Notification bridge:
    - Delivers an intense, progressive haptic vibration directly to the user's wrist.
    - Allows discreetly dismissing or snoozing the alarm without taking the phone out of a pocket or bag.

---
*Created on 2026-11-21 for WakeMe (v2) Project Development.*
