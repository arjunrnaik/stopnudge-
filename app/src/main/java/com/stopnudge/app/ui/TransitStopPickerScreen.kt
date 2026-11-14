package com.stopnudge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stopnudge.app.data.local.transit.BusStopEntity
import com.stopnudge.app.data.local.transit.MetroStationEntity
import com.stopnudge.app.util.GoogleMapsUrlParser

enum class PickerTab {
    METRO, BUS
}

@Composable
fun TransitStopPickerScreen(
    viewModel: StopNudgeViewModel,
    onTrackingStarted: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val searchQuery by viewModel.searchQuery.collectAsState()
    val metroStations by viewModel.metroStations.collectAsState()
    val busStops by viewModel.busStops.collectAsState()
    val selectedDestination by viewModel.selectedDestination.collectAsState()
    val alertRadiusMeters by viewModel.alertRadiusMeters.collectAsState()

    var activeTab by remember { mutableStateOf(PickerTab.METRO) }
    var showGmapsPasteCard by remember { mutableStateOf(false) }
    var gmapsUrlInput by remember { mutableStateOf("") }
    var gmapsParseError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search metro or bus stop...") },
            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Tabs & Paste Link Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeTab == PickerTab.METRO,
                    onClick = { activeTab = PickerTab.METRO },
                    label = { Text("Metro (${metroStations.size})") },
                    leadingIcon = {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = activeTab == PickerTab.BUS,
                    onClick = { activeTab = PickerTab.BUS },
                    label = { Text("BMTC Bus") },
                    leadingIcon = {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            IconButton(onClick = { showGmapsPasteCard = !showGmapsPasteCard }) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Paste Map Link",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Google Maps Link Paste Card
        if (showGmapsPasteCard) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Paste Google Maps Link / Coordinates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = gmapsUrlInput,
                        onValueChange = {
                            gmapsUrlInput = it
                            gmapsParseError = null
                        },
                        placeholder = { Text("e.g. https://maps.app.goo.gl/... or 12.9716, 77.5946") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboardManager.getText()?.text?.let { clipText ->
                                    gmapsUrlInput = clipText
                                }
                            }) {
                                Icon(Icons.Default.Star, contentDescription = "Paste from clipboard")
                            }
                        }
                    )
                    if (gmapsParseError != null) {
                        Text(
                            text = gmapsParseError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val parsed = GoogleMapsUrlParser.parse(gmapsUrlInput)
                            if (parsed != null) {
                                viewModel.setCustomCoordinates(parsed)
                                showGmapsPasteCard = false
                                gmapsUrlInput = ""
                            } else {
                                gmapsParseError = "Could not detect valid coordinates in link."
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Apply Destination")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Selected Destination Confirmation Banner
        if (selectedDestination != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedDestination!!.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            selectedDestination!!.subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.clearSelectedDestination() }) {
                            Icon(Icons.Default.Close, contentDescription = "Deselect")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Alert Buffer: ${alertRadiusMeters.toInt()} meters before arrival",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(200.0, 500.0, 1000.0, 2000.0).forEach { radius ->
                            FilterChip(
                                selected = alertRadiusMeters == radius,
                                onClick = { viewModel.setAlertRadius(radius) },
                                label = { Text(if (radius >= 1000) "${(radius / 1000).toInt()} km" else "${radius.toInt()} m") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.startTracking(context)
                            onTrackingStarted()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Destination Alarm", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List of Stops
        when (activeTab) {
            PickerTab.METRO -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(metroStations) { station ->
                        MetroStationRow(station = station) {
                            viewModel.selectMetroStation(station)
                        }
                    }
                }
            }
            PickerTab.BUS -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(busStops) { stop ->
                        BusStopRow(stop = stop) {
                            viewModel.selectBusStop(stop)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetroStationRow(station: MetroStationEntity, onClick: () -> Unit) {
    val lineColor = when {
        station.metroLines?.contains("Purple", ignoreCase = true) == true -> Color(0xFF7A2082)
        station.metroLines?.contains("Green", ignoreCase = true) == true -> Color(0xFF008752)
        station.metroLines?.contains("Yellow", ignoreCase = true) == true -> Color(0xFFD4A017)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(lineColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    station.metroLines?.let {
                        Text(
                            text = "$it Line",
                            style = MaterialTheme.typography.bodySmall,
                            color = lineColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    station.nameKn?.let {
                        Text(
                            text = "• $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Icon(
                Icons.Default.Place,
                contentDescription = "Select",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun BusStopRow(stop: BusStopEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stop.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stop.operator ?: "BMTC (Bengaluru)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Icon(
                Icons.Default.Place,
                contentDescription = "Select",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
