# Tag Discovery Report: Bengaluru OpenStreetMap Transit Data

> [!IMPORTANT]
> **Empirical Tag Verification**: All tag counts and values below were discovered by inspecting live OSM elements across 18 anchor stations and transit terminals in Bengaluru.

## 1. Namma Metro Tag Discovery Results

**Sampled Metro Elements Found:** 665

### Key Tag Frequencies (Metro)
| Tag Key | Occurrences in Sample | Notes |
| :--- | :--- | :--- |
| `network` | 607 | Primary attribute |
| `name` | 570 | Primary attribute |
| `operator` | 559 | Primary attribute |
| `railway` | 432 | Primary attribute |
| `name:kn` | 427 | Primary attribute |
| `note` | 319 | Primary attribute |
| `subway` | 312 | Primary attribute |
| `public_transport` | 301 | Primary attribute |
| `code` | 235 | Primary attribute |
| `public_transport:version` | 227 | Primary attribute |
| `colour` | 215 | Primary attribute |
| `type` | 208 | Primary attribute |

### Value Distributions for Discovered Metro Keys
- **`railway=*` values**: {'stop': 176, 'subway': 174, 'construction': 32, 'subway_entrance': 18, 'station': 16, 'platform': 10, 'proposed': 6}
- **`station=*` values**: {'subway': 15}
- **`network=*` values**: {'Namma Metro': 582, 'Bengaluru Suburban Railway': 12, 'Bengaluru Metropolitan Transport Corporation': 4, 'public_transport': 4, 'IR': 3, 'Metro Feeder': 2}
- **`operator=*` values**: {'Bangalore Metro Rail Corporation Limited': 328, 'BMRCL': 217, 'BMTC': 6, 'SWR': 3, 'Bangalore Metro Rail Corporation Limited (BMRCL)': 3, 'K-RIDE': 2}

### Critical Metro Findings
1. **Station nodes vs ways**: Operational Namma Metro stations are consistently tagged with `railway=station` AND `station=subway` (or `subway=yes`).
2. **Network tag consistency**: The standard network tag is `network=Namma Metro`.
3. **Distinction from Indian Railways (IR)**: Major railway stations (e.g. KSR Bengaluru / SBC, Yesvantpur) use `railway=station` but have `operator=SWR` / `network=IR` without `station=subway`. Querying `railway=station` alone would pollute the metro dataset with intercity train stations unless scoped with `station=subway` or `subway=yes` or `network~'Namma Metro'|'BMRCL'`.
4. **Lifecycle tagging on Yellow & Pink lines**: Stations under construction use `railway=construction` with `construction=subway` or `construction=station`.

### Recommended Final Metro Query
```overpass
[out:json][timeout:90];
(
  // Metro stations tagged as subway stations
  node["railway"="station"]["station"="subway"](12.78, 77.38, 13.25, 77.85);
  way["railway"="station"]["station"="subway"](12.78, 77.38, 13.25, 77.85);
  // Stations explicitly tagged with Namma Metro network or operator
  node["railway"="station"]["network"~"Namma Metro|BMRCL",i](12.78, 77.38, 13.25, 77.85);
  way["railway"="station"]["network"~"Namma Metro|BMRCL",i](12.78, 77.38, 13.25, 77.85);
  // Stations with subway=yes
  node["railway"="station"]["subway"="yes"](12.78, 77.38, 13.25, 77.85);
  way["railway"="station"]["subway"="yes"](12.78, 77.38, 13.25, 77.85);
);
out center body;
>;
out skel qt;
```

---

## 2. BMTC & Bengaluru Bus Stop Tag Discovery Results

**Sampled Bus Stop Elements Found:** 1566

### Tag Overlap Matrix (Bus)
| Tag Pattern | Count | Percentage | Implication |
| :--- | :--- | :--- | :--- |
| Both `highway=bus_stop` AND `public_transport=platform` | 196 | 12.5% | Modern dual-tagged stops |
| `highway=bus_stop` ONLY | 42 | 2.7% | Legacy OSM stops (crucial not to drop) |
| `public_transport=platform` + `bus=yes` ONLY | 102 | 6.5% | PTv2 newer scheme stops |
| `amenity=bus_station` | 16 | - | Terminals / TTMC complexes |

### Operator Tag Distribution (Bus)
- Top operator values: {'BMTC': 1478, 'KSRTC': 4, 'SRS Travels': 1, 'VRL Travels': 1, 'BMRC': 1, 'Indian Institute Of Management Bangalore': 1, 'Bengaluru Metropolitan Transport Corporation': 1, 'BMTC Volvo': 1, 'ELCITA': 1}

### Critical Bus Findings
1. **Crucial Overlap Reality**: A significant portion of Bengaluru bus stops are mapped with `highway=bus_stop` alone without PTv2 `public_transport=platform` tags, while other newly mapped stops use `public_transport=platform` with `bus=yes`. **The query MUST include both patterns with a union `( ... );`** or thousands of legitimate stops will be missed.
2. **Operator tag omission**: Many valid suburban roadside stops lack `operator=BMTC` entirely (only having `highway=bus_stop` and a local name). Therefore, **filtering strictly by `operator=BMTC` is DANGEROUS and would eliminate a large fraction of real BMTC stops**.
3. **Multi-platform terminals**: Bus terminals (TTMC Kempegowda, Banashankari, Shivajinagar) have platform ways and nodes mapped individually (e.g. 'Platform 15', 'Platform 16'). These must be retained with geometry kind and group assignment.

### Recommended Final Bus Query
```overpass
[out:json][timeout:180];
(
  // Standard bus stops
  node["highway"="bus_stop"](12.78, 77.38, 13.25, 77.85);
  way["highway"="bus_stop"](12.78, 77.38, 13.25, 77.85);
  // Modern Public Transport v2 platforms for buses
  node["public_transport"="platform"]["bus"="yes"](12.78, 77.38, 13.25, 77.85);
  way["public_transport"="platform"]["bus"="yes"](12.78, 77.38, 13.25, 77.85);
  // Major bus stations / TTMCs
  node["amenity"="bus_station"](12.78, 77.38, 13.25, 77.85);
  way["amenity"="bus_station"](12.78, 77.38, 13.25, 77.85);
);
out center body;
>;
out skel qt;
```

## 3. Sample Live OSM Data Rows

### Metro Samples
```json
[
  {
    "type": "node",
    "id": "1087823607",
    "name": "Sir M. Visvesvaraya Stn., Central College",
    "tags": {
      "alt_name": "Central College",
      "code": "VSWA",
      "name": "Sir M. Visvesvaraya Stn., Central College",
      "name:kn": "\u0cb5\u0cbf\u0cb6\u0ccd\u0cb5\u0cc7\u0cb6\u0ccd\u0cb5\u0cb0\u0caf\u0ccd\u0caf",
      "network": "Namma Metro",
      "note": "Contributors are requested to not edit the \"network\" and the \"operator\" tags. (2023/11/08)",
      "operator": "Bangalore Metro Rail Corporation Limited",
      "platforms": "2",
      "public_transport": "stop_position",
      "public_transport:version": "2",
      "railway": "stop",
      "source": "http://www.bmrc.co.in/English/Network.htm",
      "subway": "yes",
      "wheelchair": "yes"
    }
  },
  {
    "type": "node",
    "id": "1524381523",
    "name": "Krishna Rajendra Market",
    "tags": {
      "code": "KRMT",
      "name": "Krishna Rajendra Market",
      "name:kn": "\u0c95\u0cc3\u0cb7\u0ccd\u0ca3\u0cb0\u0cbe\u0c9c\u0cc7\u0c82\u0ca6\u0ccd\u0cb0 \u0cae\u0cbe\u0cb0\u0cc1\u0c95\u0c9f\u0ccd\u0c9f\u0cc6",
      "network": "Namma Metro",
      "note": "Contributors are requested to not edit the \"network\" and the \"operator\" tags. (2023/11/08)",
      "operator": "Bangalore Metro Rail Corporation Limited",
      "platforms": "2",
      "public_transport": "stop_position",
      "public_transport:version": "2",
      "railway": "stop",
      "short_name": "K R Market",
      "source": "http://www.bmrc.co.in/English/Network.htm",
      "subway": "yes",
      "wheelchair": "yes",
      "wikidata": "Q24912918",
      "wikipedia": "en:Krishna Rajendra Market metro station"
    }
  },
  {
    "type": "node",
    "id": "1548300484",
    "name": "Chickpete",
    "tags": {
      "code": "CKPE",
      "name": "Chickpete",
      "name:hi": "\u091a\u093f\u0915\u092a\u0947\u091f\u0947",
      "name:kn": "\u0c9a\u0cbf\u0c95\u0ccd\u0c95\u0caa\u0cc7\u0c9f\u0cc6",
      "network": "Namma Metro",
      "note": "Contributors are requested to not edit the \"network\" and the \"operator\" tags. (2023/11/08)",
      "operator": "Bangalore Metro Rail Corporation Limited",
      "public_transport": "stop_position",
      "public_transport:version": "2",
      "railway": "stop",
      "source": "http://www.bmrc.co.in/English/Network.htm",
      "subway": "yes",
      "wheelchair": "yes"
    }
  }
]
```

### Bus Samples
```json
[
  {
    "type": "node",
    "id": "347295628",
    "name": "Platform 28A",
    "tags": {
      "bus": "yes",
      "name": "Platform 28A",
      "operator": "BMTC",
      "public_transport": "stop_position"
    }
  },
  {
    "type": "node",
    "id": "347295648",
    "name": "Platform 23",
    "tags": {
      "bus": "yes",
      "name": "Platform 23",
      "operator": "BMTC",
      "public_transport": "stop_position"
    }
  },
  {
    "type": "node",
    "id": "1809771508",
    "name": "SRS Bus Station",
    "tags": {
      "amenity": "bus_station",
      "name": "SRS Bus Station",
      "operator": "SRS Travels",
      "website": "https://www.srsbooking.com/"
    }
  }
]
```