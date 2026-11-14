"""Unit tests for GeoJSON, JSON, and SQLite database writers."""

import json
import sqlite3
import tempfile
from pathlib import Path

import pytest
from src.cleaning.models import CleanStop
from src.writers.geojson_writer import write_geojson
from src.writers.json_writer import write_json
from src.writers.sqlite_writer import write_sqlite


@pytest.fixture
def sample_stops():
    return [
        CleanStop(
            id="osm_node_1001",
            name="MG Road",
            name_en="MG Road",
            name_kn="ಎಂ ಜಿ ರಸ್ತೆ",
            latitude=12.9755,
            longitude=77.6066,
            mode="METRO",
            metro_lines=["Purple"],
            operator="BMRCL",
            ref="MGRD",
            group_id=None,
            geometry_kind="node",
            flags=[],
            source="OpenStreetMap",
            osm_timestamp="2026-11-14T10:00:00Z",
            last_checked_date="2026-11-14",
            verified=True
        ),
        CleanStop(
            id="osm_node_2001",
            name="Silk Board",
            name_en="Silk Board",
            name_kn=None,
            latitude=12.9176,
            longitude=77.6233,
            mode="BUS",
            metro_lines=None,
            operator="BMTC",
            ref=None,
            group_id="bmtc_grp_abc123",
            geometry_kind="node",
            flags=["possible_duplicate"],
            source="OpenStreetMap",
            osm_timestamp="2026-11-14T10:00:00Z",
            last_checked_date="2026-11-14",
            verified=False
        )
    ]


def test_write_geojson(sample_stops):
    with tempfile.TemporaryDirectory() as tmpdir:
        out_file = Path(tmpdir) / "test.geojson"
        write_geojson(sample_stops, out_file)
        assert out_file.exists()

        content = json.loads(out_file.read_text(encoding="utf-8"))
        assert content["type"] == "FeatureCollection"
        assert len(content["features"]) == 2

        f1 = content["features"][0]
        assert f1["geometry"]["type"] == "Point"
        assert f1["geometry"]["coordinates"] == [77.6066, 12.9755]
        assert f1["properties"]["name"] == "MG Road"
        assert f1["properties"]["metro_lines"] == ["Purple"]


def test_write_json(sample_stops):
    with tempfile.TemporaryDirectory() as tmpdir:
        out_file = Path(tmpdir) / "test.json"
        write_json(sample_stops, out_file)
        assert out_file.exists()

        content = json.loads(out_file.read_text(encoding="utf-8"))
        assert len(content) == 2
        assert content[0]["id"] == "osm_node_1001"
        assert content[1]["group_id"] == "bmtc_grp_abc123"


def test_write_sqlite(sample_stops):
    with tempfile.TemporaryDirectory() as tmpdir:
        out_file = Path(tmpdir) / "test.db"
        metro_stops = [s for s in sample_stops if s.mode == "METRO"]
        bus_stops = [s for s in sample_stops if s.mode == "BUS"]
        meta = {"version": "2026.11.14"}

        write_sqlite(metro_stops, bus_stops, out_file, meta)
        assert out_file.exists()

        conn = sqlite3.connect(out_file)
        cursor = conn.cursor()

        # Check tables
        tables = [r[0] for r in cursor.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()]
        assert "metro_stations" in tables
        assert "bus_stops" in tables
        assert "metadata" in tables

        # Check metro records
        metro_row = cursor.execute("SELECT id, name, latitude, longitude, verified FROM metro_stations").fetchone()
        assert metro_row[0] == "osm_node_1001"
        assert metro_row[1] == "MG Road"
        assert metro_row[4] == 1  # Verified boolean mapped to 1

        # Check bus records
        bus_row = cursor.execute("SELECT id, name, group_id, verified FROM bus_stops").fetchone()
        assert bus_row[0] == "osm_node_2001"
        assert bus_row[2] == "bmtc_grp_abc123"
        assert bus_row[3] == 0  # Unverified mapped to 0

        conn.close()
