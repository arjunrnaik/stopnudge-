"""
Unified Bengaluru Transit Interactive Map (Namma Metro + BMTC Bus Stops).
Visualizes all 83 operating Metro stations and 5,311 BMTC bus stops with
opposite-side directional grouping, TTMC terminals, search, and proximity alarms.
"""

import json
import math
import os
from typing import Dict, Any, List

def build_unified_transit_map():
    metro_path = "data/processed/metro_stations.json"
    unconfirmed_path = "data/processed/unconfirmed_metro_stations.json"
    bus_path = "data/processed/bus_stops.json"
    
    out_artifact = r"C:\Users\DELL\.gemini\antigravity\brain\ad769eaf-5034-4338-97ce-9b6d1810af9c\bengaluru_transit_visual_map.html"
    out_processed = "data/processed/bengaluru_transit_visual_map.html"

    metro_stations = json.load(open(metro_path, encoding="utf-8"))
    unconfirmed = json.load(open(unconfirmed_path, encoding="utf-8"))
    bus_stops = json.load(open(bus_path, encoding="utf-8"))

    # Extract clean Pink Line stations
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

    # Metro tracks
    purple_stations = sorted([s for s in metro_stations if "Purple" in s["metro_lines"]], key=lambda x: x["longitude"])
    green_stations = sorted([s for s in metro_stations if "Green" in s["metro_lines"]], key=lambda x: -x["latitude"])
    yellow_seq = [
        "Rashtreeya Vidyalaya Road", "Ragigudda", "Jayadeva Hospital", "BTM Layout",
        "Central Silk Board", "Bommanahalli", "Hongasandra", "Kudlu Gate",
        "Singasandra", "Hosa Road", "Beratena Agrahara", "Electronic City",
        "Infosys Foundation Konappana Agrahara", "Huskur Road", "Biocon Hebbagodi",
        "Delta Electronics Bommasandra"
    ]
    y_dict = {s["name"]: s for s in metro_stations if "Yellow" in s["metro_lines"]}
    yellow_stations = [y_dict[n] for n in yellow_seq if n in y_dict]
    pink_stations_sorted = sorted(pink_stations, key=lambda x: x["latitude"])

    # Compact representation for bus stops to keep HTML payload lean (~300KB)
    # [id, name, lat, lon, group_id or "", is_ttmc, is_unnamed]
    compact_buses = []
    for b in bus_stops:
        name = b["name"] or ""
        is_unnamed = 1 if not name else 0
        is_ttmc = 1 if any(k in name.lower() for k in ["ttmc", "bus station", "bus stand"]) else 0
        grp = b.get("group_id") or ""
        compact_buses.append([
            b["id"],
            name,
            round(b["latitude"], 5),
            round(b["longitude"], 5),
            grp,
            is_ttmc,
            is_unnamed
        ])

    # Coordinates bounding box (covers all bus stops and metro stations)
    min_lat, max_lat = 12.780, 13.250
    min_lon, max_lon = 77.380, 77.850
    bbox = {
        "min_lat": min_lat,
        "max_lat": max_lat,
        "min_lon": min_lon,
        "max_lon": max_lon
    }

    # Groups map: group_id -> list of stop indices
    groups_map = {}
    for idx, b in enumerate(compact_buses):
        grp = b[4]
        if grp:
            groups_map.setdefault(grp, []).append(idx)

    # Convert to JSON strings
    buses_json = json.dumps(compact_buses, separators=(',', ':'))
    groups_json = json.dumps(groups_map, separators=(',', ':'))
    metro_json = json.dumps(list(metro_stations) + pink_stations, ensure_ascii=False)
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
  <title>Bengaluru Transit Map — Namma Metro & BMTC Bus Stops</title>
  <script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>
  <style>
    @keyframes pulse {{
      0%, 100% {{ transform: scale(1); opacity: 0.8; }}
      50% {{ transform: scale(1.5); opacity: 0.15; }}
    }}
    .pulse-ring {{
      animation: pulse 2.5s infinite ease-in-out;
      transform-origin: center;
    }}
    ::-webkit-scrollbar {{ width: 6px; height: 6px; }}
    ::-webkit-scrollbar-track {{ background: transparent; }}
    ::-webkit-scrollbar-thumb {{ background: rgba(150, 150, 150, 0.3); border-radius: 3px; }}
  </style>
</head>
<body class="bg-[var(--background,#0a0f1d)] text-[var(--foreground,#f8fafc)] antialiased min-h-screen flex flex-col font-sans select-none overflow-hidden">
  
  <!-- Header / Navigation Bar -->
  <header class="bg-[var(--card,#131d33)] border-b border-[var(--border,#1e293b)] px-4 py-2.5 shadow-md z-20 flex flex-wrap items-center justify-between gap-3">
    <div class="flex items-center space-x-3">
      <div class="w-9 h-9 rounded-lg bg-gradient-to-tr from-cyan-500 via-indigo-600 to-amber-500 flex items-center justify-center shadow-lg">
        <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7"/>
        </svg>
      </div>
      <div>
        <h1 class="text-base font-bold tracking-tight text-[var(--foreground,#f8fafc)] flex items-center gap-2">
          Bengaluru Transit Network
          <span class="text-xs px-2 py-0.5 rounded-full bg-cyan-500/20 text-cyan-300 font-medium border border-cyan-500/30">Metro + BMTC</span>
        </h1>
        <p class="text-xs text-[var(--muted-foreground,#94a3b8)]">5,311 BMTC Bus Stops • 83 Namma Metro Stations • 728 Road Median Groups</p>
      </div>
    </div>

    <!-- Mode Selector Tabs -->
    <div class="flex items-center gap-1.5 bg-[var(--background,#0a0f1d)] p-1 rounded-xl border border-[var(--border,#1e293b)] text-xs">
      <button onclick="setTransitMode('all')" id="mode-all" class="mode-btn px-3 py-1 rounded-lg font-medium transition-all bg-indigo-600 text-white shadow-sm">All Transit (5,394)</button>
      <button onclick="setTransitMode('bus')" id="mode-bus" class="mode-btn px-3 py-1 rounded-lg font-medium transition-all text-cyan-300 hover:bg-slate-800">BMTC Bus (5,311)</button>
      <button onclick="setTransitMode('metro')" id="mode-metro" class="mode-btn px-3 py-1 rounded-lg font-medium transition-all text-purple-300 hover:bg-slate-800">Metro Only (83)</button>
      <button onclick="setTransitMode('groups')" id="mode-groups" class="mode-btn px-3 py-1 rounded-lg font-medium transition-all text-amber-300 hover:bg-slate-800">Directional Pairs (728)</button>
    </div>

    <!-- Quick Count Badges -->
    <div class="flex items-center gap-2 text-xs">
      <div class="px-2.5 py-1 rounded-md bg-[var(--background,#0a0f1d)] border border-cyan-500/30 flex items-center gap-1.5">
        <span class="w-2 h-2 rounded-full bg-cyan-400"></span>
        <span class="text-[var(--muted-foreground,#94a3b8)]">BMTC:</span>
        <span class="font-semibold text-cyan-300" id="stat-bus-count">5,311</span>
      </div>
      <div class="px-2.5 py-1 rounded-md bg-[var(--background,#0a0f1d)] border border-purple-500/30 flex items-center gap-1.5">
        <span class="w-2 h-2 rounded-full bg-purple-400"></span>
        <span class="text-[var(--muted-foreground,#94a3b8)]">Metro:</span>
        <span class="font-semibold text-purple-300" id="stat-metro-count">83</span>
      </div>
      <div class="px-2.5 py-1 rounded-md bg-[var(--background,#0a0f1d)] border border-amber-500/30 flex items-center gap-1.5">
        <span class="w-2 h-2 rounded-full bg-amber-400"></span>
        <span class="text-[var(--muted-foreground,#94a3b8)]">Groups:</span>
        <span class="font-semibold text-amber-300">728</span>
      </div>
    </div>
  </header>

  <!-- Controls Toolbar -->
  <div class="bg-[var(--card,#131d33)]/90 backdrop-blur-md border-b border-[var(--border,#1e293b)] px-4 py-2 z-10 flex flex-wrap items-center justify-between gap-3 text-xs">
    
    <!-- Filter Options -->
    <div class="flex items-center gap-2 overflow-x-auto">
      <span class="text-[var(--muted-foreground,#94a3b8)] font-medium">Layers:</span>
      
      <label class="flex items-center gap-1.5 cursor-pointer text-cyan-300 hover:text-cyan-200">
        <input type="checkbox" id="layer-bus" onchange="toggleLayer('bus', this.checked)" checked class="rounded text-cyan-500 focus:ring-0">
        <span>Bus Stops</span>
      </label>

      <label class="flex items-center gap-1.5 cursor-pointer text-purple-300 hover:text-purple-200">
        <input type="checkbox" id="layer-metro" onchange="toggleLayer('metro', this.checked)" checked class="rounded text-purple-500 focus:ring-0">
        <span>Metro Lines</span>
      </label>

      <label class="flex items-center gap-1.5 cursor-pointer text-amber-300 hover:text-amber-200">
        <input type="checkbox" id="layer-groups" onchange="toggleLayer('groups', this.checked)" checked class="rounded text-amber-500 focus:ring-0">
        <span>Road Median Links</span>
      </label>

      <label class="flex items-center gap-1.5 cursor-pointer text-pink-300 hover:text-pink-200 ml-2 pl-2 border-l border-[var(--border,#1e293b)]">
        <input type="checkbox" id="layer-pink" onchange="toggleLayer('pink', this.checked)" class="rounded text-pink-500 focus:ring-0">
        <span>Pink Metro (u/c)</span>
      </label>
    </div>

    <!-- Search & Zoom -->
    <div class="flex items-center gap-3">
      <!-- Universal Transit Search -->
      <div class="relative w-64">
        <input type="text" id="transit-search" oninput="handleSearch(this.value)" placeholder="Search 5,300+ bus stops & metro..." 
          class="w-full bg-[var(--background,#0a0f1d)] border border-[var(--border,#1e293b)] text-xs rounded-lg pl-7 pr-3 py-1.5 focus:outline-none focus:border-cyan-500 text-[var(--foreground,#f8fafc)] placeholder-[var(--muted-foreground,#64748b)]">
        <svg class="w-3.5 h-3.5 text-[var(--muted-foreground,#64748b)] absolute left-2 top-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
        </svg>
      </div>

      <!-- Zoom Controls -->
      <div class="flex items-center border border-[var(--border,#1e293b)] rounded-lg overflow-hidden bg-[var(--background,#0a0f1d)]">
        <button onclick="zoomBy(1.3)" title="Zoom In" class="px-2 py-1 hover:bg-[var(--card,#131d33)] text-white font-bold">+</button>
        <button onclick="zoomBy(0.75)" title="Zoom Out" class="px-2 py-1 hover:bg-[var(--card,#131d33)] text-white font-bold border-l border-[var(--border,#1e293b)]">-</button>
        <button onclick="resetZoom()" title="Fit Bengaluru Bounds" class="px-2 py-1 hover:bg-[var(--card,#131d33)] text-xs border-l border-[var(--border,#1e293b)]">⟲</button>
      </div>
    </div>
  </div>

  <!-- Main Canvas & Map Viewport -->
  <div class="relative flex-1 w-full h-full overflow-hidden bg-[var(--background,#070a13)] flex">
    
    <!-- Fast HTML5 Canvas Layer for 5,311 Bus Stops and Median Links -->
    <div id="map-container" class="relative flex-1 w-full h-full cursor-grab active:cursor-grabbing overflow-hidden">
      <canvas id="transit-canvas" class="w-full h-full block"></canvas>

      <!-- Hover Tooltip -->
      <div id="tooltip" class="absolute pointer-events-none transition-opacity duration-150 opacity-0 bg-[var(--card,#131d33)] text-[var(--foreground,#f8fafc)] border border-[var(--border,#334155)] text-xs rounded-lg px-3 py-2 shadow-2xl z-30 min-w-[180px]">
        <div id="tooltip-mode" class="text-[10px] uppercase font-bold tracking-wider text-cyan-400">BMTC BUS STOP</div>
        <div id="tooltip-name" class="font-bold text-white text-sm">Station Name</div>
        <div id="tooltip-extra" class="text-[var(--muted-foreground,#94a3b8)] text-[11px] mt-1 font-mono">12.9716° N, 77.5946° E</div>
      </div>

      <!-- Zoom Indicator Pill -->
      <div class="absolute bottom-4 left-4 bg-[var(--card,#131d33)]/80 backdrop-blur-sm border border-[var(--border,#1e293b)] px-3 py-1.5 rounded-lg text-[11px] text-[var(--muted-foreground,#94a3b8)] flex items-center gap-3">
        <span>Zoom: <strong id="zoom-level-text" class="text-white">1.0x</strong></span>
        <span>•</span>
        <span>Visible: <strong id="visible-stops-count" class="text-cyan-300">5,394</strong></span>
        <span>•</span>
        <span>Scroll to Zoom, Drag to Pan</span>
      </div>
    </div>

    <!-- Stop Details Drawer (Slides out when a stop or metro station is clicked) -->
    <div id="stop-drawer" class="w-84 bg-[var(--card,#131d33)] border-l border-[var(--border,#1e293b)] shadow-2xl flex flex-col z-20 transition-all duration-300 transform translate-x-full">
      
      <!-- Drawer Header -->
      <div class="p-4 border-b border-[var(--border,#1e293b)] flex items-start justify-between">
        <div>
          <span id="drawer-badge" class="px-2 py-0.5 rounded text-[11px] font-semibold tracking-wide uppercase bg-cyan-500/20 text-cyan-300 border border-cyan-500/30">BMTC Bus Stop</span>
          <h2 id="drawer-title" class="text-base font-bold text-white mt-1.5 leading-snug">Stop Name</h2>
          <p id="drawer-subtitle" class="text-xs text-[var(--muted-foreground,#94a3b8)]">Bangalore Metropolitan Transport Corporation</p>
        </div>
        <button onclick="closeDrawer()" class="text-[var(--muted-foreground,#94a3b8)] hover:text-white p-1 rounded-md hover:bg-slate-700">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
        </button>
      </div>

      <!-- Drawer Content -->
      <div class="p-4 space-y-4 flex-1 overflow-y-auto text-xs">
        
        <!-- Key Metrics Cards -->
        <div class="grid grid-cols-2 gap-2">
          <div class="bg-[var(--background,#0a0f1d)] p-2.5 rounded-lg border border-[var(--border,#1e293b)]">
            <span class="text-[var(--muted-foreground,#94a3b8)] block text-[11px]">Directional Group</span>
            <span id="drawer-group" class="font-mono font-semibold text-amber-300 text-xs">Single / None</span>
          </div>
          <div class="bg-[var(--background,#0a0f1d)] p-2.5 rounded-lg border border-[var(--border,#1e293b)]">
            <span class="text-[var(--muted-foreground,#94a3b8)] block text-[11px]">Road Median Pair</span>
            <span id="drawer-pair-status" class="font-semibold text-cyan-300">Preserved (Dual)</span>
          </div>
        </div>

        <!-- Coordinates & Geometry -->
        <div class="bg-[var(--background,#0a0f1d)] p-3 rounded-lg border border-[var(--border,#1e293b)] space-y-1.5">
          <div class="flex justify-between items-center">
            <span class="text-[var(--muted-foreground,#94a3b8)]">Latitude</span>
            <span id="drawer-lat" class="font-mono text-slate-200">12.975700° N</span>
          </div>
          <div class="flex justify-between items-center">
            <span class="text-[var(--muted-foreground,#94a3b8)]">Longitude</span>
            <span id="drawer-lon" class="font-mono text-slate-200">77.572800° E</span>
          </div>
          <div class="flex justify-between items-center">
            <span class="text-[var(--muted-foreground,#94a3b8)]">OSM Source ID</span>
            <span id="drawer-osm-id" class="font-mono text-cyan-400">osm_node_12345</span>
          </div>
        </div>

        <!-- WakeMe Geofence Proximity Alarm Simulator -->
        <div class="bg-cyan-950/40 border border-cyan-500/30 p-3 rounded-lg space-y-2">
          <div class="flex items-center justify-between">
            <span class="font-semibold text-cyan-300 flex items-center gap-1.5">
              <svg class="w-3.5 h-3.5 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/></svg>
              WakeMe Proximity Alert
            </span>
            <span id="geofence-radius-val" class="font-mono font-bold text-cyan-200">300 m</span>
          </div>
          <p class="text-[11px] text-slate-400">Visualizes Android app proximity alarm trigger threshold:</p>
          <input type="range" id="geofence-slider" min="100" max="1500" step="50" value="300" oninput="updateGeofenceRadius(this.value)" class="w-full accent-cyan-500">
          <div class="flex justify-between text-[10px] text-slate-400">
            <span>100m (Close)</span>
            <span>500m (Standard)</span>
            <span>1500m (Express)</span>
          </div>
        </div>

        <!-- External Link -->
        <a id="drawer-osm-link" href="#" target="_blank" class="block w-full text-center py-2 px-3 rounded-lg bg-[var(--background,#0a0f1d)] hover:bg-slate-700 border border-[var(--border,#1e293b)] font-medium text-slate-300 transition-colors">
          View Raw Node on OpenStreetMap ↗
        </a>
      </div>
      
      <!-- Drawer Footer -->
      <div class="p-3 border-t border-[var(--border,#1e293b)] bg-[var(--background,#0a0f1d)] text-[11px] text-[var(--muted-foreground,#94a3b8)] flex items-center justify-between">
        <span>Room DB: wakeme_transit.db</span>
        <span class="text-cyan-400">Indexed (Mode & Group)</span>
      </div>
    </div>
  </div>

  <!-- Embedded Data & Canvas Rendering Engine -->
  <script>
    // Embedded Datasets
    const BUS_STOPS = {buses_json};
    const GROUPS = {groups_json};
    const METRO_STATIONS = {metro_json};
    const TRACK_PURPLE = {purple_track};
    const TRACK_GREEN = {green_track};
    const TRACK_YELLOW = {yellow_track};
    const TRACK_PINK = {pink_track};
    const BBOX = {bbox_json};

    // Color Constants
    const COLOR_PURPLE = "#9333ea";
    const COLOR_GREEN = "#10b981";
    const COLOR_YELLOW = "#f59e0b";
    const COLOR_PINK = "#ec4899";
    const COLOR_BUS_REGULAR = "rgba(6, 182, 212, 0.65)";
    const COLOR_BUS_TTMC = "#f59e0b";
    const COLOR_GROUP_LINK = "rgba(245, 158, 11, 0.4)";

    // Layer State
    let showBus = true;
    let showMetro = true;
    let showGroups = true;
    let showPink = false;
    let currentMode = "all"; // 'all', 'bus', 'metro', 'groups'
    let selectedStop = null;
    let geofenceRadiusMeters = 300;

    // Viewport Pan / Zoom State
    let scale = 1.0;
    let offsetX = 0;
    let offsetY = 0;
    let isDragging = false;
    let dragStartX = 0;
    let dragStartY = 0;

    const canvas = document.getElementById("transit-canvas");
    const ctx = canvas.getContext("2d");
    const container = document.getElementById("map-container");
    const tooltip = document.getElementById("tooltip");

    // Coordinate Math
    let canvasWidth = 0;
    let canvasHeight = 0;

    function resizeCanvas() {{
      const rect = container.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      canvasWidth = rect.width;
      canvasHeight = rect.height;
      canvas.width = canvasWidth * dpr;
      canvas.height = canvasHeight * dpr;
      ctx.scale(dpr, dpr);
      render();
    }}

    window.addEventListener("resize", resizeCanvas);

    // Lat/Lon to Base Screen Coordinates
    function project(lat, lon) {{
      const pad = 30;
      const x = pad + ((lon - BBOX.min_lon) / (BBOX.max_lon - BBOX.min_lon)) * (canvasWidth - 2 * pad);
      const y = pad + ((BBOX.max_lat - lat) / (BBOX.max_lat - BBOX.min_lat)) * (canvasHeight - 2 * pad);
      return [x, y];
    }}

    // Screen Coords to Pan/Zoom Transform
    function toScreen(x, y) {{
      return [x * scale + offsetX, y * scale + offsetY];
    }}

    function toWorld(screenX, screenY) {{
      return [(screenX - offsetX) / scale, (screenY - offsetY) / scale];
    }}

    function metersToPixels(meters) {{
      const latRangeKm = (BBOX.max_lat - BBOX.min_lat) * 110.8;
      const pxPerMeter = (canvasHeight - 60) / (latRangeKm * 1000);
      return meters * pxPerMeter * scale;
    }}

    // Main Render Loop
    function render() {{
      ctx.clearRect(0, 0, canvasWidth, canvasHeight);

      // Background subtle grid
      ctx.strokeStyle = "rgba(255, 255, 255, 0.03)";
      ctx.lineWidth = 1;
      const gridSize = 50 * scale;
      const startX = offsetX % gridSize;
      const startY = offsetY % gridSize;
      ctx.beginPath();
      for (let x = startX; x < canvasWidth; x += gridSize) {{
        ctx.moveTo(x, 0); ctx.lineTo(x, canvasHeight);
      }}
      for (let y = startY; y < canvasHeight; y += gridSize) {{
        ctx.moveTo(0, y); ctx.lineTo(canvasWidth, y);
      }}
      ctx.stroke();

      // Outer Ring Road indicator
      const [orrCenterX, orrCenterY] = toScreen(...project(12.975, 77.600));
      ctx.save();
      ctx.beginPath();
      ctx.ellipse(orrCenterX, orrCenterY, 180 * scale, 150 * scale, 0, 0, Math.PI * 2);
      ctx.strokeStyle = "rgba(255, 255, 255, 0.05)";
      ctx.lineWidth = 1.5;
      ctx.setLineDash([4, 6]);
      ctx.stroke();
      ctx.restore();

      // 1. Draw Median Links for Opposite-Side Bus Groups
      if (showBus && showGroups && (currentMode === "all" || currentMode === "bus" || currentMode === "groups")) {{
        ctx.save();
        ctx.strokeStyle = COLOR_GROUP_LINK;
        ctx.lineWidth = Math.max(1, 1.5 * Math.min(scale, 2));
        ctx.setLineDash([3, 3]);
        ctx.beginPath();
        for (const grpId in GROUPS) {{
          const indices = GROUPS[grpId];
          if (indices.length >= 2) {{
            const p1 = toScreen(...project(BUS_STOPS[indices[0]][2], BUS_STOPS[indices[0]][3]));
            const p2 = toScreen(...project(BUS_STOPS[indices[1]][2], BUS_STOPS[indices[1]][3]));
            ctx.moveTo(p1[0], p1[1]);
            ctx.lineTo(p2[0], p2[1]);
          }}
        }}
        ctx.stroke();
        ctx.restore();
      }}

      // 2. Draw BMTC Bus Stops (Canvas Optimized)
      let visibleBusCount = 0;
      if (showBus && (currentMode === "all" || currentMode === "bus" || currentMode === "groups")) {{
        const rRegular = Math.max(1.5, Math.min(4, 2.0 * Math.sqrt(scale)));
        const rTTMC = Math.max(3, Math.min(7, 4.0 * Math.sqrt(scale)));

        for (let i = 0; i < BUS_STOPS.length; i++) {{
          const b = BUS_STOPS[i];
          const isGrouped = b[4] !== "";
          const isTTMC = b[5] === 1;

          if (currentMode === "groups" && !isGrouped) continue;

          const [sx, sy] = toScreen(...project(b[2], b[3]));

          // Frustum Culling
          if (sx < -10 || sx > canvasWidth + 10 || sy < -10 || sy > canvasHeight + 10) continue;
          visibleBusCount++;

          ctx.beginPath();
          if (isTTMC) {{
            ctx.arc(sx, sy, rTTMC, 0, Math.PI * 2);
            ctx.fillStyle = COLOR_BUS_TTMC;
            ctx.fill();
            ctx.strokeStyle = "#ffffff";
            ctx.lineWidth = 1.2;
            ctx.stroke();
          }} else if (isGrouped) {{
            ctx.arc(sx, sy, rRegular, 0, Math.PI * 2);
            ctx.fillStyle = "#38bdf8";
            ctx.fill();
          }} else {{
            ctx.arc(sx, sy, rRegular, 0, Math.PI * 2);
            ctx.fillStyle = COLOR_BUS_REGULAR;
            ctx.fill();
          }}
        }}
      }}

      // 3. Draw Metro Tracks
      if (showMetro && (currentMode === "all" || currentMode === "metro")) {{
        const drawTrack = (track, color, dashed = false) => {{
          if (track.length === 0) return;
          ctx.save();
          ctx.strokeStyle = color;
          ctx.lineWidth = Math.max(3, 4.5 * Math.min(scale, 2));
          ctx.lineCap = "round";
          ctx.lineJoin = "round";
          if (dashed) ctx.setLineDash([6, 6]);
          ctx.beginPath();
          track.forEach((pt, i) => {{
            const [sx, sy] = toScreen(...project(pt[0], pt[1]));
            if (i === 0) ctx.moveTo(sx, sy);
            else ctx.lineTo(sx, sy);
          }});
          ctx.stroke();
          ctx.restore();
        }};

        drawTrack(TRACK_PURPLE, COLOR_PURPLE);
        drawTrack(TRACK_GREEN, COLOR_GREEN);
        drawTrack(TRACK_YELLOW, COLOR_YELLOW);
        if (showPink) drawTrack(TRACK_PINK, COLOR_PINK, true);

        // 4. Draw Metro Stations
        METRO_STATIONS.forEach(station => {{
          if (station.is_unconfirmed && !showPink) return;
          const [sx, sy] = toScreen(...project(station.latitude, station.longitude));
          const isInterchange = station.metro_lines.length > 1;

          if (isInterchange) {{
            // Interchange Outer Pulse
            ctx.beginPath();
            ctx.arc(sx, sy, 10 * Math.min(scale, 1.8), 0, Math.PI * 2);
            ctx.fillStyle = "rgba(129, 140, 248, 0.25)";
            ctx.fill();

            // Interchange Core
            ctx.beginPath();
            ctx.arc(sx, sy, 6 * Math.min(scale, 1.5), 0, Math.PI * 2);
            ctx.fillStyle = "#ffffff";
            ctx.fill();
            ctx.strokeStyle = "#0f172a";
            ctx.lineWidth = 2;
            ctx.stroke();
          }} else {{
            ctx.beginPath();
            ctx.arc(sx, sy, 4.5 * Math.min(scale, 1.5), 0, Math.PI * 2);
            ctx.fillStyle = station.is_unconfirmed ? COLOR_PINK : (station.metro_lines.includes("Purple") ? COLOR_PURPLE : (station.metro_lines.includes("Green") ? COLOR_GREEN : COLOR_YELLOW));
            ctx.fill();
            ctx.strokeStyle = "#ffffff";
            ctx.lineWidth = 1.5;
            ctx.stroke();
          }}

          // Labels at higher zoom
          if (scale >= 1.4 || isInterchange) {{
            ctx.font = isInterchange ? "bold 11px sans-serif" : "10px sans-serif";
            ctx.fillStyle = isInterchange ? "#ffffff" : "#cbd5e1";
            ctx.fillText(station.name, sx + 8, sy + 3);
          }}
        }});
      }}

      // 5. Draw Selected Stop Geofence Circle
      if (selectedStop) {{
        const [sx, sy] = toScreen(...project(selectedStop.latitude, selectedStop.longitude));
        const radiusPx = metersToPixels(geofenceRadiusMeters);

        ctx.save();
        ctx.beginPath();
        ctx.arc(sx, sy, radiusPx, 0, Math.PI * 2);
        ctx.fillStyle = "rgba(6, 182, 212, 0.18)";
        ctx.fill();
        ctx.strokeStyle = "#06b6d4";
        ctx.lineWidth = 2;
        ctx.setLineDash([5, 4]);
        ctx.stroke();

        // Center Marker
        ctx.beginPath();
        ctx.arc(sx, sy, 4, 0, Math.PI * 2);
        ctx.fillStyle = "#ffffff";
        ctx.fill();
        ctx.restore();
      }}

      // Update HUD count
      document.getElementById("visible-stops-count").textContent = visibleBusCount + (showMetro ? METRO_STATIONS.length : 0);
      document.getElementById("zoom-level-text").textContent = scale.toFixed(1) + "x";
    }}

    // Spatial Hit-Testing for Click & Hover
    function findNearestStop(screenX, screenY) {{
      const [wx, wy] = toWorld(screenX, screenY);
      const hitRadius = 14 / scale;
      let closest = null;
      let minDistSq = hitRadius * hitRadius;

      // Check Metro first
      if (showMetro) {{
        for (const m of METRO_STATIONS) {{
          if (m.is_unconfirmed && !showPink) continue;
          const [px, py] = project(m.latitude, m.longitude);
          const d2 = (wx - px) * (wx - px) + (wy - py) * (wy - py);
          if (d2 < minDistSq) {{
            minDistSq = d2;
            closest = {{ type: "METRO", data: m }};
          }}
        }}
      }}

      // Check Bus Stops
      if (showBus && !closest) {{
        for (let i = 0; i < BUS_STOPS.length; i++) {{
          const b = BUS_STOPS[i];
          if (currentMode === "groups" && b[4] === "") continue;
          const [px, py] = project(b[2], b[3]);
          const d2 = (wx - px) * (wx - px) + (wy - py) * (wy - py);
          if (d2 < minDistSq) {{
            minDistSq = d2;
            closest = {{ type: "BUS", data: b }};
          }}
        }}
      }}

      return closest;
    }}

    // Mouse Interaction Handlers
    container.addEventListener("mousedown", (e) => {{
      isDragging = true;
      dragStartX = e.clientX - offsetX;
      dragStartY = e.clientY - offsetY;
    }});

    window.addEventListener("mousemove", (e) => {{
      if (isDragging) {{
        offsetX = e.clientX - dragStartX;
        offsetY = e.clientY - dragStartY;
        render();
        return;
      }}

      // Hover Tooltip
      const rect = container.getBoundingClientRect();
      const mx = e.clientX - rect.left;
      const my = e.clientY - rect.top;
      const hit = findNearestStop(mx, my);

      if (hit) {{
        tooltip.classList.remove("opacity-0");
        tooltip.style.left = `${{Math.min(mx + 15, canvasWidth - 220)}}px`;
        tooltip.style.top = `${{Math.min(my + 15, canvasHeight - 90)}}px`;

        if (hit.type === "METRO") {{
          const m = hit.data;
          document.getElementById("tooltip-mode").textContent = "NAMMA METRO STATION";
          document.getElementById("tooltip-mode").className = "text-[10px] uppercase font-bold tracking-wider text-purple-400";
          document.getElementById("tooltip-name").textContent = m.name;
          document.getElementById("tooltip-extra").textContent = `${{m.metro_lines.join(", ")}} • ${{m.latitude.toFixed(5)}}° N, ${{m.longitude.toFixed(5)}}° E`;
        }} else {{
          const b = hit.data;
          document.getElementById("tooltip-mode").textContent = b[5] === 1 ? "BMTC BUS TERMINAL (TTMC)" : "BMTC BUS STOP";
          document.getElementById("tooltip-mode").className = "text-[10px] uppercase font-bold tracking-wider " + (b[5] === 1 ? "text-amber-400" : "text-cyan-400");
          document.getElementById("tooltip-name").textContent = b[1] || "(Unnamed Bus Stop)";
          document.getElementById("tooltip-extra").textContent = `${{b[4] ? "Grouped Pair • " : ""}}${{b[2].toFixed(5)}}° N, ${{b[3].toFixed(5)}}° E`;
        }}
      }} else {{
        tooltip.classList.add("opacity-0");
      }}
    }});

    window.addEventListener("mouseup", () => {{
      isDragging = false;
    }});

    container.addEventListener("click", (e) => {{
      const rect = container.getBoundingClientRect();
      const mx = e.clientX - rect.left;
      const my = e.clientY - rect.top;
      const hit = findNearestStop(mx, my);
      if (hit) {{
        selectStop(hit);
      }}
    }});

    container.addEventListener("wheel", (e) => {{
      e.preventDefault();
      const zoomFactor = e.deltaY < 0 ? 1.2 : 0.82;
      const rect = container.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      const mouseY = e.clientY - rect.top;

      const newScale = Math.min(Math.max(scale * zoomFactor, 0.7), 15.0);
      offsetX = mouseX - (mouseX - offsetX) * (newScale / scale);
      offsetY = mouseY - (mouseY - offsetY) * (newScale / scale);
      scale = newScale;
      render();
    }}, {{ passive: false }});

    // Stop Details Drawer Logic
    function selectStop(hit) {{
      if (hit.type === "METRO") {{
        const m = hit.data;
        selectedStop = {{ latitude: m.latitude, longitude: m.longitude }};
        document.getElementById("drawer-badge").textContent = `Metro • ${{m.metro_lines.join(" • ")}}`;
        document.getElementById("drawer-badge").className = "px-2 py-0.5 rounded text-[11px] font-semibold tracking-wide uppercase bg-purple-500/20 text-purple-300 border border-purple-500/30";
        document.getElementById("drawer-title").textContent = m.name;
        document.getElementById("drawer-subtitle").textContent = m.name_kn || "Bangalore Metro Rail Corporation";
        document.getElementById("drawer-group").textContent = m.ref || "METRO";
        document.getElementById("drawer-pair-status").textContent = "Operating Line";
        document.getElementById("drawer-lat").textContent = `${{m.latitude.toFixed(6)}}° N`;
        document.getElementById("drawer-lon").textContent = `${{m.longitude.toFixed(6)}}° E`;
        document.getElementById("drawer-osm-id").textContent = m.id;
        document.getElementById("drawer-osm-link").href = `https://www.openstreetmap.org/${{m.id.replace('osm_', '').replace('_', '/')}}`;
      }} else {{
        const b = hit.data;
        selectedStop = {{ latitude: b[2], longitude: b[3] }};
        document.getElementById("drawer-badge").textContent = b[5] === 1 ? "BMTC Major Terminal / TTMC" : "BMTC Bus Stop";
        document.getElementById("drawer-badge").className = b[5] === 1 ? "px-2 py-0.5 rounded text-[11px] font-semibold tracking-wide uppercase bg-amber-500/20 text-amber-300 border border-amber-500/30" : "px-2 py-0.5 rounded text-[11px] font-semibold tracking-wide uppercase bg-cyan-500/20 text-cyan-300 border border-cyan-500/30";
        document.getElementById("drawer-title").textContent = b[1] || "(Unnamed Bus Stop)";
        document.getElementById("drawer-subtitle").textContent = "Bangalore Metropolitan Transport Corporation";
        document.getElementById("drawer-group").textContent = b[4] ? b[4].substring(0, 15) : "Single Stop";
        document.getElementById("drawer-pair-status").textContent = b[4] ? "Opposite Pair Linked" : "Isolated Point";
        document.getElementById("drawer-lat").textContent = `${{b[2].toFixed(6)}}° N`;
        document.getElementById("drawer-lon").textContent = `${{b[3].toFixed(6)}}° E`;
        document.getElementById("drawer-osm-id").textContent = b[0];
        document.getElementById("drawer-osm-link").href = `https://www.openstreetmap.org/${{b[0].replace('osm_', '').replace('_', '/')}}`;
      }}

      document.getElementById("stop-drawer").classList.remove("translate-x-full");
      render();
    }}

    function closeDrawer() {{
      document.getElementById("stop-drawer").classList.add("translate-x-full");
      selectedStop = null;
      render();
    }}

    function updateGeofenceRadius(meters) {{
      geofenceRadiusMeters = Number(meters);
      document.getElementById("geofence-radius-val").textContent = `${{geofenceRadiusMeters}} m`;
      render();
    }}

    // Filter Handlers
    function setTransitMode(mode) {{
      currentMode = mode;
      document.querySelectorAll(".mode-btn").forEach(btn => {{
        btn.classList.remove("bg-indigo-600", "text-white");
        btn.classList.add("text-slate-400");
      }});
      const activeBtn = document.getElementById(`mode-${{mode}}`);
      if (activeBtn) {{
        activeBtn.classList.add("bg-indigo-600", "text-white");
        activeBtn.classList.remove("text-slate-400");
      }}

      if (mode === "metro") {{
        showMetro = true; showBus = false;
      }} else if (mode === "bus") {{
        showMetro = false; showBus = true;
      }} else {{
        showMetro = true; showBus = true;
      }}
      render();
    }}

    function toggleLayer(layer, checked) {{
      if (layer === "bus") showBus = checked;
      if (layer === "metro") showMetro = checked;
      if (layer === "groups") showGroups = checked;
      if (layer === "pink") showPink = checked;
      render();
    }}

    function zoomBy(factor) {{
      scale = Math.min(Math.max(scale * factor, 0.7), 15.0);
      render();
    }}

    function resetZoom() {{
      scale = 1.0;
      offsetX = 0;
      offsetY = 0;
      render();
    }}

    // Universal Search
    function handleSearch(query) {{
      const q = query.trim().toLowerCase();
      if (!q) return;

      // 1. Search Metro
      const metroMatch = METRO_STATIONS.find(m => m.name.toLowerCase().includes(q));
      if (metroMatch) {{
        const [px, py] = project(metroMatch.latitude, metroMatch.longitude);
        scale = 3.5;
        offsetX = (canvasWidth / 2) - px * scale;
        offsetY = (canvasHeight / 2) - py * scale;
        selectStop({{ type: "METRO", data: metroMatch }});
        render();
        return;
      }}

      // 2. Search Bus
      const busMatch = BUS_STOPS.find(b => b[1].toLowerCase().includes(q));
      if (busMatch) {{
        const [px, py] = project(busMatch[2], busMatch[3]);
        scale = 4.0;
        offsetX = (canvasWidth / 2) - px * scale;
        offsetY = (canvasHeight / 2) - py * scale;
        selectStop({{ type: "BUS", data: busMatch }});
        render();
      }}
    }}

    // Initial Load
    window.addEventListener("DOMContentLoaded", () => {{
      resizeCanvas();
    }});
  </script>
</body>
</html>"""

    with open(out_artifact, "w", encoding="utf-8") as f:
        f.write(html_content)

    with open(out_processed, "w", encoding="utf-8") as f:
        f.write(html_content)

    print("Unified Transit Map generated successfully!")

if __name__ == "__main__":
    build_unified_transit_map()
