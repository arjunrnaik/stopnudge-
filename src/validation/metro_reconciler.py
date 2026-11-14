"""Reconciliation engine for Namma Metro stations against reference lists."""

import difflib
import math
from typing import Any, Dict, List, Set, Tuple

from src.cleaning.cleaner import haversine_distance, normalize_name
from src.cleaning.models import CleanStop

# Authoritative Reference List of Operational Stations by Line
# Purple Line (37 stations)
REFERENCE_PURPLE = [
    "Challaghatta", "Kengeri", "Kengeri Bus Terminal", "Pattanagere", "Jnanabharathi",
    "Rajarajeshwari Nagar", "Nayandahalli", "Mysuru Road", "Deepanjali Nagar", "Attiguppe",
    "Vijayanagar", "Hosahalli", "Magadi Road", "Krantivira Sangolli Rayanna Railway Station",
    "Nadaprabhu Kempegowda Station, Majestic", "Sir M. Visvesvaraya Stn., Central College",
    "Dr. B. R. Ambedkar Station, Vidhana Soudha", "Cubbon Park", "Mahatma Gandhi Road",
    "Trinity", "Halasuru", "Indira Nagar", "Swami Vivekananda Road", "Baiyappanahalli",
    "Benniganahalli", "Krishnarajapura", "Singayyanapalya", "Garudacharpalya", "Hoodi",
    "Seetharamapalya", "Kundalahalli", "Nallurhalli", "Sri Sathya Sai Hospital",
    "Pattandur Agrahara", "Kadugodi Tree Park", "Hopefarm Channasandra", "Whitefield (Kadugodi)"
]

# Green Line (32 stations, excluding 2 terminal Phase 2B extensions)
REFERENCE_GREEN = [
    "Madavara", "Chikkabidarakallu", "Manjunathanagara", "Nagasandra", "Dasarahalli",
    "Jalahalli", "Peenya Industry", "Peenya", "Goraguntepalya", "Yeshwanthpur",
    "Sandal Soap Factory", "Mahalakshmi", "Rajajinagar", "Mahakavi Kuvempu Road",
    "Srirampura", "Mantri Square Sampige Road", "Nadaprabhu Kempegowda Station, Majestic",
    "Chickpete", "Krishna Rajendra Market", "National College", "Lalbagh", "Southend Circle",
    "Jayanagar", "Rashtreeya Vidyalaya Road", "Banashankari", "Jaya Prakash Nagar",
    "Yelachenahalli", "Konanakunte Cross", "Doddakallasandra", "Vajarahalli",
    "Thalaghattapura", "Silk Institute"
]

# Yellow Line (16 stations, Phase 2)
REFERENCE_YELLOW = [
    "Rashtreeya Vidyalaya Road", "Ragigudda", "Jayadeva Hospital", "BTM Layout",
    "Central Silk Board", "Bommanahalli", "Hongasandra", "Kudlu Gate", "Singasandra",
    "Hosa Road", "Beratena Agrahara", "Konappana Agrahara", "Electronic City",
    "Huskur Road", "Hebbagodi", "Bommasandra"
]

# Pink Line (6 stations, Scheduled Phase 2 stretch - status unconfirmed)
REFERENCE_PINK_STRETCH = [
    "Kalena Agrahara", "Hulimavu", "IIM Bangalore", "JP Nagar 4th Phase",
    "Jayadeva Hospital", "Tavarekere"
]


def fuzzy_match_ratio(s1: str, s2: str) -> float:
    """Compute string similarity ratio between 0.0 and 1.0, accounting for sponsored/honorific prefixes."""
    norm1 = normalize_name(s1)
    norm2 = normalize_name(s2)
    
    if not norm1 or not norm2:
        return 0.0
        
    if norm1 == norm2:
        return 1.0
        
    # Check token containment (e.g. "Hebbagodi" in "Biocon Hebbagodi", "Hosahalli" in "Sri Balagangadharanatha Swamiji Station Hosahalli")
    tokens1 = set(norm1.split())
    tokens2 = set(norm2.split())
    if tokens1 and tokens2:
        if tokens1.issubset(tokens2) or tokens2.issubset(tokens1):
            return 0.92
        if norm1 in norm2 or norm2 in norm1:
            return 0.90

    return difflib.SequenceMatcher(None, norm1, norm2).ratio()


def reconcile_metro(osm_metro_stops: List[CleanStop]) -> Dict[str, Any]:
    """Reconcile cleaned OSM metro stations against reference dataset."""
    osm_by_norm = {normalize_name(s.name): s for s in osm_metro_stops}
    
    # Track matches
    matched_osm_ids: Set[str] = set()
    matches: List[Dict[str, Any]] = []
    missing_in_osm: List[Dict[str, str]] = []
    fuzzy_matches: List[Dict[str, Any]] = []
    
    # Combined reference set (Majestic and RV Road counted once)
    all_ref_stations = {}
    for st in REFERENCE_PURPLE:
        all_ref_stations.setdefault(st, set()).add("Purple")
    for st in REFERENCE_GREEN:
        all_ref_stations.setdefault(st, set()).add("Green")
    for st in REFERENCE_YELLOW:
        all_ref_stations.setdefault(st, set()).add("Yellow")

    for ref_name, lines in all_ref_stations.items():
        ref_norm = normalize_name(ref_name)
        
        # 1. Exact normalized match
        if ref_norm in osm_by_norm:
            matched_stop = osm_by_norm[ref_norm]
            matched_osm_ids.add(matched_stop.id)
            matches.append({
                "reference_name": ref_name,
                "osm_name": matched_stop.name,
                "osm_id": matched_stop.id,
                "match_type": "exact",
                "score": 1.0,
                "expected_lines": sorted(list(lines)),
                "osm_lines": matched_stop.metro_lines
            })
            continue

        # 2. Fuzzy match
        best_match = None
        best_score = 0.0
        for osm_norm, stop in osm_by_norm.items():
            score = fuzzy_match_ratio(ref_name, stop.name)
            if score > best_score:
                best_score = score
                best_match = stop

        if best_match and best_score >= 0.75:
            matched_osm_ids.add(best_match.id)
            match_entry = {
                "reference_name": ref_name,
                "osm_name": best_match.name,
                "osm_id": best_match.id,
                "match_type": "fuzzy",
                "score": round(best_score, 3),
                "expected_lines": sorted(list(lines)),
                "osm_lines": best_match.metro_lines
            }
            matches.append(match_entry)
            fuzzy_matches.append(match_entry)
        else:
            missing_in_osm.append({
                "reference_name": ref_name,
                "expected_lines": sorted(list(lines)),
                "best_candidate": best_match.name if best_match else None,
                "best_score": round(best_score, 3) if best_match else 0.0
            })

    # Unmatched OSM stations (extra in OSM)
    extra_in_osm = [s for s in osm_metro_stops if s.id not in matched_osm_ids]

    # Spacing checks along lines
    spacing_issues = []
    # Sort stations per line by lat/lon sequence and check consecutive distances
    for line_name, ref_list in [("Purple", REFERENCE_PURPLE), ("Green", REFERENCE_GREEN)]:
        line_stops = [s for s in osm_metro_stops if line_name in (s.metro_lines or [])]
        for i, s1 in enumerate(line_stops):
            for s2 in line_stops[i+1:]:
                dist = haversine_distance(s1.latitude, s1.longitude, s2.latitude, s2.longitude)
                if dist < 400.0:
                    spacing_issues.append({
                        "station_1": s1.name,
                        "station_2": s2.name,
                        "distance_meters": round(dist, 1),
                        "flag": "suspiciously_close (<400m)"
                    })

    return {
        "reference_unique_count": len(all_ref_stations),
        "osm_operating_count": len(osm_metro_stops),
        "matched_count": len(matches),
        "missing_count": len(missing_in_osm),
        "extra_count": len(extra_in_osm),
        "fuzzy_matches": fuzzy_matches,
        "missing_in_osm": missing_in_osm,
        "extra_in_osm": [
            {"id": s.id, "name": s.name, "lines": s.metro_lines, "coords": (s.latitude, s.longitude)}
            for s in extra_in_osm
        ],
        "spacing_flags": spacing_issues,
        "pink_line_status": {
            "stretch": REFERENCE_PINK_STRETCH,
            "status": "UNCONFIRMED",
            "reason": "BMRCL website has not confirmed commercial passenger opening; kept in unconfirmed staging"
        }
    }
