"""Schema and invariant validation for transit data packs."""

from typing import List
from src.cleaning.models import CleanStop

MIN_LAT = 12.78
MAX_LAT = 13.25
MIN_LON = 77.38
MAX_LON = 77.85


class SchemaValidationError(Exception):
    """Raised when a stop violates data schema or sanity invariants."""
    pass


def validate_stops_schema(stops: List[CleanStop]) -> bool:
    """Validate that all stops adhere strictly to required invariants."""
    for s in stops:
        # 1. ID checks
        if not s.id or not isinstance(s.id, str):
            raise SchemaValidationError(f"Invalid stop ID: {s.id}")
            
        # 2. Mode checks
        if s.mode not in ["METRO", "BUS"]:
            raise SchemaValidationError(f"Stop {s.id} has invalid mode: {s.mode}")
            
        # 3. Coordinate boundary checks
        if not (-90.0 <= s.latitude <= 90.0) or not (-180.0 <= s.longitude <= 180.0):
            raise SchemaValidationError(f"Stop {s.id} coordinates out of global range: ({s.latitude}, {s.longitude})")
            
        if not (MIN_LAT <= s.latitude <= MAX_LAT) or not (MIN_LON <= s.longitude <= MAX_LON):
            raise SchemaValidationError(f"Stop {s.id} coordinates outside Bengaluru BBox: ({s.latitude}, {s.longitude})")
            
        # 4. Name checks
        if not s.name and "no_name" not in s.flags:
            raise SchemaValidationError(f"Stop {s.id} is missing a name but lacks 'no_name' flag")
            
        # 5. Metro-specific checks
        if s.mode == "METRO":
            if not s.metro_lines or len(s.metro_lines) == 0:
                raise SchemaValidationError(f"Metro station {s.id} ({s.name}) has no lines assigned")
            if not s.name:
                raise SchemaValidationError(f"Metro station {s.id} cannot be unnamed")
                
    return True
