"""Overpass API source adapter with multi-server failover, exponential backoff,
and raw-response disk caching.
"""

import hashlib
import json
import logging
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from src.adapters.base import RawTransitSnapshot, SourceAdapter

logger = logging.getLogger("OverpassAdapter")

# Default bounding box: covers all operational Phase 1, Phase 2, and outer commuter terminals
# South: 12.78, West: 77.38, North: 13.25, East: 77.85
DEFAULT_BBOX = "12.78, 77.38, 13.25, 77.85"

# Verified Overpass Endpoints with automatic failover
DEFAULT_ENDPOINTS = [
    "https://lz4.overpass-api.de/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter"
]

USER_AGENT = "WakeMeTransitPipeline/1.0 (+https://github.com/user/wakeme; contact: transit-dev)"


METRO_QUERY_TEMPLATE = """[out:json][timeout:90];
(
  // Metro stations tagged as subway stations
  node["railway"="station"]["station"="subway"]({bbox});
  way["railway"="station"]["station"="subway"]({bbox});
  
  // Stations explicitly tagged with Namma Metro network or operator
  node["railway"="station"]["network"~"Namma Metro|BMRCL",i]({bbox});
  way["railway"="station"]["network"~"Namma Metro|BMRCL",i]({bbox});
  
  // Stations or stop positions with subway=yes and Namma Metro
  node["subway"="yes"]["network"~"Namma Metro|BMRCL",i]({bbox});
  node["railway"="station"]["subway"="yes"]({bbox});
  way["railway"="station"]["subway"="yes"]({bbox});
  
  // Construction/unconfirmed stations for audit
  node["railway"="construction"]["construction"~"subway|station"]({bbox});
  way["railway"="construction"]["construction"~"subway|station"]({bbox});
);
out center body;
>;
out skel qt;
"""

BUS_QUERY_TEMPLATE = """[out:json][timeout:180];
(
  // Standard highway bus stops
  node["highway"="bus_stop"]({bbox});
  way["highway"="bus_stop"]({bbox});
  
  // Modern Public Transport v2 platforms for buses
  node["public_transport"="platform"]["bus"="yes"]({bbox});
  way["public_transport"="platform"]["bus"="yes"]({bbox});
  
  // Major bus stations / TTMCs
  node["amenity"="bus_station"]({bbox});
  way["amenity"="bus_station"]({bbox});
);
out center body;
>;
out skel qt;
"""


class OverpassAdapter(SourceAdapter):
    """Fetches transit data from the OpenStreetMap Overpass API."""

    def __init__(
        self,
        raw_data_dir: Path = Path("data/raw"),
        endpoints: Optional[List[str]] = None,
        bbox: str = DEFAULT_BBOX,
        timeout_seconds: int = 180,
        max_retries_per_endpoint: int = 3
    ):
        self.raw_data_dir = raw_data_dir
        self.raw_data_dir.mkdir(parents=True, exist_ok=True)
        self.endpoints = endpoints or DEFAULT_ENDPOINTS
        self.bbox = bbox
        self.timeout_seconds = timeout_seconds
        self.max_retries = max_retries_per_endpoint

    def _execute_query_with_failover(self, query: str) -> Tuple[Dict[str, Any], str]:
        """Execute query across configured Overpass endpoints with exponential backoff."""
        data = urllib.parse.urlencode({"data": query}).encode("utf-8")
        headers = {
            "User-Agent": USER_AGENT,
            "Accept": "application/json"
        }

        last_error = None
        for endpoint in self.endpoints:
            logger.info("Attempting Overpass query on endpoint: %s", endpoint)
            for attempt in range(1, self.max_retries + 1):
                try:
                    req = urllib.request.Request(endpoint, data=data, headers=headers)
                    with urllib.request.urlopen(req, timeout=self.timeout_seconds) as resp:
                        raw_bytes = resp.read()
                        parsed = json.loads(raw_bytes.decode("utf-8"))
                        logger.info("Successfully fetched %d elements from %s (attempt %d)",
                                    len(parsed.get("elements", [])), endpoint, attempt)
                        return parsed, endpoint
                except Exception as e:
                    last_error = e
                    wait_time = 2 ** attempt
                    logger.warning("Endpoint %s failed (attempt %d/%d): %s. Backing off %ds...",
                                   endpoint, attempt, self.max_retries, e, wait_time)
                    time.sleep(wait_time)

        raise RuntimeError(f"All Overpass endpoints exhausted. Last error: {last_error}")

    def _save_raw_snapshot(
        self,
        mode: str,
        payload: Dict[str, Any],
        query: str,
        endpoint: str
    ) -> RawTransitSnapshot:
        """Save unmodified raw JSON payload to disk and compute its checksum."""
        now_utc = datetime.now(timezone.utc)
        timestamp_str = now_utc.strftime("%Y%m%d_%H%M%S")
        
        # Save timestamped version
        filename = f"raw_{mode.lower()}_{timestamp_str}.json"
        filepath = self.raw_data_dir / filename
        
        # Serialize raw JSON exactly as returned
        raw_json_str = json.dumps(payload, ensure_ascii=False, indent=2)
        filepath.write_text(raw_json_str, encoding="utf-8")
        
        # Also maintain a latest pointer
        latest_path = self.raw_data_dir / f"raw_{mode.lower()}_latest.json"
        latest_path.write_text(raw_json_str, encoding="utf-8")
        
        # Compute SHA-256
        sha256 = hashlib.sha256(raw_json_str.encode("utf-8")).hexdigest()
        
        osm_timestamp = payload.get("osm3s", {}).get("timestamp_osm_base")
        elements = payload.get("elements", [])
        
        snapshot = RawTransitSnapshot(
            mode=mode,
            elements=elements,
            raw_payload=payload,
            query_text=query,
            endpoint_used=endpoint,
            osm_timestamp=osm_timestamp,
            fetched_at=now_utc.isoformat(),
            raw_file_path=str(filepath),
            sha256_hash=sha256,
            element_count=len(elements)
        )
        return snapshot

    def _load_cached_snapshot(self, mode: str) -> Optional[RawTransitSnapshot]:
        """Load latest cached raw snapshot if present."""
        latest_path = self.raw_data_dir / f"raw_{mode.lower()}_latest.json"
        if not latest_path.exists():
            return None
            
        try:
            content = latest_path.read_text(encoding="utf-8")
            payload = json.loads(content)
            sha256 = hashlib.sha256(content.encode("utf-8")).hexdigest()
            osm_timestamp = payload.get("osm3s", {}).get("timestamp_osm_base")
            elements = payload.get("elements", [])
            
            logger.info("Loaded cached raw snapshot for %s from %s (%d elements)",
                        mode, latest_path, len(elements))
            return RawTransitSnapshot(
                mode=mode,
                elements=elements,
                raw_payload=payload,
                query_text="<from_cache>",
                endpoint_used="<local_cache>",
                osm_timestamp=osm_timestamp,
                fetched_at="<cached>",
                raw_file_path=str(latest_path),
                sha256_hash=sha256,
                element_count=len(elements)
            )
        except Exception as e:
            logger.warning("Could not read cached raw snapshot: %s", e)
            return None

    def fetch_metro_data(self, force_refresh: bool = False) -> RawTransitSnapshot:
        """Fetch raw Namma Metro station elements from Overpass."""
        if not force_refresh:
            cached = self._load_cached_snapshot("METRO")
            if cached is not None:
                return cached

        query = METRO_QUERY_TEMPLATE.format(bbox=self.bbox)
        logger.info("Executing Metro Overpass query...")
        payload, endpoint = self._execute_query_with_failover(query)
        snapshot = self._save_raw_snapshot("METRO", payload, query, endpoint)
        return snapshot

    def fetch_bus_data(self, force_refresh: bool = False) -> RawTransitSnapshot:
        """Fetch raw BMTC & Bengaluru bus stop elements from Overpass."""
        if not force_refresh:
            cached = self._load_cached_snapshot("BUS")
            if cached is not None:
                return cached

        query = BUS_QUERY_TEMPLATE.format(bbox=self.bbox)
        logger.info("Executing Bus Stops Overpass query...")
        payload, endpoint = self._execute_query_with_failover(query)
        snapshot = self._save_raw_snapshot("BUS", payload, query, endpoint)
        return snapshot
