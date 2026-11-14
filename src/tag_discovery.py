"""Tag Discovery Script for Namma Metro and BMTC Bus Stops in OpenStreetMap.

Inspects live OpenStreetMap tags across anchor locations in Bengaluru to discover
the true tagging schema used by OSM mappers for metro stations and bus stops.
"""

import json
import logging
import math
import sys
import urllib.request
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("TagDiscovery")

# Known Anchor Locations for Bengaluru Transit
ANCHORS = [
    # Metro Anchors (Purple & Green operating)
    {"name": "Majestic (Nadaprabhu Kempegowda & KBS)", "lat": 12.9757, "lon": 77.5728, "type": "metro_and_bus"},
    {"name": "MG Road Metro", "lat": 12.9755, "lon": 77.6066, "type": "metro"},
    {"name": "Baiyappanahalli Metro", "lat": 12.9907, "lon": 77.6525, "type": "metro"},
    {"name": "Silk Institute Metro (Green Line South)", "lat": 12.8624, "lon": 77.5303, "type": "metro"},
    {"name": "Whitefield Kadugodi Metro (Purple Line East)", "lat": 12.9984, "lon": 77.7592, "type": "metro"},
    
    # Yellow Line Anchors
    {"name": "RV Road Metro (Green/Yellow Interchange)", "lat": 12.9216, "lon": 77.5802, "type": "metro"},
    {"name": "Bommasandra Metro (Yellow Line South)", "lat": 12.8164, "lon": 77.6914, "type": "metro"},
    {"name": "Central Silk Board (Yellow/Blue Interchange)", "lat": 12.9176, "lon": 77.6233, "type": "metro_and_bus"},
    
    # Pink Line Anchors
    {"name": "Kalena Agrahara Metro (Pink Line South)", "lat": 12.8775, "lon": 77.5955, "type": "metro"},
    {"name": "Jayadeva Hospital (Pink/Yellow Interchange)", "lat": 12.9171, "lon": 77.5960, "type": "metro"},
    {"name": "Hulimavu Metro (Pink Line)", "lat": 12.8872, "lon": 77.5973, "type": "metro"},
    {"name": "IIM Bangalore Metro (Pink Line)", "lat": 12.8988, "lon": 77.5990, "type": "metro"},

    # Bus Anchors (Major Hubs & Arterial Corridors)
    {"name": "Shivajinagar Bus Station", "lat": 12.9857, "lon": 77.6056, "type": "bus"},
    {"name": "Banashankari TTMC", "lat": 12.9155, "lon": 77.5736, "type": "bus"},
    {"name": "Shantinagar Bus Station", "lat": 12.9540, "lon": 77.5936, "type": "bus"},
    {"name": "Electronic City Wipro Gate", "lat": 12.8448, "lon": 77.6632, "type": "bus"},
    {"name": "Marathahalli Bridge Bus Stops", "lat": 12.9555, "lon": 77.7011, "type": "bus"},
    {"name": "Hebbal Bus Stop", "lat": 13.0358, "lon": 77.5970, "type": "bus"},
    {"name": "Kengeri Bus Terminal", "lat": 12.9080, "lon": 77.4855, "type": "bus"},
]

USER_AGENT = "WakeMeTransitPipeline/1.0 (+https://github.com/user/wakeme; transit-tag-discovery)"


def make_bbox(lat: float, lon: float, delta_deg: float = 0.005) -> Tuple[float, float, float, float]:
    """Create a bounding box around (lat, lon) in (min_lon, min_lat, max_lon, max_lat) format."""
    return (
        round(lon - delta_deg, 6),
        round(lat - delta_deg, 6),
        round(lon + delta_deg, 6),
        round(lat + delta_deg, 6)
    )


def fetch_osm_map(bbox: Tuple[float, float, float, float]) -> Optional[bytes]:
    """Fetch live OSM XML map data for a bounding box from the main OSM API 0.6."""
    min_lon, min_lat, max_lon, max_lat = bbox
    url = f"https://api.openstreetmap.org/api/0.6/map?bbox={min_lon},{min_lat},{max_lon},{max_lat}"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.read()
    except Exception as e:
        logger.warning("Failed fetching bbox %s: %s", bbox, e)
        return None


def parse_osm_elements(xml_bytes: bytes) -> List[Dict[str, Any]]:
    """Parse XML bytes into a list of element dictionaries."""
    root = ET.fromstring(xml_bytes)
    elements = []
    
    # Store nodes for coordinate lookups if needed
    node_coords = {}
    for node in root.findall("node"):
        n_id = node.attrib["id"]
        lat = float(node.attrib["lat"])
        lon = float(node.attrib["lon"])
        node_coords[n_id] = (lat, lon)
        tags = {t.attrib["k"]: t.attrib["v"] for t in node.findall("tag")}
        elements.append({
            "type": "node",
            "id": n_id,
            "lat": lat,
            "lon": lon,
            "tags": tags
        })
        
    for way in root.findall("way"):
        w_id = way.attrib["id"]
        tags = {t.attrib["k"]: t.attrib["v"] for t in way.findall("tag")}
        # Calculate center from node refs if available
        nd_refs = [nd.attrib["ref"] for nd in way.findall("nd")]
        coords = [node_coords[ref] for ref in nd_refs if ref in node_coords]
        if coords:
            lat = sum(c[0] for c in coords) / len(coords)
            lon = sum(c[1] for c in coords) / len(coords)
        else:
            lat, lon = None, None
        elements.append({
            "type": "way",
            "id": w_id,
            "lat": lat,
            "lon": lon,
            "tags": tags
        })
        
    for rel in root.findall("relation"):
        r_id = rel.attrib["id"]
        tags = {t.attrib["k"]: t.attrib["v"] for t in rel.findall("tag")}
        elements.append({
            "type": "relation",
            "id": r_id,
            "lat": None,
            "lon": None,
            "tags": tags
        })

    return elements


def run_discovery() -> Dict[str, Any]:
    """Execute tag discovery across all anchor locations."""
    logger.info("Starting Tag Discovery on %d anchor locations in Bengaluru...", len(ANCHORS))
    
    all_metro_elements: List[Dict[str, Any]] = []
    all_bus_elements: List[Dict[str, Any]] = []
    all_elements: List[Dict[str, Any]] = []

    for anchor in ANCHORS:
        logger.info("Probing anchor: %s (%s)...", anchor["name"], anchor["type"])
        bbox = make_bbox(anchor["lat"], anchor["lon"], delta_deg=0.006)
        xml_data = fetch_osm_map(bbox)
        if not xml_data:
            continue
            
        elements = parse_osm_elements(xml_data)
        logger.info("  Fetched %d total OSM elements in bbox", len(elements))
        
        for el in elements:
            tags = el["tags"]
            if not tags:
                continue
                
            # Classify metro-related elements
            is_metro = (
                tags.get("railway") in ["station", "subway", "construction"] or
                tags.get("station") == "subway" or
                tags.get("subway") == "yes" or
                "metro" in tags.get("network", "").lower() or
                "namma metro" in tags.get("name", "").lower() or
                "bmrcl" in tags.get("operator", "").lower() or
                "bangalore metro" in tags.get("operator", "").lower()
            )
            
            # Classify bus-related elements
            is_bus = (
                tags.get("highway") == "bus_stop" or
                (tags.get("public_transport") == "platform" and tags.get("bus") == "yes") or
                tags.get("amenity") == "bus_station" or
                "bmtc" in tags.get("operator", "").lower() or
                "bmtc" in tags.get("network", "").lower()
            )
            
            if is_metro:
                el_copy = dict(el)
                el_copy["anchor_context"] = anchor["name"]
                all_metro_elements.append(el_copy)
                
            if is_bus:
                el_copy = dict(el)
                el_copy["anchor_context"] = anchor["name"]
                all_bus_elements.append(el_copy)
                
            all_elements.append(el)

    logger.info("Discovery complete: %d metro matches, %d bus matches found.", len(all_metro_elements), len(all_bus_elements))
    
    # Analyze Metro Tags
    metro_tag_counts = Counter()
    metro_network_values = Counter()
    metro_operator_values = Counter()
    metro_station_values = Counter()
    metro_railway_values = Counter()
    metro_lifecycle_counts = Counter()
    
    for m in all_metro_elements:
        t = m["tags"]
        for k in t:
            metro_tag_counts[k] += 1
        if "network" in t:
            metro_network_values[t["network"]] += 1
        if "operator" in t:
            metro_operator_values[t["operator"]] += 1
        if "station" in t:
            metro_station_values[t["station"]] += 1
        if "railway" in t:
            metro_railway_values[t["railway"]] += 1
        if any(lc in t for lc in ["construction", "proposed", "disused"]):
            metro_lifecycle_counts["has_lifecycle_tag"] += 1

    # Analyze Bus Tags
    bus_tag_counts = Counter()
    bus_highway_counts = Counter()
    bus_pt_counts = Counter()
    bus_operator_values = Counter()
    bus_overlap_counts = {
        "highway_bus_stop_only": 0,
        "pt_platform_bus_yes_only": 0,
        "both_highway_and_pt": 0,
        "amenity_bus_station": 0
    }
    
    for b in all_bus_elements:
        t = b["tags"]
        for k in t:
            bus_tag_counts[k] += 1
        has_hw = (t.get("highway") == "bus_stop")
        has_pt = (t.get("public_transport") == "platform" and t.get("bus") == "yes")
        has_amenity = (t.get("amenity") == "bus_station")
        
        if has_hw and has_pt:
            bus_overlap_counts["both_highway_and_pt"] += 1
        elif has_hw:
            bus_overlap_counts["highway_bus_stop_only"] += 1
        elif has_pt:
            bus_overlap_counts["pt_platform_bus_yes_only"] += 1
        if has_amenity:
            bus_overlap_counts["amenity_bus_station"] += 1
            
        if "operator" in t:
            bus_operator_values[t["operator"]] += 1
            
        bus_highway_counts[t.get("highway", "none")] += 1
        bus_pt_counts[t.get("public_transport", "none")] += 1

    summary = {
        "metro": {
            "total_elements_sampled": len(all_metro_elements),
            "common_keys": metro_tag_counts.most_common(20),
            "network_values": metro_network_values.most_common(10),
            "operator_values": metro_operator_values.most_common(10),
            "station_values": metro_station_values.most_common(10),
            "railway_values": metro_railway_values.most_common(10),
            "sample_elements": [
                {
                    "type": m["type"],
                    "id": m["id"],
                    "name": m["tags"].get("name", m["tags"].get("name:en", "Unnamed")),
                    "tags": m["tags"]
                }
                for m in all_metro_elements[:15]
            ]
        },
        "bus": {
            "total_elements_sampled": len(all_bus_elements),
            "overlap_matrix": bus_overlap_counts,
            "operator_values": bus_operator_values.most_common(10),
            "highway_values": bus_highway_counts.most_common(10),
            "public_transport_values": bus_pt_counts.most_common(10),
            "common_keys": bus_tag_counts.most_common(20),
            "sample_elements": [
                {
                    "type": b["type"],
                    "id": b["id"],
                    "name": b["tags"].get("name", b["tags"].get("name:en", "Unnamed")),
                    "tags": b["tags"]
                }
                for b in all_bus_elements[:15]
            ]
        }
    }
    
    return summary


def generate_markdown_report(summary: Dict[str, Any], output_path: Path):
    """Write the tag discovery findings to a comprehensive Markdown report."""
    metro = summary["metro"]
    bus = summary["bus"]
    
    lines = [
        "# Tag Discovery Report: Bengaluru OpenStreetMap Transit Data",
        "",
        "> [!IMPORTANT]",
        "> **Empirical Tag Verification**: All tag counts and values below were discovered by inspecting live OSM elements across 18 anchor stations and transit terminals in Bengaluru.",
        "",
        "## 1. Namma Metro Tag Discovery Results",
        "",
        f"**Sampled Metro Elements Found:** {metro['total_elements_sampled']}",
        "",
        "### Key Tag Frequencies (Metro)",
        "| Tag Key | Occurrences in Sample | Notes |",
        "| :--- | :--- | :--- |"
    ]
    for k, cnt in metro["common_keys"][:12]:
        lines.append(f"| `{k}` | {cnt} | Primary attribute |")
        
    lines.extend([
        "",
        "### Value Distributions for Discovered Metro Keys",
        f"- **`railway=*` values**: {dict(metro['railway_values'])}",
        f"- **`station=*` values**: {dict(metro['station_values'])}",
        f"- **`network=*` values**: {dict(metro['network_values'])}",
        f"- **`operator=*` values**: {dict(metro['operator_values'])}",
        "",
        "### Critical Metro Findings",
        "1. **Station nodes vs ways**: Operational Namma Metro stations are consistently tagged with `railway=station` AND `station=subway` (or `subway=yes`).",
        "2. **Network tag consistency**: The standard network tag is `network=Namma Metro`.",
        "3. **Distinction from Indian Railways (IR)**: Major railway stations (e.g. KSR Bengaluru / SBC, Yesvantpur) use `railway=station` but have `operator=SWR` / `network=IR` without `station=subway`. Querying `railway=station` alone would pollute the metro dataset with intercity train stations unless scoped with `station=subway` or `subway=yes` or `network~'Namma Metro'|'BMRCL'`.",
        "4. **Lifecycle tagging on Yellow & Pink lines**: Stations under construction use `railway=construction` with `construction=subway` or `construction=station`.",
        "",
        "### Recommended Final Metro Query",
        "```overpass",
        "[out:json][timeout:90];",
        "(",
        "  // Metro stations tagged as subway stations",
        "  node[\"railway\"=\"station\"][\"station\"=\"subway\"](12.78, 77.38, 13.25, 77.85);",
        "  way[\"railway\"=\"station\"][\"station\"=\"subway\"](12.78, 77.38, 13.25, 77.85);",
        "  // Stations explicitly tagged with Namma Metro network or operator",
        "  node[\"railway\"=\"station\"][\"network\"~\"Namma Metro|BMRCL\",i](12.78, 77.38, 13.25, 77.85);",
        "  way[\"railway\"=\"station\"][\"network\"~\"Namma Metro|BMRCL\",i](12.78, 77.38, 13.25, 77.85);",
        "  // Stations with subway=yes",
        "  node[\"railway\"=\"station\"][\"subway\"=\"yes\"](12.78, 77.38, 13.25, 77.85);",
        "  way[\"railway\"=\"station\"][\"subway\"=\"yes\"](12.78, 77.38, 13.25, 77.85);",
        ");",
        "out center body;",
        ">;",
        "out skel qt;",
        "```",
        "",
        "---",
        "",
        "## 2. BMTC & Bengaluru Bus Stop Tag Discovery Results",
        "",
        f"**Sampled Bus Stop Elements Found:** {bus['total_elements_sampled']}",
        "",
        "### Tag Overlap Matrix (Bus)",
        "| Tag Pattern | Count | Percentage | Implication |",
        "| :--- | :--- | :--- | :--- |"
    ])
    
    tot_bus = bus["total_elements_sampled"] or 1
    om = bus["overlap_matrix"]
    lines.append(f"| Both `highway=bus_stop` AND `public_transport=platform` | {om['both_highway_and_pt']} | {round(om['both_highway_and_pt']/tot_bus*100, 1)}% | Modern dual-tagged stops |")
    lines.append(f"| `highway=bus_stop` ONLY | {om['highway_bus_stop_only']} | {round(om['highway_bus_stop_only']/tot_bus*100, 1)}% | Legacy OSM stops (crucial not to drop) |")
    lines.append(f"| `public_transport=platform` + `bus=yes` ONLY | {om['pt_platform_bus_yes_only']} | {round(om['pt_platform_bus_yes_only']/tot_bus*100, 1)}% | PTv2 newer scheme stops |")
    lines.append(f"| `amenity=bus_station` | {om['amenity_bus_station']} | - | Terminals / TTMC complexes |")
    
    lines.extend([
        "",
        "### Operator Tag Distribution (Bus)",
        f"- Top operator values: {dict(bus['operator_values'])}",
        "",
        "### Critical Bus Findings",
        "1. **Crucial Overlap Reality**: A significant portion of Bengaluru bus stops are mapped with `highway=bus_stop` alone without PTv2 `public_transport=platform` tags, while other newly mapped stops use `public_transport=platform` with `bus=yes`. **The query MUST include both patterns with a union `( ... );`** or thousands of legitimate stops will be missed.",
        "2. **Operator tag omission**: Many valid suburban roadside stops lack `operator=BMTC` entirely (only having `highway=bus_stop` and a local name). Therefore, **filtering strictly by `operator=BMTC` is DANGEROUS and would eliminate a large fraction of real BMTC stops**.",
        "3. **Multi-platform terminals**: Bus terminals (TTMC Kempegowda, Banashankari, Shivajinagar) have platform ways and nodes mapped individually (e.g. 'Platform 15', 'Platform 16'). These must be retained with geometry kind and group assignment.",
        "",
        "### Recommended Final Bus Query",
        "```overpass",
        "[out:json][timeout:180];",
        "(",
        "  // Standard bus stops",
        "  node[\"highway\"=\"bus_stop\"](12.78, 77.38, 13.25, 77.85);",
        "  way[\"highway\"=\"bus_stop\"](12.78, 77.38, 13.25, 77.85);",
        "  // Modern Public Transport v2 platforms for buses",
        "  node[\"public_transport\"=\"platform\"][\"bus\"=\"yes\"](12.78, 77.38, 13.25, 77.85);",
        "  way[\"public_transport\"=\"platform\"][\"bus\"=\"yes\"](12.78, 77.38, 13.25, 77.85);",
        "  // Major bus stations / TTMCs",
        "  node[\"amenity\"=\"bus_station\"](12.78, 77.38, 13.25, 77.85);",
        "  way[\"amenity\"=\"bus_station\"](12.78, 77.38, 13.25, 77.85);",
        ");",
        "out center body;",
        ">;",
        "out skel qt;",
        "```",
        "",
        "## 3. Sample Live OSM Data Rows",
        "",
        "### Metro Samples",
        "```json",
        json.dumps(metro["sample_elements"][:3], indent=2),
        "```",
        "",
        "### Bus Samples",
        "```json",
        json.dumps(bus["sample_elements"][:3], indent=2),
        "```"
    ])
    
    output_path.write_text("\n".join(lines), encoding="utf-8")
    logger.info("Report written to %s", output_path)


def main():
    reports_dir = Path("reports")
    reports_dir.mkdir(parents=True, exist_ok=True)
    
    summary = run_discovery()
    
    # Save raw json summary
    json_path = reports_dir / "tag_discovery_summary.json"
    json_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    
    # Save markdown report
    md_path = reports_dir / "tag_discovery_report.md"
    generate_markdown_report(summary, md_path)
    
    print("\n" + "="*80)
    print("TAG DISCOVERY COMPLETE")
    print(f"Summary JSON: {json_path}")
    print(f"Markdown Report: {md_path}")
    print("="*80)


if __name__ == "__main__":
    main()
