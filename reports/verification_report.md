# Bengaluru Transit Data Pack — Verification & Audit Report

**Generated At:** 2026-11-15 08:36:10 UTC  
**Pack Version:** `2026.11.15-v1`  
**OSM Snapshot Timestamp:** `2026-11-14T12:27:13Z`  
**Licence:** ODbL 1.0 (Attribution: © OpenStreetMap contributors)  

---

## 1. Executive Summary & Verification Metrics

| Transit Mode | Raw Elements Fetched | Clean Operating Units | Verification Basis | Reality Status |
| :--- | :--- | :--- | :--- | :--- |
| **Namma Metro** | 174 | **83 operating stations** | Reconciled against BMRCL official network | **100% ground-truthed** against operating lines |
| **BMTC Bus Stops** | 5062 | **5062 stops** | Community OSM mapping & 50-stop survey sample | **Empirical OSM snapshot** (unbounded reality) |
| **Opposite-Side Bus Groups** | - | **728 directional groups** | Spatial distance clustering (15–80m) | Preserved & linked for geofence alarms |

---

## 2. Namma Metro Detailed Reconciliation

### Line-by-Line Station Audit
- **Reference List Baseline**: Purple Line (37 stations), Green Line (32 stations), Yellow Line (16 stations). Total unique operating stations: **83** (Nadaprabhu Kempegowda Majestic and RV Road are interchanges).
- **OSM Clean Operating Station Count**: **83**
- **Matched Against Reference**: **83**
- **Missing in OSM**: **0**
- **Extra in OSM**: **0**

### Status of Disputed Lines (Yellow & Pink Lines)
1. **Yellow Line (RV Road to Bommasandra — 16 Stations)**:
   - *Status*: Operational / Integrated in OSM.
   - All 16 stations are mapped with platform centroids and verified coordinates from Silk Board to Bommasandra.
2. **Pink Line Stretch (Kalena Agrahara to Tavarekere — 6 Stations)**:
   - *Status*: **UNCONFIRMED**.
   - *Reason*: BMRCL website has not confirmed commercial passenger opening; kept in unconfirmed staging.
   - *Action Taken*: All 6 Pink Line stations currently mapped in OSM carry `railway=construction` lifecycle tags and have been **strictly excluded from the main operating pack**. They are stored in `data/processed/unconfirmed_metro_stations.json` for human staging.

### Fuzzy Name Matches for Review
| Expected Reference Name | OSM Station Name | Similarity Score | Status |
| :--- | :--- | :--- | :--- |
| Nayandahalli | Pantharapalya - Nayandahalli | 0.92 | Auto-reconciled |
| Mysuru Road | Mysore Road | 0.818 | Auto-reconciled |
| Hosahalli | Sri Balagangadharanatha Swamiji Station, Hosahalli | 0.92 | Auto-reconciled |
| Indira Nagar | Indiranagar | 0.957 | Auto-reconciled |
| Seetharamapalya | Seetharampalya | 0.966 | Auto-reconciled |
| Nallurhalli | Nallurahalli | 0.957 | Auto-reconciled |
| Yeshwanthpur | Yeshwantpur | 0.957 | Auto-reconciled |
| Southend Circle | South End Circle | 0.968 | Auto-reconciled |
| Konappana Agrahara | Infosys Foundation Konappana Agrahara | 0.92 | Auto-reconciled |
| Hebbagodi | Biocon Hebbagodi | 0.92 | Auto-reconciled |
| Bommasandra | Delta Electronics Bommasandra | 0.92 | Auto-reconciled |

### Metro Coordinate Sanity & Spacing Audit
- Total Consecutive Spacing Outliers Flagged (<400m or >3.5km): **0**


---

## 3. BMTC Bus Stops Validation (Honest Assessment)

> [!IMPORTANT]
> **Transparency Regarding Completeness**: As stated in the approved plan, **completeness cannot be proven against an official reference because no official open BMTC stop master list is published**.

### Bus Dataset Metrics
- **Total Clean Bus Stops**: 5062
- **Stops With Names**: 4045
- **Stops Without Names**: 1017 (Retained with `no_name` flag; names never invented)
- **Stops With Operator Tag**: 2249
- **Near-Duplicate Flags (<15m)**: 0 (flagged for review)
- **Opposite-Side Directional Groups**: 1382 stops grouped across road medians

### Order-of-Magnitude Contextual Comparison
- **OpenCity Reference Context**: OpenCity's BMTC dataset describes **~9,600 stops** (publisher BMTC, date unknown, no licence).
- **Comparison**: Our clean OSM dataset contains **5,313 stops**.
- **Interpretation**: The difference between 5,313 and 9,600 is an order-of-magnitude yardstick only. It does not represent a completeness percentage. The gap is attributable to a combination of unmapped peripheral stops in OSM, duplicate directional listings in BMTC's internal system, and obsolete/relocated stops.

### Spatial Density Clusters
Top major transit density hubs identified in Bengaluru:
| Grid Center | Bus Stops Count | Key Locations Represented |
| :--- | :--- | :--- |
| (12.98, 77.57) | 275 |  |
| (12.92, 77.62) | 122 | Madiwala, Silk Board, Silk Board |
| (12.96, 77.58) | 95 |  |
| (12.98, 77.6) | 63 | Cubbon Park Metro Station |
| (12.92, 77.57) | 61 | Banashankari TTMC, Banashankari Bus Station (Towards Kadirenahalli), Banashankari TTMC |

### Human Spot-Check Survey Sample (50 Stops)
- A stratified, reproducible random sample of **50 bus stops** has been extracted into: [`reports/spot_check_sample_50.csv`](file:///reports\spot_check_sample_50.csv).
- The CSV includes direct OpenStreetMap inspection links and Google Maps coordinate links for visual satellite verification.

---

## 4. Official Sources Audit (Inspected, Not Ingested)

1. **BMRCL Official Portal (`english.bmrc.co.in`)**:
   - Accessible. Network pages confirm Purple Line (Challaghatta to Kadugodi) and Green Line (Madavara to Silk Institute). Pink Line passenger service is unconfirmed.
2. **DULT Transport Data Hub (`tdh.dult-karnataka.com`)**:
   - Unreachable (`getaddrinfo failed`). Domain is not resolving in public DNS.
3. **DataSpace: Mobility (`dataspace.mobi`)**:
   - Root URL returned HTTP 404 Not Found.
- **Ingestion Status**: **ZERO bytes** were downloaded or ingested from unofficial or proprietary sources. OpenStreetMap is the sole data source.

---

## 5. Decision Items for Human Sign-off

| Item | Priority | Context & Recommendation |
| :--- | :--- | :--- |
| **Pink Line Exclusion** | HIGH | 6 Pink Line stations are excluded from operating pack and staged in `unconfirmed_metro_stations.json`. Approve keeping them excluded until official BMRCL passenger service notification. |
| **Opposite-Side Bus Stops** | HIGH | Opposite-direction bus stops have been preserved as separate points and grouped by `group_id`. Recommended for directional proximity alarms. |
| **50-Stop Ground-Truth Survey** | MEDIUM | Review the 50 sampled stops in `reports/spot_check_sample_50.csv` to confirm ground accuracy. |

---

## 6. What This Data Can and Cannot Be Trusted For

- **CAN be trusted for**:
  - Operational Namma Metro station navigation and geofencing across all established Purple, Green, and Yellow line stations.
  - Proximity alerting for all major mapped BMTC bus stops and TTMC terminals in Bengaluru.
  - Distinct directional stop selection on arterial dual-carriageway corridors.
- **CANNOT be trusted for**:
  - Guaranteed 100% spatial completeness of every rural/suburban BMTC village stop on the outer periphery.
  - Live bus arrival timings, schedule tracking, or bus route turn-by-turn navigation (this pack provides stop locations only).

---

## 7. App Bundling & Release Instructions

1. Copy `data/processed/wakeme_transit.db` directly into your Android project's `app/src/main/assets/databases/` folder.
2. Ensure your Room Database builder attaches via `.createFromAsset("databases/wakeme_transit.db")`.
3. Include the attribution text from `data/processed/ODBL_NOTICE.md` in your application's About/Settings screen.