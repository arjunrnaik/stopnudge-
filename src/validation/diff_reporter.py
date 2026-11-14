"""Diff engine comparing a new transit pack against a previous version."""

import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Set

from src.cleaning.cleaner import haversine_distance
from src.cleaning.models import CleanStop


def compute_diff(
    current_stops: List[CleanStop],
    previous_pack_dir: Optional[Path] = None
) -> Dict[str, Any]:
    """Compute diff between current clean stops and previous pack."""
    if not previous_pack_dir or not (previous_pack_dir / "wakeme_transit.db").exists():
        return {
            "status": "INITIAL_PACK",
            "message": "No previous pack found to compare against. This is the baseline pack.",
            "added": len(current_stops),
            "removed": 0,
            "moved_over_25m": [],
            "renamed": []
        }

    # Load previous stops from JSON if available
    prev_json_path = previous_pack_dir / f"{current_stops[0].mode.lower()}_stations.json"
    if not prev_json_path.exists():
        prev_json_path = previous_pack_dir / f"{current_stops[0].mode.lower()}_stops.json"
        
    if not prev_json_path.exists():
        return {
            "status": "NO_PREVIOUS_FILE",
            "message": f"Previous file {prev_json_path} not found.",
            "added": len(current_stops),
            "removed": 0
        }

    prev_data = json.loads(prev_json_path.read_text(encoding="utf-8"))
    prev_by_id = {item["id"]: item for item in prev_data}
    curr_by_id = {s.id: s for s in current_stops}

    added = [curr_by_id[s_id] for s_id in curr_by_id if s_id not in prev_by_id]
    removed = [prev_by_id[s_id] for s_id in prev_by_id if s_id not in curr_by_id]
    
    moved_over_25m = []
    renamed = []

    for s_id, curr_stop in curr_by_id.items():
        if s_id in prev_by_id:
            prev_stop = prev_by_id[s_id]
            dist = haversine_distance(curr_stop.latitude, curr_stop.longitude,
                                      prev_stop["latitude"], prev_stop["longitude"])
            if dist > 25.0:
                moved_over_25m.append({
                    "id": s_id,
                    "name": curr_stop.name,
                    "distance_moved_meters": round(dist, 1),
                    "old_coords": (prev_stop["latitude"], prev_stop["longitude"]),
                    "new_coords": (curr_stop.latitude, curr_stop.longitude)
                })
            if curr_stop.name != prev_stop["name"]:
                renamed.append({
                    "id": s_id,
                    "old_name": prev_stop["name"],
                    "new_name": curr_stop.name
                })

    return {
        "status": "DIFF_COMPUTED",
        "total_current": len(current_stops),
        "total_previous": len(prev_data),
        "added_count": len(added),
        "removed_count": len(removed),
        "moved_count": len(moved_over_25m),
        "renamed_count": len(renamed),
        "moved_over_25m": moved_over_25m[:10],
        "renamed": renamed[:10]
    }
