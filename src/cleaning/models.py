"""Data models for cleaned transit stops."""

from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class CleanStop:
    """Represents a clean, normalized transit stop ready for mobile and GIS export."""
    id: str  # Stable ID: osm_node_<id> or osm_way_<id>
    name: str
    name_en: str
    name_kn: Optional[str]
    latitude: float
    longitude: float
    mode: str  # "METRO" or "BUS"
    metro_lines: Optional[List[str]] = None
    operator: Optional[str] = None
    ref: Optional[str] = None
    group_id: Optional[str] = None
    geometry_kind: str = "node"  # "node", "way", "relation"
    flags: List[str] = field(default_factory=list)
    source: str = "OpenStreetMap"
    osm_timestamp: Optional[str] = None
    last_checked_date: str = ""
    verified: bool = False
