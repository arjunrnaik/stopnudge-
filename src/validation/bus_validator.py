"""Validation, spatial density analysis, and spot-check sampling for bus stops."""

import csv
import random
from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, List

from src.cleaning.models import CleanStop


def validate_and_sample_bus_stops(
    stops: List[CleanStop],
    sample_output_csv: Path,
    seed: int = 42
) -> Dict[str, Any]:
    """Analyze bus stops, compute density grid, and extract reproducible 50-stop spot-check sample."""
    total = len(stops)
    named = sum(1 for s in stops if "no_name" not in s.flags and s.name)
    unnamed = sum(1 for s in stops if "no_name" in s.flags or not s.name)
    with_operator = sum(1 for s in stops if s.operator and s.operator != "None")
    with_ref = sum(1 for s in stops if s.ref)
    near_duplicates = sum(1 for s in stops if "possible_duplicate" in s.flags)
    grouped = sum(1 for s in stops if s.group_id)

    # 1. Spatial Grid Density (approx. 0.01 deg ~= 1.1 km grid cells)
    grid_bins = defaultdict(list)
    for s in stops:
        # Bin by lat/lon rounded to 2 decimal places (~1.1 km)
        lat_bin = round(s.latitude, 2)
        lon_bin = round(s.longitude, 2)
        grid_bins[(lat_bin, lon_bin)].append(s)

    top_dense_clusters = sorted(
        [
            {"center_lat": k[0], "center_lon": k[1], "stop_count": len(v), "sample_names": [x.name for x in v[:3] if x.name]}
            for k, v in grid_bins.items()
        ],
        key=lambda x: x["stop_count"],
        reverse=True
    )[:5]

    # 2. Stratified Random Sampling of 50 Stops for Human Spot-Check
    # Stratify by 5 sectors around Majestic center (12.975, 77.575):
    # Central (within 3km), North, South, East, West
    sectors = {
        "central": [],
        "north": [],
        "south": [],
        "east": [],
        "west": []
    }

    center_lat, center_lon = 12.975, 77.575
    for s in stops:
        d_lat = s.latitude - center_lat
        d_lon = s.longitude - center_lon
        dist_deg = (d_lat**2 + d_lon**2)**0.5
        
        if dist_deg <= 0.03:  # ~3.3 km
            sectors["central"].append(s)
        elif abs(d_lat) > abs(d_lon):
            if d_lat > 0:
                sectors["north"].append(s)
            else:
                sectors["south"].append(s)
        else:
            if d_lon > 0:
                sectors["east"].append(s)
            else:
                sectors["west"].append(s)

    rng = random.Random(seed)
    sample: List[CleanStop] = []
    
    # 10 stops from each of the 5 sectors = exactly 50 stops
    for sec_name, sec_stops in sectors.items():
        sample_size = min(10, len(sec_stops))
        sample.extend(rng.sample(sec_stops, sample_size))

    # If any sector had fewer than 10, fill to 50
    if len(sample) < 50:
        remaining_pool = [s for s in stops if s not in sample]
        sample.extend(rng.sample(remaining_pool, 50 - len(sample)))

    # Sort sample by ID for stable output
    sample.sort(key=lambda x: x.id)

    # 3. Write Spot-Check CSV
    sample_output_csv.parent.mkdir(parents=True, exist_ok=True)
    with open(sample_output_csv, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "stop_id",
            "name",
            "latitude",
            "longitude",
            "geometry_kind",
            "group_id",
            "osm_link",
            "map_view_url",
            "exists_yes_no",
            "name_correct_yes_no",
            "position_within_50m_yes_no",
            "notes"
        ])
        for s in sample:
            clean_id = s.id.replace("osm_node_", "").replace("osm_way_", "").replace("osm_relation_", "")
            osm_type = "node" if "node" in s.id else ("way" if "way" in s.id else "relation")
            osm_url = f"https://www.openstreetmap.org/{osm_type}/{clean_id}"
            map_url = f"https://www.google.com/maps?q={s.latitude},{s.longitude}"
            
            writer.writerow([
                s.id,
                s.name,
                f"{s.latitude:.6f}",
                f"{s.longitude:.6f}",
                s.geometry_kind,
                s.group_id or "",
                osm_url,
                map_url,
                "",  # Blank for human reviewer
                "",  # Blank for human reviewer
                "",  # Blank for human reviewer
                ""   # Blank for human reviewer
            ])

    return {
        "total_stops": total,
        "named_stops": named,
        "unnamed_stops": unnamed,
        "with_operator": with_operator,
        "with_ref": with_ref,
        "near_duplicate_clusters": near_duplicates,
        "grouped_stops": grouped,
        "top_dense_clusters": top_dense_clusters,
        "opencity_reference_context": {
            "quoted_figure": 9600,
            "publisher": "BMTC (via OpenCity)",
            "caveat": (
                "OpenCity page describes ~9,600 stops with unknown age and no licence. "
                "This figure is a rough order-of-magnitude yardstick only and is NOT an authoritative denominator. "
                "The discrepancy between OSM (~5,300) and 9,600 may stem from unmapped stops, duplicates in BMTC's list, or both."
            )
        },
        "spot_check_sample_size": len(sample),
        "spot_check_csv_path": str(sample_output_csv)
    }
