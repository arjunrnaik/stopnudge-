# WakeMe — Bengaluru Metro & Bus Stop Data Pipeline

An offline-first transit data pipeline producing verified, clean OpenStreetMap (OSM) data packs for the WakeMe Android proximity alert app.

## Project Structure

```text
wakeme/
├── data/
│   ├── raw/                  # Cached raw Overpass responses (JSON/XML)
│   └── processed/            # Final GeoJSON, JSON, and Room SQLite databases
├── reports/                  # Verification reports, spot-check CSV, diff reports
├── src/
│   ├── adapters/             # Pluggable source adapters (Overpass, OSM API)
│   ├── cleaning/             # Geometries, deduplication, name normalization
│   ├── validation/           # Quality checks, BMRCL metro reconciliation
│   └── writers/              # GeoJSON, SQLite (Room-friendly), JSON writers
├── tests/                    # Unit tests with hand-crafted fixtures
├── requirements.txt          # Pinned dependencies (pytest)
└── README.md
```

## Prerequisites

- Python 3.11+
- Git

## Installation

```bash
python -m pip install -r requirements.txt
```

## Running the Pipeline

1. **Tag Discovery & Probe**:
   ```bash
   python -m src.tag_discovery
   ```

2. **Run Pipeline (Fetch, Clean, Validate, Package)**:
   ```bash
   python -m src.main
   ```

3. **Run Unit Tests**:
   ```bash
   python -m pytest tests/ -v
   ```

## Output Artifacts

- `data/processed/metro_stations.geojson` & `data/processed/metro_stations.json`
- `data/processed/bus_stops.geojson` & `data/processed/bus_stops.json`
- `data/processed/wakeme_transit.db` (SQLite database with Android Room compatibility)
- `data/processed/metadata.json` (ODbL attribution, queries, checksums, timestamps)
- `reports/verification_report.md` (Detailed ground-truth audit)
- `reports/spot_check_sample_50.csv` (Stratified sample for human verification)
