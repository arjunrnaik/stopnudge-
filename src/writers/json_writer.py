"""Standard JSON writer for CleanStop records."""

import json
from dataclasses import asdict
from pathlib import Path
from typing import List

from src.cleaning.models import CleanStop


def write_json(stops: List[CleanStop], output_path: Path) -> Path:
    """Serialize CleanStop records into a clean JSON array."""
    data = [asdict(s) for s in stops]
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return output_path
