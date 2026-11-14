"""GeoJSON writer for CleanStop objects (RFC 7946 compliant)."""

import json
from pathlib import Path
from typing import Any, Dict, List

from src.cleaning.models import CleanStop


def write_geojson(stops: List[CleanStop], output_path: Path) -> Path:
    """Serialize CleanStop records into RFC 7946 FeatureCollection GeoJSON."""
    features = []
    for s in stops:
        properties = {
            "id": s.id,
            "name": s.name,
            "name_en": s.name_en,
            "name_kn": s.name_kn,
            "mode": s.mode,
            "operator": s.operator,
            "ref": s.ref,
            "group_id": s.group_id,
            "geometry_kind": s.geometry_kind,
            "flags": s.flags,
            "source": s.source,
            "osm_timestamp": s.osm_timestamp,
            "last_checked_date": s.last_checked_date,
            "verified": s.verified,
        }
        if s.metro_lines is not None:
            properties["metro_lines"] = s.metro_lines

        feature = {
            "type": "Feature",
            "geometry": {
                "type": "Point",
                "coordinates": [s.longitude, s.latitude]  # GeoJSON is [lon, lat]
            },
            "properties": properties
        }
        features.append(feature)

    feature_collection = {
        "type": "FeatureCollection",
        "features": features
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(feature_collection, ensure_ascii=False, indent=2), encoding="utf-8")
    return output_path
