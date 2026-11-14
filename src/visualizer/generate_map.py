"""
Interactive Visual Map Generator for Bengaluru Namma Metro stations.
Produces a self-contained HTML artifact with SVG rendering, pan/zoom, line filters,
search, and WakeMe proximity alarm geofencing simulation.
"""

import json
import math
import os
from typing import Dict, Any, List

def generate_metro_map_html(
    metro_json_path: str,
    unconfirmed_json_path: str,
    output_html_path: str
) -> str:
    metro_stations: List[Dict[str, Any]] = json.load(open(metro_json_path, encoding="utf-8"))
    unconfirmed: List[Dict[str, Any]] = json.load(open(unconfirmed_json_path, encoding="utf-8"))

    # Extract unique Pink Line stations
    raw_pink = [s for s in unconfirmed if s.get("reason") == "pink_line_unconfirmed_by_bmrcl"]
    seen_pink = set()
    pink_stations = []
    for s in sorted(raw_pink, key=lambda x: x["latitude"]):
        name = s["name"].replace(" (u/c)", "").strip()
        if name in ["N S Palya"] and "JP Nagar 4th Phase" in seen_pink:
            continue
        if name in ["Suddaguntepalya"] and "Tavarekere" in seen_pink:
            continue
        if name not in seen_pink:
            seen_pink.add(name)
            pink_stations.append({
                "id": s["id"],
                "name": name,
                "name_en": name,
                "name_kn": s.get("tags", {}).get("name:kn", ""),
                "latitude": s["latitude"],
                "longitude": s["longitude"],
                "metro_lines": ["Pink (u/c)"],
                "mode": "METRO",
                "is_unconfirmed": True,
                "operator": "BMRCL",
                "ref": s.get("tags", {}).get("ref") or "PINK-UC"
            })

    # Prepare datasets for embedding
    # Purple Line ordered West-to-East
    purple_stations = sorted([s for s in metro_stations if "Purple" in s["metro_lines"]], key=lambda x: x["longitude"])
    
    # Green Line ordered North-to-South
    green_stations = sorted([s for s in metro_stations if "Green" in s["metro_lines"]], key=lambda x: -x["latitude"])

    # Yellow Line ordered RV Road to Bommasandra
    yellow_seq = [
        "Rashtreeya Vidyalaya Road", "Ragigudda", "Jayadeva Hospital", "BTM Layout",
        "Central Silk Board", "Bommanahalli", "Hongasandra", "Kudlu Gate",
        "Singasandra", "Hosa Road", "Beratena Agrahara", "Electronic City",
        "Infosys Foundation Konappana Agrahara", "Huskur Road", "Biocon Hebbagodi",
        "Delta Electronics Bommasandra"
    ]
    y_dict = {s["name"]: s for s in metro_stations if "Yellow" in s["metro_lines"]}
    yellow_stations = [y_dict[n] for n in yellow_seq if n in y_dict]

    # Pink Line ordered South-to-North
    pink_stations_sorted = sorted(pink_stations, key=lambda x: x["latitude"])

    # Combine all stations for index
    all_stations = list(metro_stations) + pink_stations

    # Calculate geographic bounds
    all_lats = [s["latitude"] for s in all_stations]
    all_lons = [s["longitude"] for s in all_stations]
    min_lat, max_lat = min(all_lats), max(all_lats)
    min_lon, max_lon = min(all_lons), max(all_lons)

    # Pad bounds by 5%
    lat_pad = (max_lat - min_lat) * 0.06
    lon_pad = (max_lon - min_lon) * 0.06
    bbox = {
        "min_lat": min_lat - lat_pad,
        "max_lat": max_lat + lat_pad,
        "min_lon": min_lon - lon_pad,
        "max_lon": max_lon + lon_pad
    }

    # Serialized JSON data for JS
    stations_json = json.dumps(all_stations, ensure_ascii=False)
    purple_track = json.dumps([[s["latitude"], s["longitude"]] for s in purple_stations])
    green_track = json.dumps([[s["latitude"], s["longitude"]] for s in green_stations])
    yellow_track = json.dumps([[s["latitude"], s["longitude"]] for s in yellow_stations])
    pink_track = json.dumps([[s["latitude"], s["longitude"]] for s in pink_stations_sorted])
    bbox_json = json.dumps(bbox)

    html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Namma Metro — Interactive Network Map</title>
  <script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>
  <style>
    @keyframes pulse {{
      0%, 100% {{ transform: scale(1); opacity: 0.8; }}
      50% {{ transform: scale(1.4); opacity: 0.2; }}
    }}
    .pulse-ring {{
      animation: pulse 2.5s infinite ease-in-out;
      transform-origin: center;
    }}
    .station-node {{
      transition: transform 0.15s ease-out, filter 0.15s ease-out;
      cursor: pointer;
    }}
    .station-node:hover {{
      filter: drop-shadow(0 0 6px rgba(255, 255, 255, 0.8));
    }}
    .track-glow {{
      filter: drop-shadow(0 0 4px currentColor);
    }}
    /* Custom scrollbar */
    ::-webkit-scrollbar {{ width: 6px; height: 6px; }}
    ::-webkit-scrollbar-track {{ background: transparent; }}
    ::-webkit-scrollbar-thumb {{ background: rgba(150, 150, 150, 0.3); border-radius: 3px; }}
  </style>
</head>
<body class="bg-[var(--background,#0f172a)] text-[var(--foreground,#f8fafc)] antialiased min-h-screen flex flex-col font-sans select-none">
  
  <!-- Header / Nav -->
  <header class="bg-[var(--card,#1e293b)] border-b border-[var(--border,#334155)] px-4 py-3 shadow-md z-20 flex flex-wrap items-center justify-between gap-3">
    <div class="flex items-center space-x-3">
      <div class="w-9 h-9 rounded-lg bg-gradient-to-tr from-purple-600 via-emerald-500 to-amber-500 flex items-center justify-center shadow-lg">
        <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
        </svg>
      </div>
      <div>
        <h1 class="text-base font-bold tracking-tight text-[var(--foreground,#f8fafc)] flex items-center gap-2">
          Bengaluru Namma Metro
          <span class="text-xs px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 font-medium border border-emerald-500/30">Verified Pack</span>
        </h1>
        <p class="text-xs text-[var(--muted-foreground,#94a3b8)]">83 Operating Stations Reconciled • OSM Source • Offline Alarm Pipeline</p>
      </div>
    </div>

    <!-- Quick Stats Pills -->
    <div class="flex items-center gap-2 text-xs">
      <div class="px-2.5 py-1 rounded-md bg-[var(--background,#0f172a)] border border-[var(--border,#334155)] flex items-center gap-1.5">
        <span class="w-2 h-2 rounded-full bg-purple-500"></span>
        <span class="text-[var(--muted-foreground,#94a3b8)]">Purple:</span>
        <span class="font-semibold text-purple-300">37</span>
      </div>
      <div class="px-2.5 py-1 rounded-md bg-[var(--background,#0f172a)] border border-[var(--border,#334155)] flex items-center gap-1.5">
        <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
        <span class="text-[var(--muted-foreground,#94a3b8)]">Green:</span>
        <span class="font-semibold text-emerald-300">32</span>
      </div>
      <div class="px-2.5 py-1 rounded-md bg-[var(--background,#0f172a)] border border-[var(--border,#334155)] flex items-center gap-1.5">
        <span class="w-2 h-2 rounded-full bg-amber-500"></span>
        <span class="text-[var(--muted-foreground,#94a3b8)]">Yellow:</span>
        <span class="font-semibold text-amber-300">16</span>
      </div>
      <div class="px-2.5 py-1 rounded-md bg-[var(--background,#0f172a)] border border-pink-500/30 flex items-center gap-1.5">
        <span class="w-2 h-2 rounded-full bg-pink-500 border border-dashed border-white"></span>
        <span class="text-[var(--muted-foreground,#94a3b8)]">Pink (u/c):</span>
        <span class="font-semibold text-pink-300">18</span>
      </div>
    </div>
  </header>

  <!-- Controls Toolbar -->
  <div class="bg-[var(--card,#1e293b)]/80 backdrop-blur-md border-b border-[var(--border,#334155)] px-4 py-2.5 z-10 flex flex-wrap items-center justify-between gap-3 text-xs">
    
    <!-- Filter Chips -->
    <div class="flex items-center gap-1.5 overflow-x-auto py-0.5">
      <span class="text-[var(--muted-foreground,#94a3b8)] font-medium mr-1">Filter Line:</span>
      <button onclick="setLineFilter('all')" id="btn-all" class="line-btn active px-3 py-1 rounded-full font-medium transition-all bg-indigo-600 text-white shadow-sm">All Operating (83)</button>
      <button onclick="setLineFilter('Purple')" id="btn-Purple" class="line-btn px-3 py-1 rounded-full font-medium transition-all bg-[var(--background,#0f172a)] hover:bg-purple-900/40 text-purple-300 border border-purple-500/40">Purple (37)</button>
      <button onclick="setLineFilter('Green')" id="btn-Green" class="line-btn px-3 py-1 rounded-full font-medium transition-all bg-[var(--background,#0f172a)] hover:bg-emerald-900/40 text-emerald-300 border border-emerald-500/40">Green (32)</button>
      <button onclick="setLineFilter('Yellow')" id="btn-Yellow" class="line-btn px-3 py-1 rounded-full font-medium transition-all bg-[var(--background,#0f172a)] hover:bg-amber-900/40 text-amber-300 border border-amber-500/40">Yellow (16)</button>
      
      <label class="flex items-center gap-1.5 ml-2 pl-2 border-l border-[var(--border,#334155)] cursor-pointer text-pink-300 hover:text-pink-200">
        <input type="checkbox" id="toggle-pink" onchange="togglePinkLine(this.checked)" class="rounded text-pink-500 focus:ring-0">
        <span>Show Pink Line (Under Construction)</span>
      </label>
    </div>

    <!-- Search & Display Toggles -->
    <div class="flex items-center gap-3">
      <!-- Search Input -->
      <div class="relative w-56">
        <input type="text" id="station-search" oninput="handleSearch(this.value)" placeholder="Find station (e.g. Majestic, Silk Board)..." 
          class="w-full bg-[var(--background,#0f172a)] border border-[var(--border,#334155)] text-xs rounded-lg pl-7 pr-3 py-1.5 focus:outline-none focus:border-indigo-500 text-[var(--foreground,#f8fafc)] placeholder-[var(--muted-foreground,#64748b)]">
        <svg class="w-3.5 h-3.5 text-[var(--muted-foreground,#64748b)] absolute left-2 top-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
        </svg>
      </div>

      <!-- Toggle Labels -->
      <label class="flex items-center gap-1.5 cursor-pointer text-[var(--muted-foreground,#94a3b8)] hover:text-[var(--foreground,#f8fafc)]">
        <input type="checkbox" id="toggle-labels" onchange="toggleLabels(this.checked)" checked class="rounded text-indigo-500">
        <span>Labels</span>
      </label>

      <!-- Zoom Reset Buttons -->
      <div class="flex items-center border border-[var(--border,#334155)] rounded-lg overflow-hidden bg-[var(--background,#0f172a)]">
        <button onclick="zoomBy(1.25)" title="Zoom In" class="px-2 py-1 hover:bg-[var(--card,#1e293b)] text-[var(--foreground,#f8fafc)] font-bold">+</button>
        <button onclick="zoomBy(0.8)" title="Zoom Out" class="px-2 py-1 hover:bg-[var(--card,#1e293b)] text-[var(--foreground,#f8fafc)] font-bold border-l border-[var(--border,#334155)]">-</button>
        <button onclick="resetZoom()" title="Fit View" class="px-2 py-1 hover:bg-[var(--card,#1e293b)] text-xs border-l border-[var(--border,#334155)]">⟲</button>
      </div>
    </div>
  </div>

  <!-- Main Map Canvas & Drawer Layout -->
  <div class="relative flex-1 w-full overflow-hidden bg-[var(--background,#0b1120)] flex">
    
    <!-- SVG Map Container -->
    <div id="map-container" class="relative flex-1 w-full h-full cursor-grab active:cursor-grabbing overflow-hidden">
      <svg id="metro-svg" class="w-full h-full" viewBox="0 0 1050 820" preserveAspectRatio="xMidYMid meet">
        <defs>
          <!-- Grid Pattern -->
          <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
            <path d="M 40 0 L 0 0 0 40" fill="none" stroke="rgba(255,255,255,0.03)" stroke-width="1"/>
          </pattern>
          
          <!-- Outer Area Ring Glow -->
          <radialGradient id="hub-glow" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stop-color="#818cf8" stop-opacity="0.3"/>
            <stop offset="100%" stop-color="#818cf8" stop-opacity="0"/>
          </radialGradient>
        </defs>

        <!-- Base Background Grid -->
        <rect width="100%" height="100%" fill="url(#grid)" />

        <!-- Pan / Zoom Transformed Group -->
        <g id="viewport" transform="translate(0, 0) scale(1)">
          
          <!-- Outer Reference Boundaries (Outer Ring Road & City Center indicators) -->
          <g id="city-guides" opacity="0.4">
            <!-- Center Hub Marker around Majestic -->
            <circle cx="495" cy="350" r="140" fill="url(#hub-glow)" />
            <!-- Outer Ring Road subtle contour -->
            <ellipse cx="505" cy="410" rx="360" ry="290" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="2" stroke-dasharray="4,6" />
            <text x="780" y="380" fill="rgba(255,255,255,0.2)" font-size="11" letter-spacing="1.5">OUTER RING ROAD</text>
          </g>

          <!-- WakeMe Proximity Simulation Geofence Circle -->
          <g id="geofence-layer" style="display: none;">
            <circle id="geofence-circle" cx="0" cy="0" r="30" fill="rgba(59, 130, 246, 0.15)" stroke="#3b82f6" stroke-width="2" stroke-dasharray="6,4"/>
            <circle id="geofence-inner" cx="0" cy="0" r="4" fill="#3b82f6" />
          </g>

          <!-- Tracks Layer -->
          <g id="tracks-layer">
            <!-- Purple Line Track -->
            <path id="track-purple" d="" fill="none" stroke="#9333ea" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" class="track-glow text-purple-600 opacity-90"/>
            <!-- Green Line Track -->
            <path id="track-green" d="" fill="none" stroke="#10b981" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" class="track-glow text-emerald-600 opacity-90"/>
            <!-- Yellow Line Track -->
            <path id="track-yellow" d="" fill="none" stroke="#f59e0b" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" class="track-glow text-amber-500 opacity-90"/>
            <!-- Pink Line Track (Dashed, toggleable) -->
            <path id="track-pink" d="" fill="none" stroke="#ec4899" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="8,6" class="track-glow text-pink-500 opacity-80" style="display: none;"/>
          </g>

          <!-- Stations Layer -->
          <g id="stations-layer"></g>

          <!-- Station Labels Layer -->
          <g id="labels-layer"></g>

          <!-- Interchanges Layer (Majestic, RV Road, Jayadeva) -->
          <g id="interchanges-layer"></g>
        </g>
      </svg>

      <!-- Hover Tooltip -->
      <div id="tooltip" class="absolute pointer-events-none transition-opacity duration-150 opacity-0 bg-[var(--card,#1e293b)] text-[var(--foreground,#f8fafc)] border border-[var(--border,#475569)] text-xs rounded-lg px-3 py-2 shadow-2xl z-30 min-w-[170px]">
        <div id="tooltip-name" class="font-bold text-white text-sm">Station Name</div>
        <div id="tooltip-kn" class="text-indigo-300 text-xs font-normal mb-1">Kannada Script</div>
        <div class="flex items-center gap-1.5 mt-1" id="tooltip-badges"></div>
        <div id="tooltip-coords" class="text-[var(--muted-foreground,#94a3b8)] text-[11px] mt-1.5 font-mono">12.9716° N, 77.5946° E</div>
      </div>
    </div>

    <!-- Station Details Drawer (Slides in on Station Click) -->
    <div id="station-drawer" class="w-80 bg-[var(--card,#1e293b)] border-l border-[var(--border,#334155)] shadow-2xl flex flex-col z-20 transition-all duration-300 transform translate-x-full">
      
      <!-- Drawer Header -->
      <div class="p-4 border-b border-[var(--border,#334155)] flex items-start justify-between">
        <div>
          <span id="drawer-badge" class="px-2 py-0.5 rounded text-[11px] font-semibold tracking-wide uppercase bg-purple-500/20 text-purple-300 border border-purple-500/30">Purple Line</span>
          <h2 id="drawer-title" class="text-base font-bold text-[var(--foreground,#f8fafc)] mt-1.5 leading-snug">Station Name</h2>
          <p id="drawer-kn" class="text-xs text-indigo-300">Kannada Name</p>
        </div>
        <button onclick="closeDrawer()" class="text-[var(--muted-foreground,#94a3b8)] hover:text-white p-1 rounded-md hover:bg-slate-700">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
        </button>
      </div>

      <!-- Drawer Content -->
      <div class="p-4 space-y-4 flex-1 overflow-y-auto text-xs">
        
        <!-- Key Info Grid -->
        <div class="grid grid-cols-2 gap-2">
          <div class="bg-[var(--background,#0f172a)] p-2.5 rounded-lg border border-[var(--border,#334155)]">
            <span class="text-[var(--muted-foreground,#94a3b8)] block text-[11px]">Station Code</span>
            <span id="drawer-code" class="font-mono font-semibold text-[var(--foreground,#f8fafc)] text-sm">MJST</span>
          </div>
          <div class="bg-[var(--background,#0f172a)] p-2.5 rounded-lg border border-[var(--border,#334155)]">
            <span class="text-[var(--muted-foreground,#94a3b8)] block text-[11px]">Operating Status</span>
            <span id="drawer-status" class="font-semibold text-emerald-400">Operating (100%)</span>
          </div>
        </div>

        <!-- Coordinates & Geometry -->
        <div class="bg-[var(--background,#0f172a)] p-3 rounded-lg border border-[var(--border,#334155)] space-y-1.5">
          <div class="flex justify-between items-center">
            <span class="text-[var(--muted-foreground,#94a3b8)]">Latitude</span>
            <span id="drawer-lat" class="font-mono text-slate-200">12.975700° N</span>
          </div>
          <div class="flex justify-between items-center">
            <span class="text-[var(--muted-foreground,#94a3b8)]">Longitude</span>
            <span id="drawer-lon" class="font-mono text-slate-200">77.572800° E</span>
          </div>
          <div class="flex justify-between items-center">
            <span class="text-[var(--muted-foreground,#94a3b8)]">OSM Source Node</span>
            <span id="drawer-osm-id" class="font-mono text-indigo-400">osm_node_12345</span>
          </div>
        </div>

        <!-- WakeMe Geofence Proximity Simulator -->
        <div class="bg-indigo-950/40 border border-indigo-500/30 p-3 rounded-lg space-y-2">
          <div class="flex items-center justify-between">
            <span class="font-semibold text-indigo-300 flex items-center gap-1.5">
              <svg class="w-3.5 h-3.5 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/></svg>
              WakeMe Proximity Alarm
            </span>
            <span id="geofence-radius-val" class="font-mono font-bold text-indigo-200">500 m</span>
          </div>
          <p class="text-[11px] text-slate-400">Simulate Android app geofence triggering an alarm before arrival:</p>
          <input type="range" id="geofence-slider" min="200" max="2500" step="100" value="500" oninput="updateGeofenceRadius(this.value)" class="w-full accent-indigo-500">
          <div class="flex justify-between text-[10px] text-slate-400">
            <span>200m (Immediate)</span>
            <span>1000m</span>
            <span>2500m (Advanced)</span>
          </div>
        </div>

        <!-- External Link -->
        <a id="drawer-osm-link" href="#" target="_blank" class="block w-full text-center py-2 px-3 rounded-lg bg-[var(--background,#0f172a)] hover:bg-slate-700 border border-[var(--border,#334155)] font-medium text-slate-300 transition-colors">
          View Raw Node on OpenStreetMap ↗
        </a>
      </div>
      
      <!-- Drawer Footer -->
      <div class="p-3 border-t border-[var(--border,#334155)] bg-[var(--background,#0f172a)] text-[11px] text-[var(--muted-foreground,#94a3b8)] flex items-center justify-between">
        <span>Room DB: wakeme_transit.db</span>
        <span class="text-emerald-400">Verified</span>
      </div>
    </div>
  </div>

  <!-- Embedded Station & Track Data -->
  <script>
    const STATIONS = {stations_json};
    const TRACK_PURPLE = {purple_track};
    const TRACK_GREEN = {green_track};
    const TRACK_YELLOW = {yellow_track};
    const TRACK_PINK = {pink_track};
    const BBOX = {bbox_json};

    // Color Palette
    const LINE_COLORS = {{
      "Purple": "#9333ea",
      "Green": "#10b981",
      "Yellow": "#f59e0b",
      "Pink (u/c)": "#ec4899"
    }};

    // State
    let currentFilter = "all";
    let showPink = false;
    let showLabels = true;
    let selectedStation = null;
    let geofenceRadiusMeters = 500;

    // Pan & Zoom Transform State
    let scale = 1;
    let pointX = 0;
    let pointY = 0;
    let isPanning = false;
    let startX = 0;
    let startY = 0;

    const viewport = document.getElementById("viewport");
    const container = document.getElementById("map-container");
    const tooltip = document.getElementById("tooltip");
    const geofenceLayer = document.getElementById("geofence-layer");
    const geofenceCircle = document.getElementById("geofence-circle");

    // Coordinate Projection: Lat/Lon -> SVG (1050 x 820)
    const SVG_WIDTH = 1050;
    const SVG_HEIGHT = 820;
    const PAD = 40;

    function project(lat, lon) {{
      const x = PAD + ((lon - BBOX.min_lon) / (BBOX.max_lon - BBOX.min_lon)) * (SVG_WIDTH - 2 * PAD);
      const y = PAD + ((BBOX.max_lat - lat) / (BBOX.max_lat - BBOX.min_lat)) * (SVG_HEIGHT - 2 * PAD);
      return [x, y];
    }}

    // Meters to SVG scale factor at 12.97° N latitude
    function metersToSvg(meters) {{
      const latRangeKm = (BBOX.max_lat - BBOX.min_lat) * 110.8;
      const svgHeight = SVG_HEIGHT - 2 * PAD;
      const metersPerSvgUnit = (latRangeKm * 1000) / svgHeight;
      return meters / metersPerSvgUnit;
    }}

    // Render Track Paths
    function renderTracks() {{
      const buildPath = (coords) => {{
        return coords.map((c, i) => {{
          const [x, y] = project(c[0], c[1]);
          return (i === 0 ? "M" : "L") + `${{x.toFixed(1)}},${{y.toFixed(1)}}`;
        }}).join(" ");
      }};

      document.getElementById("track-purple").setAttribute("d", buildPath(TRACK_PURPLE));
      document.getElementById("track-green").setAttribute("d", buildPath(TRACK_GREEN));
      document.getElementById("track-yellow").setAttribute("d", buildPath(TRACK_YELLOW));
      document.getElementById("track-pink").setAttribute("d", buildPath(TRACK_PINK));
    }}

    // Render Station Nodes and Labels
    function renderStations() {{
      const stationsLayer = document.getElementById("stations-layer");
      const labelsLayer = document.getElementById("labels-layer");
      const interchangesLayer = document.getElementById("interchanges-layer");

      stationsLayer.innerHTML = "";
      labelsLayer.innerHTML = "";
      interchangesLayer.innerHTML = "";

      STATIONS.forEach(station => {{
        const isPinkStation = station.is_unconfirmed;
        if (isPinkStation && !showPink) return;

        // Check line filter
        if (currentFilter !== "all" && !station.metro_lines.includes(currentFilter)) return;

        const [x, y] = project(station.latitude, station.longitude);
        const lines = station.metro_lines;
        const isInterchange = lines.length > 1;
        const primaryLine = lines[0];
        const mainColor = LINE_COLORS[primaryLine] || "#64748b";

        // Interchange Special Rendering
        if (isInterchange) {{
          const gInt = document.createElementNS("http://www.w3.org/2000/svg", "g");
          gInt.setAttribute("transform", `translate(${{x}}, ${{y}})`);
          
          // Outer animated pulse ring
          const pulseCircle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
          pulseCircle.setAttribute("r", "16");
          pulseCircle.setAttribute("fill", "none");
          pulseCircle.setAttribute("stroke", mainColor);
          pulseCircle.setAttribute("stroke-width", "2");
          pulseCircle.setAttribute("class", "pulse-ring");
          gInt.appendChild(pulseCircle);

          // Outer solid ring
          const ring = document.createElementNS("http://www.w3.org/2000/svg", "circle");
          ring.setAttribute("r", "10");
          ring.setAttribute("fill", "#0f172a");
          ring.setAttribute("stroke", "#ffffff");
          ring.setAttribute("stroke-width", "2.5");
          gInt.appendChild(ring);

          // Multi-color inner dots
          const dot1 = document.createElementNS("http://www.w3.org/2000/svg", "circle");
          dot1.setAttribute("cx", "-3");
          dot1.setAttribute("r", "3.5");
          dot1.setAttribute("fill", LINE_COLORS[lines[0]]);
          gInt.appendChild(dot1);

          const dot2 = document.createElementNS("http://www.w3.org/2000/svg", "circle");
          dot2.setAttribute("cx", "3");
          dot2.setAttribute("r", "3.5");
          dot2.setAttribute("fill", LINE_COLORS[lines[1]]);
          gInt.appendChild(dot2);

          interchangesLayer.appendChild(gInt);
        }}

        // Standard Station Node
        const gNode = document.createElementNS("http://www.w3.org/2000/svg", "g");
        gNode.setAttribute("class", "station-node");
        gNode.setAttribute("data-id", station.id);
        gNode.setAttribute("transform", `translate(${{x}}, ${{y}})`);

        // Node Circle
        const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
        circle.setAttribute("r", isInterchange ? "12" : (isPinkStation ? "5" : "6.5"));
        circle.setAttribute("fill", isInterchange ? "transparent" : mainColor);
        circle.setAttribute("stroke", isPinkStation ? "#f472b6" : "#ffffff");
        circle.setAttribute("stroke-width", isPinkStation ? "1.5" : "2");
        if (isPinkStation) circle.setAttribute("stroke-dasharray", "3,2");
        gNode.appendChild(circle);

        // Events
        gNode.addEventListener("mouseenter", (e) => showTooltip(e, station));
        gNode.addEventListener("mousemove", (e) => moveTooltip(e));
        gNode.addEventListener("mouseleave", hideTooltip);
        gNode.addEventListener("click", () => selectStation(station));

        stationsLayer.appendChild(gNode);

        // Station Label
        const text = document.createElementNS("http://www.w3.org/2000/svg", "text");
        text.setAttribute("x", x + 9);
        text.setAttribute("y", y + 3.5);
        text.setAttribute("fill", isPinkStation ? "#f472b6" : "#cbd5e1");
        text.setAttribute("font-size", isInterchange ? "11.5" : "9.5");
        text.setAttribute("font-weight", isInterchange ? "700" : "500");
        text.setAttribute("letter-spacing", "0.2");
        text.setAttribute("class", "station-label pointer-events-none");
        text.textContent = station.name;
        labelsLayer.appendChild(text);
      }});

      labelsLayer.style.display = showLabels ? "block" : "none";
    }}

    // Tooltip Handlers
    function showTooltip(e, station) {{
      document.getElementById("tooltip-name").textContent = station.name;
      document.getElementById("tooltip-kn").textContent = station.name_kn || station.name;
      document.getElementById("tooltip-coords").textContent = `${{station.latitude.toFixed(6)}}° N, ${{station.longitude.toFixed(6)}}° E`;

      const badges = document.getElementById("tooltip-badges");
      badges.innerHTML = "";
      station.metro_lines.forEach(line => {{
        const span = document.createElement("span");
        span.className = "px-2 py-0.5 rounded text-[10px] font-bold text-white";
        span.style.backgroundColor = LINE_COLORS[line] || "#64748b";
        span.textContent = line;
        badges.appendChild(span);
      }});

      if (station.is_unconfirmed) {{
        const unBadge = document.createElement("span");
        unBadge.className = "px-1.5 py-0.5 rounded text-[10px] font-medium bg-pink-900/60 text-pink-300 border border-pink-500/40";
        unBadge.textContent = "Under Construction";
        badges.appendChild(unBadge);
      }}

      tooltip.classList.remove("opacity-0");
      moveTooltip(e);
    }}

    function moveTooltip(e) {{
      const rect = container.getBoundingClientRect();
      const x = e.clientX - rect.left + 15;
      const y = e.clientY - rect.top + 15;
      tooltip.style.left = `${{Math.min(x, rect.width - 200)}}px`;
      tooltip.style.top = `${{Math.min(y, rect.height - 100)}}px`;
    }}

    function hideTooltip() {{
      tooltip.classList.add("opacity-0");
    }}

    // Station Selection & Drawer
    function selectStation(station) {{
      selectedStation = station;
      const [x, y] = project(station.latitude, station.longitude);

      // Update Drawer Details
      document.getElementById("drawer-title").textContent = station.name;
      document.getElementById("drawer-kn").textContent = station.name_kn || "—";
      document.getElementById("drawer-code").textContent = station.ref || (station.name.substring(0, 4).toUpperCase());
      document.getElementById("drawer-lat").textContent = `${{station.latitude.toFixed(6)}}° N`;
      document.getElementById("drawer-lon").textContent = `${{station.longitude.toFixed(6)}}° E`;
      document.getElementById("drawer-osm-id").textContent = station.id;
      document.getElementById("drawer-osm-link").href = `https://www.openstreetmap.org/${{station.id.replace('osm_', '').replace('_', '/')}}`;

      const badge = document.getElementById("drawer-badge");
      badge.textContent = station.metro_lines.join(" • ");
      badge.style.backgroundColor = (LINE_COLORS[station.metro_lines[0]] || "#64748b") + "33";
      badge.style.color = LINE_COLORS[station.metro_lines[0]] || "#cbd5e1";

      const status = document.getElementById("drawer-status");
      if (station.is_unconfirmed) {{
        status.textContent = "Under Construction (Staged)";
        status.className = "font-semibold text-pink-400";
      }} else {{
        status.textContent = "Operating (100% Reconciled)";
        status.className = "font-semibold text-emerald-400";
      }}

      // Display WakeMe Geofence Circle on Map
      geofenceLayer.setAttribute("transform", `translate(${{x}}, ${{y}})`);
      geofenceLayer.style.display = "block";
      updateGeofenceRadius(geofenceRadiusMeters);

      // Slide out Drawer
      document.getElementById("station-drawer").classList.remove("translate-x-full");
    }}

    function closeDrawer() {{
      document.getElementById("station-drawer").classList.add("translate-x-full");
      geofenceLayer.style.display = "none";
      selectedStation = null;
    }}

    function updateGeofenceRadius(meters) {{
      geofenceRadiusMeters = Number(meters);
      document.getElementById("geofence-radius-val").textContent = `${{geofenceRadiusMeters}} m`;
      const rSvg = metersToSvg(geofenceRadiusMeters);
      geofenceCircle.setAttribute("r", rSvg.toFixed(1));
    }}

    // Filter Handlers
    function setLineFilter(line) {{
      currentFilter = line;
      document.querySelectorAll(".line-btn").forEach(btn => {{
        btn.classList.remove("active", "bg-indigo-600", "text-white");
        btn.classList.add("bg-[var(--background,#0f172a)]");
      }});
      const activeBtn = document.getElementById(`btn-${{line}}`);
      if (activeBtn) {{
        activeBtn.classList.add("active", "bg-indigo-600", "text-white");
        activeBtn.classList.remove("bg-[var(--background,#0f172a)]");
      }}
      renderStations();
    }}

    function togglePinkLine(checked) {{
      showPink = checked;
      document.getElementById("track-pink").style.display = checked ? "block" : "none";
      renderStations();
    }}

    function toggleLabels(checked) {{
      showLabels = checked;
      document.getElementById("labels-layer").style.display = checked ? "block" : "none";
    }}

    // Search Handler
    function handleSearch(query) {{
      const q = query.trim().toLowerCase();
      if (!q) {{
        renderStations();
        return;
      }}
      const match = STATIONS.find(s => s.name.toLowerCase().includes(q) || (s.name_kn && s.name_kn.includes(q)));
      if (match) {{
        // Center view on matching station
        const [x, y] = project(match.latitude, match.longitude);
        scale = 1.6;
        pointX = (SVG_WIDTH / 2) - x * scale;
        pointY = (SVG_HEIGHT / 2) - y * scale;
        updateTransform();
        selectStation(match);
      }}
    }}

    // Pan & Zoom Logic
    function updateTransform() {{
      viewport.setAttribute("transform", `translate(${{pointX}}, ${{pointY}}) scale(${{scale}})`);
    }}

    function zoomBy(factor) {{
      scale = Math.min(Math.max(scale * factor, 0.6), 4.5);
      updateTransform();
    }}

    function resetZoom() {{
      scale = 1;
      pointX = 0;
      pointY = 0;
      updateTransform();
    }}

    container.addEventListener("mousedown", (e) => {{
      if (e.target.closest(".station-node")) return;
      isPanning = true;
      startX = e.clientX - pointX;
      startY = e.clientY - pointY;
    }});

    window.addEventListener("mousemove", (e) => {{
      if (!isPanning) return;
      pointX = e.clientX - startX;
      pointY = e.clientY - startY;
      updateTransform();
    }});

    window.addEventListener("mouseup", () => {{
      isPanning = false;
    }});

    container.addEventListener("wheel", (e) => {{
      e.preventDefault();
      const zoomFactor = e.deltaY < 0 ? 1.15 : 0.85;
      const rect = container.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      const mouseY = e.clientY - rect.top;

      const newScale = Math.min(Math.max(scale * zoomFactor, 0.6), 4.5);
      pointX = mouseX - (mouseX - pointX) * (newScale / scale);
      pointY = mouseY - (mouseY - pointY) * (newScale / scale);
      scale = newScale;
      updateTransform();
    }}, {{ passive: false }});

    // Initial Render
    renderTracks();
    renderStations();
  </script>
</body>
</html>"""

    with open(output_html_path, "w", encoding="utf-8") as f:
        f.write(html_content)

    return output_html_path

if __name__ == "__main__":
    generate_metro_map_html(
        metro_json_path="data/processed/metro_stations.json",
        unconfirmed_json_path="data/processed/unconfirmed_metro_stations.json",
        output_html_path=r"C:\Users\DELL\.gemini\antigravity\brain\ad769eaf-5034-4338-97ce-9b6d1810af9c\namma_metro_visual_map.html"
    )
    # Also save a copy inside data/processed/
    generate_metro_map_html(
        metro_json_path="data/processed/metro_stations.json",
        unconfirmed_json_path="data/processed/unconfirmed_metro_stations.json",
        output_html_path="data/processed/namma_metro_visual_map.html"
    )
    print("Map generated successfully in both artifact and processed directories!")
