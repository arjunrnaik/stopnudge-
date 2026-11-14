"""Packaging coordinator: generates GeoJSON, SQLite, JSON, checksums, and metadata."""

import hashlib
import json
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List

from src.cleaning.models import CleanStop
from src.writers.geojson_writer import write_geojson
from src.writers.json_writer import write_json
from src.writers.licence_notice import generate_attribution_notice
from src.writers.sqlite_writer import write_sqlite

logger = logging.getLogger("Packager")


def compute_sha256(filepath: Path) -> str:
    """Compute SHA-256 hash of a file."""
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def package_datapack(
    metro_stops: List[CleanStop],
    bus_stops: List[CleanStop],
    unconfirmed_metro: List[Dict[str, Any]],
    fetch_metadata: Dict[str, Any],
    output_dir: Path = Path("data/processed")
) -> Dict[str, Any]:
    """Package all clean transit datasets into versioned release files with checksums."""
    output_dir.mkdir(parents=True, exist_ok=True)
    
    now_utc = datetime.now(timezone.utc)
    pack_version = now_utc.strftime("%Y.%m.%d-v1")
    generated_at = now_utc.isoformat()

    logger.info("Packaging Data Pack version %s...", pack_version)

    # 1. Write GeoJSON files
    metro_geojson = write_geojson(metro_stops, output_dir / "metro_stations.geojson")
    bus_geojson = write_geojson(bus_stops, output_dir / "bus_stops.geojson")

    # 2. Write JSON files
    metro_json = write_json(metro_stops, output_dir / "metro_stations.json")
    bus_json = write_json(bus_stops, output_dir / "bus_stops.json")
    
    # Save unconfirmed/construction metro staging file
    unconfirmed_json = output_dir / "unconfirmed_metro_stations.json"
    unconfirmed_json.write_text(json.dumps(unconfirmed_metro, indent=2), encoding="utf-8")

    # 3. Write Legal and Attribution Notice
    licence_file = generate_attribution_notice(output_dir / "ODBL_NOTICE.md")

    # 4. Write SQLite Room Database (transit_stops.db for app bundling and wakeme_transit.db)
    meta_entries = {
        "pack_version": pack_version,
        "generated_at": generated_at,
        "licence": "ODbL 1.0",
        "attribution": "© OpenStreetMap contributors",
        "metro_count": len(metro_stops),
        "bus_count": len(bus_stops)
    }
    transit_sqlite = write_sqlite(metro_stops, bus_stops, output_dir / "transit_stops.db", meta_entries)
    wakeme_sqlite = write_sqlite(metro_stops, bus_stops, output_dir / "wakeme_transit.db", meta_entries)

    # 5. Compute SHA-256 Checksums
    checksums = {
        "metro_stations.geojson": compute_sha256(metro_geojson),
        "bus_stops.geojson": compute_sha256(bus_geojson),
        "metro_stations.json": compute_sha256(metro_json),
        "bus_stops.json": compute_sha256(bus_json),
        "transit_stops.db": compute_sha256(transit_sqlite),
        "wakeme_transit.db": compute_sha256(wakeme_sqlite),
        "unconfirmed_metro_stations.json": compute_sha256(unconfirmed_json),
        "ODBL_NOTICE.md": compute_sha256(licence_file),
    }

    # 6. Write top-level metadata.json
    osm_ts = fetch_metadata.get("datasets", {}).get("metro", {}).get("osm_timestamp") or generated_at
    top_metadata = {
        "pack_version": pack_version,
        "generated_at_utc": generated_at,
        "osm_data_timestamp": osm_ts,
        "licence": "Open Database License (ODbL) 1.0",
        "attribution": "© OpenStreetMap contributors",
        "attribution_url": "https://www.openstreetmap.org/copyright",
        "counts": {
            "metro_operating_stations": len(metro_stops),
            "metro_unconfirmed_or_construction": len(unconfirmed_metro),
            "bus_stops_total": len(bus_stops),
            "bus_stops_with_names": sum(1 for b in bus_stops if "no_name" not in b.flags),
            "bus_stops_unnamed": sum(1 for b in bus_stops if "no_name" in b.flags),
            "bus_opposite_side_groups": len({b.group_id for b in bus_stops if b.group_id}),
            "bus_possible_duplicates": sum(1 for b in bus_stops if "possible_duplicate" in b.flags)
        },
        "checksums_sha256": checksums,
        "fetch_details": fetch_metadata.get("datasets", {})
    }

    metadata_path = output_dir / "metadata.json"
    metadata_path.write_text(json.dumps(top_metadata, indent=2), encoding="utf-8")
    logger.info("Package metadata written to %s", metadata_path)
    
    return top_metadata
