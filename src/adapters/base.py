"""Base classes and interfaces for transit data source adapters."""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any, Dict, List, Optional


@dataclass
class RawTransitSnapshot:
    """Represents an immutable snapshot of raw transit data from a source."""
    mode: str  # "METRO" or "BUS"
    elements: List[Dict[str, Any]]
    raw_payload: Dict[str, Any]
    query_text: str
    endpoint_used: str
    osm_timestamp: Optional[str]
    fetched_at: str
    raw_file_path: str
    sha256_hash: str
    element_count: int


class SourceAdapter(ABC):
    """Abstract interface for transit data source adapters.
    
    Allows swapping the underlying data source (e.g., Overpass API vs. GTFS feed vs. OSM PBF)
    without modifying downstream cleaning, validation, or output packaging.
    """

    @abstractmethod
    def fetch_metro_data(self, force_refresh: bool = False) -> RawTransitSnapshot:
        """Fetch raw Namma Metro station data."""
        pass

    @abstractmethod
    def fetch_bus_data(self, force_refresh: bool = False) -> RawTransitSnapshot:
        """Fetch raw BMTC & Bengaluru bus stop data."""
        pass
