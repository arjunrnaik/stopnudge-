"""CLI script to run Step 3 (Fetch raw OSM transit data and record metadata)."""

import argparse
import json
import logging
import sys
from datetime import datetime, timezone
from pathlib import Path

from src.adapters.overpass_adapter import OverpassAdapter

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("FetchStep")


def run_fetch(force_refresh: bool = False) -> Path:
    """Fetch raw transit snapshots and save metadata."""
    adapter = OverpassAdapter()
    
    logger.info("=== STEP 3: FETCHING TRANSIT DATA FROM OPENSTREETMAP ===")
    
    # 1. Fetch Metro
    metro_snapshot = adapter.fetch_metro_data(force_refresh=force_refresh)
    logger.info("Metro snapshot obtained: %d elements (SHA-256: %s)",
                metro_snapshot.element_count, metro_snapshot.sha256_hash[:12])

    # 2. Fetch Bus
    bus_snapshot = adapter.fetch_bus_data(force_refresh=force_refresh)
    logger.info("Bus snapshot obtained: %d elements (SHA-256: %s)",
                bus_snapshot.element_count, bus_snapshot.sha256_hash[:12])

    # 3. Create fetch metadata file
    now_utc = datetime.now(timezone.utc).isoformat()
    metadata = {
        "pipeline_step": "step_3_fetch",
        "generated_at_utc": now_utc,
        "licence": "Open Database License (ODbL) 1.0",
        "attribution": "© OpenStreetMap contributors",
        "attribution_url": "https://www.openstreetmap.org/copyright",
        "datasets": {
            "metro": {
                "mode": metro_snapshot.mode,
                "element_count": metro_snapshot.element_count,
                "endpoint_used": metro_snapshot.endpoint_used,
                "osm_timestamp": metro_snapshot.osm_timestamp,
                "fetched_at": metro_snapshot.fetched_at,
                "raw_file": metro_snapshot.raw_file_path,
                "sha256": metro_snapshot.sha256_hash,
                "query": metro_snapshot.query_text
            },
            "bus": {
                "mode": bus_snapshot.mode,
                "element_count": bus_snapshot.element_count,
                "endpoint_used": bus_snapshot.endpoint_used,
                "osm_timestamp": bus_snapshot.osm_timestamp,
                "fetched_at": bus_snapshot.fetched_at,
                "raw_file": bus_snapshot.raw_file_path,
                "sha256": bus_snapshot.sha256_hash,
                "query": bus_snapshot.query_text
            }
        }
    }

    meta_path = Path("data/raw/fetch_metadata.json")
    meta_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    logger.info("Fetch metadata recorded to %s", meta_path)
    
    print("\n" + "="*80)
    print("STEP 3: FETCH COMPLETED SUCCESSFULLY")
    print(f"Metro Elements Fetched: {metro_snapshot.element_count}")
    print(f"Bus Elements Fetched:   {bus_snapshot.element_count}")
    print(f"OSM Data Timestamp:     {metro_snapshot.osm_timestamp}")
    print(f"Raw Metro File:         {metro_snapshot.raw_file_path}")
    print(f"Raw Bus File:           {bus_snapshot.raw_file_path}")
    print(f"Fetch Metadata File:    {meta_path}")
    print("="*80)
    return meta_path


def main():
    parser = argparse.ArgumentParser(description="Fetch raw OpenStreetMap transit data.")
    parser.add_argument("--force-refresh", action="store_true", help="Bypass local cache and query Overpass directly.")
    args = parser.parse_args()
    
    run_fetch(force_refresh=args.force_refresh)


if __name__ == "__main__":
    main()
