"""Unit tests for schema validation, metro reconciliation, and diff engine."""

import tempfile
from pathlib import Path

import pytest
from src.cleaning.models import CleanStop
from src.validation.diff_reporter import compute_diff
from src.validation.metro_reconciler import fuzzy_match_ratio, reconcile_metro
from src.validation.schema_validator import SchemaValidationError, validate_stops_schema


def test_schema_validator_valid():
    stops = [
        CleanStop(
            id="osm_node_1",
            name="Trinity",
            name_en="Trinity",
            name_kn=None,
            latitude=12.9729,
            longitude=77.6171,
            mode="METRO",
            metro_lines=["Purple"],
            operator="BMRCL",
            ref=None,
            group_id=None,
            geometry_kind="node",
            flags=[],
            source="OpenStreetMap",
            osm_timestamp="2026-11-14T00:00:00Z",
            last_checked_date="2026-11-14",
            verified=True
        )
    ]
    assert validate_stops_schema(stops) is True


def test_schema_validator_out_of_bounds_raises():
    stops = [
        CleanStop(
            id="osm_node_invalid",
            name="Invalid Stop",
            name_en="Invalid Stop",
            name_kn=None,
            latitude=14.5000,  # Out of Bengaluru BBox
            longitude=77.6171,
            mode="BUS",
            metro_lines=None,
            operator="BMTC",
            ref=None,
            group_id=None,
            geometry_kind="node",
            flags=[],
            source="OpenStreetMap",
            osm_timestamp="2026-11-14T00:00:00Z",
            last_checked_date="2026-11-14",
            verified=False
        )
    ]
    with pytest.raises(SchemaValidationError, match="outside Bengaluru BBox"):
        validate_stops_schema(stops)


def test_schema_validator_metro_without_lines_raises():
    stops = [
        CleanStop(
            id="osm_node_no_line",
            name="No Line Metro",
            name_en="No Line Metro",
            name_kn=None,
            latitude=12.9729,
            longitude=77.6171,
            mode="METRO",
            metro_lines=[],  # Missing lines
            operator="BMRCL",
            ref=None,
            group_id=None,
            geometry_kind="node",
            flags=[],
            source="OpenStreetMap",
            osm_timestamp="2026-11-14T00:00:00Z",
            last_checked_date="2026-11-14",
            verified=True
        )
    ]
    with pytest.raises(SchemaValidationError, match="has no lines assigned"):
        validate_stops_schema(stops)


def test_fuzzy_match_ratio():
    # Minor spelling variation
    score = fuzzy_match_ratio("Baiyappanahalli", "Baiyyappanahalli")
    assert score > 0.85
    # Completely different
    assert fuzzy_match_ratio("Majestic", "Whitefield") < 0.40


def test_diff_reporter_baseline():
    stops = [
        CleanStop(
            id="osm_node_1",
            name="MG Road",
            name_en="MG Road",
            name_kn=None,
            latitude=12.9755,
            longitude=77.6066,
            mode="METRO",
            metro_lines=["Purple"],
            operator="BMRCL",
            ref=None,
            group_id=None,
            geometry_kind="node",
            flags=[],
            source="OpenStreetMap",
            osm_timestamp="2026-11-14T00:00:00Z",
            last_checked_date="2026-11-14",
            verified=True
        )
    ]
    diff = compute_diff(stops, previous_pack_dir=None)
    assert diff["status"] == "INITIAL_PACK"
    assert diff["added"] == 1
