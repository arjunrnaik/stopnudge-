"""SQLite writer for Android Room-compatible mobile database."""

import json
import sqlite3
from pathlib import Path
from typing import List

from src.cleaning.models import CleanStop


def write_sqlite(
    metro_stops: List[CleanStop],
    bus_stops: List[CleanStop],
    output_path: Path,
    metadata_entries: dict
) -> Path:
    """Create a Room-friendly SQLite database bundling all transit stops."""
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    # Overwrite if exists
    if output_path.exists():
        output_path.unlink()

    conn = sqlite3.connect(output_path)
    cursor = conn.cursor()

    # Create tables
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS metro_stations (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        name_en TEXT NOT NULL,
        name_kn TEXT,
        latitude REAL NOT NULL,
        longitude REAL NOT NULL,
        mode TEXT NOT NULL,
        metro_lines TEXT NOT NULL,
        operator TEXT,
        ref TEXT,
        group_id TEXT,
        geometry_kind TEXT NOT NULL,
        flags TEXT NOT NULL,
        source TEXT NOT NULL,
        osm_timestamp TEXT,
        last_checked_date TEXT NOT NULL,
        verified INTEGER NOT NULL
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS bus_stops (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        name_en TEXT NOT NULL,
        name_kn TEXT,
        latitude REAL NOT NULL,
        longitude REAL NOT NULL,
        mode TEXT NOT NULL,
        metro_lines TEXT,
        operator TEXT,
        ref TEXT,
        group_id TEXT,
        geometry_kind TEXT NOT NULL,
        flags TEXT NOT NULL,
        source TEXT NOT NULL,
        osm_timestamp TEXT,
        last_checked_date TEXT NOT NULL,
        verified INTEGER NOT NULL
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS metadata (
        key TEXT PRIMARY KEY NOT NULL,
        value TEXT NOT NULL
    );
    """)

    # Fast spatial and search indexes for Android
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_metro_coords ON metro_stations (latitude, longitude);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_metro_name ON metro_stations (name_en);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_bus_coords ON bus_stops (latitude, longitude);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_bus_name ON bus_stops (name_en);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_bus_group ON bus_stops (group_id);")

    # Insert Metro
    for m in metro_stops:
        cursor.execute("""
        INSERT INTO metro_stations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            m.id,
            m.name,
            m.name_en,
            m.name_kn,
            m.latitude,
            m.longitude,
            m.mode,
            json.dumps(m.metro_lines or []),
            m.operator,
            m.ref,
            m.group_id,
            m.geometry_kind,
            ",".join(m.flags),
            m.source,
            m.osm_timestamp,
            m.last_checked_date,
            1 if m.verified else 0
        ))

    # Insert Bus
    for b in bus_stops:
        cursor.execute("""
        INSERT INTO bus_stops VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            b.id,
            b.name,
            b.name_en,
            b.name_kn,
            b.latitude,
            b.longitude,
            b.mode,
            None,
            b.operator,
            b.ref,
            b.group_id,
            b.geometry_kind,
            ",".join(b.flags),
            b.source,
            b.osm_timestamp,
            b.last_checked_date,
            1 if b.verified else 0
        ))

    # Insert Metadata
    for k, v in metadata_entries.items():
        val_str = json.dumps(v) if isinstance(v, (dict, list)) else str(v)
        cursor.execute("INSERT INTO metadata VALUES (?, ?)", (k, val_str))

    conn.commit()
    conn.close()
    return output_path
