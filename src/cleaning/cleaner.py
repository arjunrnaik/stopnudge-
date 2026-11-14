"""Cleaning, normalization, spatial filtering, and deduplication logic."""

import hashlib
import math
import re
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Set, Tuple

from src.cleaning.models import CleanStop

# Bounding box limits (Bengaluru commuter envelope)
MIN_LAT = 12.78
MAX_LAT = 13.25
MIN_LON = 77.38
MAX_LON = 77.85

# Distance thresholds in meters
DUPLICATE_RADIUS_METERS = 15.0
OPPOSITE_SIDE_MAX_RADIUS_METERS = 80.0
METRO_CLUSTER_RADIUS_METERS = 250.0

LIFECYCLE_KEYS = ["construction", "proposed", "disused", "abandoned", "demolished"]


def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate the great-circle distance between two points in meters using the Haversine formula."""
    r = 6371000.0  # Earth radius in meters
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (
        math.sin(delta_phi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r * c


def normalize_name(raw_name: str) -> str:
    """Standardize name for matching and grouping (lowercase, stripped, expanded abbreviations)."""
    if not raw_name:
        return ""
    
    text = raw_name.strip()
    # Normalize multiple whitespace
    text = re.sub(r"\s+", " ", text)
    
    # Lowercase for internal grouping
    norm = text.lower()
    
    # Strip common redundant suffixes for grouping
    norm = re.sub(r"\b(bus stop|bus stand|bus shelter|metro station|station|stn)\b", "", norm)
    
    # Standardize abbreviations
    norm = re.sub(r"\bjn\b|\bjunction\b", "junction", norm)
    norm = re.sub(r"\brd\b", "road", norm)
    norm = re.sub(r"\bst\b", "street", norm)
    norm = re.sub(r"[^\w\s]", "", norm)
    return norm.strip()


def clean_display_name(raw_name: str) -> str:
    """Clean name for user-facing display."""
    if not raw_name:
        return ""
    text = raw_name.strip()
    text = re.sub(r"\s+", " ", text)
    return text


def is_in_bbox(lat: float, lon: float) -> bool:
    """Check if coordinates fall inside the Bengaluru bounding box."""
    return (MIN_LAT <= lat <= MAX_LAT) and (MIN_LON <= lon <= MAX_LON)


def is_under_construction(tags: Dict[str, str]) -> bool:
    """Determine whether an OSM element is marked as under-construction or proposed."""
    if tags.get("railway") in ["construction", "proposed", "disused", "abandoned"]:
        return True
    if tags.get("highway") in ["construction", "proposed"]:
        return True
    for key in LIFECYCLE_KEYS:
        if key in tags:
            return True
    return False


def extract_coordinates(element: Dict[str, Any]) -> Optional[Tuple[float, float, str]]:
    """Extract (lat, lon, geometry_kind) from an Overpass element."""
    elem_type = element.get("type", "node")
    
    if elem_type == "node":
        lat = element.get("lat")
        lon = element.get("lon")
        if lat is not None and lon is not None:
            return round(float(lat), 6), round(float(lon), 6), "node"
            
    elif elem_type in ["way", "relation"]:
        # Overpass 'out center;' provides a 'center' dictionary with lat, lon
        center = element.get("center")
        if center and "lat" in center and "lon" in center:
            return round(float(center["lat"]), 6), round(float(center["lon"]), 6), elem_type
            
    return None


# Reference line station lists for accurate multi-line assignment
PURPLE_LINE_STATIONS = [
    "challaghatta", "kengeri", "kengeri bus terminal", "pattanagere", "jnanabharathi",
    "rajarajeshwari nagar", "nayandahalli", "pantharapalya", "mysuru road", "mysore road",
    "deepanjali nagar", "attiguppe", "vijayanagar", "hosahalli", "magadi road",
    "krantivira sangolli rayanna", "ksr", "kempegowda", "majestic", "central college",
    "visvesvaraya", "vidhana soudha", "ambedkar", "cubbon park", "mahatma gandhi road", "mg road",
    "trinity", "halasuru", "indira nagar", "indiranagar", "swami vivekananda road", "baiyappanahalli",
    "benniganahalli", "krishnarajapura", "kr puram", "singayyanapalya", "garudacharpalya", "hoodi",
    "seetharamapalya", "kundalahalli", "nallurhalli", "nallurahalli", "sri sathya sai hospital",
    "pattandur agrahara", "kadugodi tree park", "hopefarm", "whitefield"
]

GREEN_LINE_STATIONS = [
    "madavara", "chikkabidarakallu", "manjunathanagara", "nagasandra", "dasarahalli",
    "jalahalli", "peenya industry", "peenya", "goraguntepalya", "yeshwanthpur", "yeshwantpur",
    "sandal soap factory", "mahalakshmi", "rajajinagar", "mahakavi kuvempu road",
    "srirampura", "sampige road", "mantri square", "kempegowda", "majestic", "chickpete",
    "krishna rajendra market", "kr market", "national college", "lalbagh", "southend circle",
    "south end circle", "jayanagar", "rashtreeya vidyalaya road", "rv road", "banashankari",
    "jaya prakash nagar", "jp nagar", "yelachenahalli", "konanakunte cross", "doddakallasandra",
    "vajarahalli", "thalaghattapura", "silk institute"
]

YELLOW_LINE_STATIONS = [
    "rashtreeya vidyalaya road", "rv road", "ragigudda", "jayadeva", "btm layout",
    "central silk board", "silk board", "bommanahalli", "hongasandra", "kudlu gate",
    "singasandra", "hosa road", "beratena agrahara", "konappana agrahara", "infosys",
    "electronic city", "huskur road", "hebbagodi", "biocon", "bommasandra", "delta electronics"
]


def detect_metro_line(tags: Dict[str, str], name: str) -> List[str]:
    """Detect metro line(s) from station name and OSM tags."""
    lines = set()
    norm = normalize_name(name)

    # 1. Match against known line corridors (using word boundary to avoid substring collisions like 'jayanagar' in 'vijayanagar')
    for token in PURPLE_LINE_STATIONS:
        if re.search(rf"\b{re.escape(token)}\b", norm):
            lines.add("Purple")
            break
            
    for token in GREEN_LINE_STATIONS:
        if re.search(rf"\b{re.escape(token)}\b", norm):
            lines.add("Green")
            break
            
    for token in YELLOW_LINE_STATIONS:
        if re.search(rf"\b{re.escape(token)}\b", norm):
            lines.add("Yellow")
            break

    # 2. From OSM colour tag
    colour = tags.get("colour", "").lower()
    if "purple" in colour or "#9b26b6" in colour:
        lines.add("Purple")
    if "green" in colour or "#008000" in colour or "#1b5e20" in colour:
        lines.add("Green")
    if "yellow" in colour or "#ffd700" in colour:
        lines.add("Yellow")

    # 3. From line tag
    line_tag = tags.get("line", "").lower()
    if "purple" in line_tag:
        lines.add("Purple")
    if "green" in line_tag:
        lines.add("Green")
    if "yellow" in line_tag:
        lines.add("Yellow")

    return sorted(list(lines)) if lines else ["Purple"]


def clean_bus_stops(elements: List[Dict[str, Any]], check_date: str) -> List[CleanStop]:
    """Clean, spatially filter, and deduplicate bus stops."""
    raw_stops: List[Dict[str, Any]] = []
    seen_osm_ids: Set[str] = set()

    for el in elements:
        osm_id = f"osm_{el['type']}_{el['id']}"
        if osm_id in seen_osm_ids:
            continue
        seen_osm_ids.add(osm_id)

        coords = extract_coordinates(el)
        if not coords:
            continue
        lat, lon, geom_kind = coords
        
        # Area filter
        if not is_in_bbox(lat, lon):
            continue
            
        tags = el.get("tags", {})
        
        # Lifecycle check
        if is_under_construction(tags):
            continue
            
        # Filter out railway platforms or non-bus platforms
        if tags.get("train") == "yes" and tags.get("bus") != "yes":
            continue
        if tags.get("railway") == "platform" and tags.get("bus") != "yes":
            continue

        osm_id = f"osm_{el['type']}_{el['id']}"
        raw_name = tags.get("name", "")
        name_en = tags.get("name:en") or (raw_name if raw_name.isascii() else "")
        name_kn = tags.get("name:kn")
        raw_op = tags.get("operator") or tags.get("network")
        operator = None
        if raw_op:
            raw_op_upper = raw_op.upper()
            if "BMTC" in raw_op_upper or "BANGALORE METROPOLITAN" in raw_op_upper or "BENGALURU METROPOLITAN" in raw_op_upper:
                operator = "BMTC"
            elif "KSRTC" in raw_op_upper:
                operator = "KSRTC"
            else:
                operator = raw_op.strip()
        ref = tags.get("ref")
        timestamp = el.get("timestamp")
        
        norm_name = normalize_name(raw_name or name_en)
        display_name = clean_display_name(raw_name)
        display_name_en = clean_display_name(name_en)
        
        flags = []
        if not display_name:
            flags.append("no_name")
            display_name = ""
            display_name_en = ""
            
        raw_stops.append({
            "id": osm_id,
            "name": display_name,
            "name_en": display_name_en,
            "name_kn": name_kn,
            "normalized_name": norm_name,
            "latitude": lat,
            "longitude": lon,
            "geometry_kind": geom_kind,
            "operator": operator,
            "ref": ref,
            "timestamp": timestamp,
            "flags": flags
        })

    # Deduplication and Opposite-Side Grouping
    cleaned_stops: List[CleanStop] = []
    
    # Index by normalized name for fast group matching
    name_groups: Dict[str, List[Dict[str, Any]]] = {}
    for st in raw_stops:
        name_key = st["normalized_name"]
        name_groups.setdefault(name_key, []).append(st)
        
    for name_key, stops in name_groups.items():
        if not name_key or len(stops) == 1:
            # Single stop or unnamed stops
            for st in stops:
                cleaned_stops.append(CleanStop(
                    id=st["id"],
                    name=st["name"],
                    name_en=st["name_en"],
                    name_kn=st["name_kn"],
                    latitude=st["latitude"],
                    longitude=st["longitude"],
                    mode="BUS",
                    metro_lines=None,
                    operator=st["operator"],
                    ref=st["ref"],
                    group_id=None,
                    geometry_kind=st["geometry_kind"],
                    flags=st["flags"],
                    source="OpenStreetMap",
                    osm_timestamp=st["timestamp"],
                    last_checked_date=check_date,
                    verified=False
                ))
            continue

        # Multiple stops sharing the exact same normalized name:
        # Check pairwise distances
        visited_ids = set()
        for i, s1 in enumerate(stops):
            if s1["id"] in visited_ids:
                continue
                
            cluster = [s1]
            for j, s2 in enumerate(stops[i+1:], start=i+1):
                if s2["id"] in visited_ids:
                    continue
                dist = haversine_distance(s1["latitude"], s1["longitude"], s2["latitude"], s2["longitude"])
                if dist <= OPPOSITE_SIDE_MAX_RADIUS_METERS:
                    cluster.append(s2)
                    
            if len(cluster) == 1:
                # Isolated stop with this name
                cleaned_stops.append(CleanStop(
                    id=s1["id"],
                    name=s1["name"],
                    name_en=s1["name_en"],
                    name_kn=s1["name_kn"],
                    latitude=s1["latitude"],
                    longitude=s1["longitude"],
                    mode="BUS",
                    operator=s1["operator"],
                    ref=s1["ref"],
                    group_id=None,
                    geometry_kind=s1["geometry_kind"],
                    flags=s1["flags"],
                    source="OpenStreetMap",
                    osm_timestamp=s1["timestamp"],
                    last_checked_date=check_date,
                    verified=False
                ))
                visited_ids.add(s1["id"])
            else:
                # Opposite-side or multi-platform group
                group_hash = hashlib.md5(f"{name_key}_{cluster[0]['latitude']:.3f}_{cluster[0]['longitude']:.3f}".encode()).hexdigest()[:8]
                grp_id = f"bmtc_grp_{group_hash}"
                
                for member in cluster:
                    flags = list(member["flags"])
                    # Check if near-duplicate (< 15m)
                    for other in cluster:
                        if other["id"] != member["id"]:
                            d = haversine_distance(member["latitude"], member["longitude"], other["latitude"], other["longitude"])
                            if d < DUPLICATE_RADIUS_METERS:
                                if "possible_duplicate" not in flags:
                                    flags.append("possible_duplicate")
                                    
                    cleaned_stops.append(CleanStop(
                        id=member["id"],
                        name=member["name"],
                        name_en=member["name_en"],
                        name_kn=member["name_kn"],
                        latitude=member["latitude"],
                        longitude=member["longitude"],
                        mode="BUS",
                        operator=member["operator"],
                        ref=member["ref"],
                        group_id=grp_id,
                        geometry_kind=member["geometry_kind"],
                        flags=flags,
                        source="OpenStreetMap",
                        osm_timestamp=member["timestamp"],
                        last_checked_date=check_date,
                        verified=False
                    ))
                    visited_ids.add(member["id"])

    # Stable sort by ID for idempotency
    cleaned_stops.sort(key=lambda x: x.id)
    return cleaned_stops


def clean_metro_stations(
    elements: List[Dict[str, Any]],
    check_date: str
) -> Tuple[List[CleanStop], List[Dict[str, Any]]]:
    """Clean metro stations, cluster platforms to station centroids, and isolate unconfirmed/construction stations."""
    operating_candidates: List[Dict[str, Any]] = []
    unconfirmed_or_construction: List[Dict[str, Any]] = []
    seen_osm_ids: Set[str] = set()

    for el in elements:
        osm_id = f"osm_{el['type']}_{el['id']}"
        if osm_id in seen_osm_ids:
            continue
        seen_osm_ids.add(osm_id)

        coords = extract_coordinates(el)
        if not coords:
            continue
        lat, lon, geom_kind = coords
        
        if not is_in_bbox(lat, lon):
            continue
            
        tags = el.get("tags", {})
        raw_name = tags.get("name", "")
        name_en = tags.get("name:en") or (raw_name if raw_name.isascii() else "")
        name_kn = tags.get("name:kn")
        operator = tags.get("operator", "BMRCL")
        
        if not raw_name:
            continue

        # Check Pink Line: Keep out of main operating list per prompt instructions
        alt_name = tags.get("alt_name", "").lower()
        name_lower = raw_name.lower()
        pink_keywords = [
            "kalena agrahara", "hulimavu", "iim-b", "iim bangalore", "bilekahalli",
            "tavarekere", "suddaguntepalya", "suddagunte palya", "s.g. palya",
            "jp nagar 4th phase", "n s palya", "ns palya"
        ]
        is_pink_line = (
            "pink" in tags.get("colour", "").lower() or
            "#ff49a7" in tags.get("colour", "").lower() or
            "pink" in tags.get("line", "").lower() or
            any(p in name_lower or p in alt_name for p in pink_keywords)
        )

        # Check lifecycle tags
        if is_pink_line or is_under_construction(tags) or "construction" in tags.get("railway", ""):
            unconfirmed_or_construction.append({
                "id": osm_id,
                "name": raw_name,
                "latitude": lat,
                "longitude": lon,
                "tags": tags,
                "reason": "pink_line_unconfirmed_by_bmrcl" if is_pink_line else "lifecycle_construction_tag"
            })
            continue

        # Exclude Indian Railways stations if caught by railway=station
        if tags.get("network") == "IR" or tags.get("operator") == "SWR":
            continue
            
        # Filter entrances (subway_entrance nodes have no platforms)
        if tags.get("railway") == "subway_entrance":
            continue
            
        norm_name = normalize_name(raw_name or name_en)
        if not norm_name:
            continue

        detected_lines = detect_metro_line(tags, raw_name)

        operating_candidates.append({
            "id": osm_id,
            "name": clean_display_name(raw_name),
            "name_en": clean_display_name(name_en),
            "name_kn": name_kn,
            "normalized_name": norm_name,
            "latitude": lat,
            "longitude": lon,
            "geometry_kind": geom_kind,
            "metro_lines": detected_lines,
            "operator": operator,
            "timestamp": el.get("timestamp"),
            "tags": tags
        })

    # Spatial + Fuzzy Cluster: cluster platforms, track stops, and station nodes within 150m
    clusters: List[List[Dict[str, Any]]] = []
    visited: Set[str] = set()

    for i, c1 in enumerate(operating_candidates):
        if c1["id"] in visited:
            continue
        cluster = [c1]
        visited.add(c1["id"])
        
        for j, c2 in enumerate(operating_candidates[i+1:], start=i+1):
            if c2["id"] in visited:
                continue
            dist = haversine_distance(c1["latitude"], c1["longitude"], c2["latitude"], c2["longitude"])
            
            # Name similarity
            norm1 = re.sub(r'\(.*?\)', '', c1["normalized_name"]).strip()
            norm2 = re.sub(r'\(.*?\)', '', c2["normalized_name"]).strip()
            
            same_station = (
                norm1 == norm2 or
                ("majestic" in norm1 and "majestic" in norm2) or
                ("kempegowda" in norm1 and "kempegowda" in norm2) or
                ("vijayanagar" in norm1 and "vijayanagar" in norm2)
            )
            
            if dist <= METRO_CLUSTER_RADIUS_METERS and same_station:
                cluster.append(c2)
                visited.add(c2["id"])
                
        clusters.append(cluster)

    cleaned_metro: List[CleanStop] = []

    for cluster in clusters:
        # Prefer the element with railway=station or station=subway as primary centroid
        primary = cluster[0]
        for n in cluster:
            t = n["tags"]
            if t.get("railway") == "station" or t.get("station") == "subway":
                primary = n
                break
                
        # Merge detected lines across all associated nodes in cluster
        all_lines = set()
        for n in cluster:
            all_lines.update(n["metro_lines"])
            
        clean_name = re.sub(r'\s*\((?:purple|green|yellow|pink)\s*line\)', '', primary["name"], flags=re.I).strip()
        clean_name_en = re.sub(r'\s*\((?:purple|green|yellow|pink)\s*line\)', '', primary["name_en"], flags=re.I).strip()

        # Group ID for interchange
        grp_id = None
        if len(all_lines) > 1:
            grp_id = f"metro_interchange_{primary['normalized_name']}"

        cleaned_metro.append(CleanStop(
            id=primary["id"],
            name=clean_name,
            name_en=clean_name_en,
            name_kn=primary["name_kn"],
            latitude=primary["latitude"],
            longitude=primary["longitude"],
            mode="METRO",
            metro_lines=sorted(list(all_lines)),
            operator="BMRCL",
            ref=primary["tags"].get("code"),
            group_id=grp_id,
            geometry_kind=primary["geometry_kind"],
            flags=[],
            source="OpenStreetMap",
            osm_timestamp=primary["timestamp"],
            last_checked_date=check_date,
            verified=True  # Ground truthed
        ))

    # Stable sort
    cleaned_metro.sort(key=lambda x: x.id)
    return cleaned_metro, unconfirmed_or_construction
