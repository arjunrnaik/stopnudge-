"""Unit tests for cleaning, normalization, and deduplication rules."""

import pytest
from src.cleaning.cleaner import (
    clean_bus_stops,
    clean_display_name,
    clean_metro_stations,
    detect_metro_line,
    extract_coordinates,
    haversine_distance,
    is_in_bbox,
    is_under_construction,
    normalize_name,
)


def test_haversine_distance():
    # Distance between MG Road Metro (12.9755, 77.6066) and Trinity Metro (12.9729, 77.6171) is ~1.17 km
    dist = haversine_distance(12.9755, 77.6066, 12.9729, 77.6171)
    assert 1100 <= dist <= 1250

    # Same point should be 0.0 meters
    assert haversine_distance(12.9755, 77.6066, 12.9755, 77.6066) == 0.0


def test_normalize_name():
    assert normalize_name("Silk Board Bus Stop") == "silk board"
    assert normalize_name("Domlur Jn.") == "domlur junction"
    assert normalize_name("Outer Ring Rd") == "outer ring road"
    assert normalize_name("Brigade St.") == "brigade street"
    assert normalize_name("  Majestic   Metro Station  ") == "majestic"


def test_is_in_bbox():
    # Inside Bengaluru
    assert is_in_bbox(12.9716, 77.5946) is True  # Vidhana Soudha
    assert is_in_bbox(12.8164, 77.6914) is True  # Bommasandra
    assert is_in_bbox(12.9984, 77.7592) is True  # Kadugodi Whitefield

    # Outside Bengaluru
    assert is_in_bbox(13.5000, 77.5946) is False  # Far north
    assert is_in_bbox(12.5000, 77.5946) is False  # Far south
    assert is_in_bbox(12.9716, 77.1000) is False  # Far west
    assert is_in_bbox(12.9716, 78.2000) is False  # Far east


def test_is_under_construction():
    assert is_under_construction({"railway": "construction", "construction": "subway"}) is True
    assert is_under_construction({"highway": "bus_stop", "construction": "yes"}) is True
    assert is_under_construction({"railway": "proposed"}) is True
    assert is_under_construction({"railway": "station", "station": "subway"}) is False
    assert is_under_construction({"highway": "bus_stop"}) is False


def test_extract_coordinates_node_and_way():
    node_elem = {"type": "node", "id": 101, "lat": 12.975512, "lon": 77.606611}
    coords = extract_coordinates(node_elem)
    assert coords == (12.975512, 77.606611, "node")

    way_elem = {
        "type": "way",
        "id": 202,
        "center": {"lat": 12.975600, "lon": 77.606700}
    }
    coords = extract_coordinates(way_elem)
    assert coords == (12.9756, 77.6067, "way")


def test_detect_metro_line():
    assert "Purple" in detect_metro_line({"colour": "#9b26b6"}, "MG Road")
    assert "Green" in detect_metro_line({"colour": "green"}, "Silk Institute")
    # Majestic is an interchange with Purple & Green
    lines = detect_metro_line({}, "Nadaprabhu Kempegowda Station, Majestic")
    assert "Purple" in lines and "Green" in lines


def test_opposite_side_bus_stops_retained_and_grouped():
    # Two bus stops on opposite sides of the road (approx 45 meters apart)
    # Stop 1: 12.955500, 77.701100
    # Stop 2: 12.955800, 77.701300 (~40 m apart)
    elements = [
        {
            "type": "node",
            "id": 1,
            "lat": 12.955500,
            "lon": 77.701100,
            "tags": {"highway": "bus_stop", "name": "Marathahalli Bridge", "operator": "BMTC"}
        },
        {
            "type": "node",
            "id": 2,
            "lat": 12.955800,
            "lon": 77.701300,
            "tags": {"highway": "bus_stop", "name": "Marathahalli Bridge", "operator": "BMTC"}
        }
    ]

    cleaned = clean_bus_stops(elements, check_date="2026-11-14")
    # BOTH must be kept as distinct records
    assert len(cleaned) == 2
    # Both must share the same group_id
    assert cleaned[0].group_id is not None
    assert cleaned[0].group_id == cleaned[1].group_id
    # Neither should be collapsed or dropped
    assert {c.id for c in cleaned} == {"osm_node_1", "osm_node_2"}


def test_near_duplicate_bus_stops_flagged():
    # Two mapper nodes placed for the same physical bus pole (< 10 meters apart)
    elements = [
        {
            "type": "node",
            "id": 10,
            "lat": 12.955500,
            "lon": 77.701100,
            "tags": {"highway": "bus_stop", "name": "Silk Board"}
        },
        {
            "type": "node",
            "id": 11,
            "lat": 12.955520,
            "lon": 77.701130,  # ~4 meters apart
            "tags": {"highway": "bus_stop", "name": "Silk Board"}
        }
    ]

    cleaned = clean_bus_stops(elements, check_date="2026-11-14")
    assert len(cleaned) == 2
    # At least one should be flagged as possible duplicate
    has_dup_flag = any("possible_duplicate" in c.flags for c in cleaned)
    assert has_dup_flag is True


def test_unnamed_bus_stop_kept_with_flag():
    elements = [
        {
            "type": "node",
            "id": 99,
            "lat": 12.955500,
            "lon": 77.701100,
            "tags": {"highway": "bus_stop"}  # No name tag
        }
    ]
    cleaned = clean_bus_stops(elements, check_date="2026-11-14")
    assert len(cleaned) == 1
    assert cleaned[0].name == ""
    assert "no_name" in cleaned[0].flags


def test_construction_metro_station_isolated():
    elements = [
        {
            "type": "node",
            "id": 501,
            "lat": 12.877500,
            "lon": 77.595500,
            "tags": {
                "name": "Kalena Agrahara",
                "railway": "construction",
                "construction": "subway"
            }
        },
        {
            "type": "node",
            "id": 502,
            "lat": 12.975500,
            "lon": 77.606600,
            "tags": {
                "name": "MG Road",
                "railway": "station",
                "station": "subway",
                "network": "Namma Metro"
            }
        }
    ]

    operating, unconfirmed = clean_metro_stations(elements, check_date="2026-11-14")
    assert len(operating) == 1
    assert operating[0].name == "MG Road"
    assert len(unconfirmed) == 1
    assert unconfirmed[0]["name"] == "Kalena Agrahara"
