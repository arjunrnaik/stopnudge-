package com.stopnudge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stopnudge.app.data.local.user.TripEntity
import com.stopnudge.app.ui.*
import com.stopnudge.app.ui.theme.StopNudgeTheme
import com.stopnudge.app.util.ParsedCoordinates

class MainActivity : ComponentActivity() {

    private val viewModel: StopNudgeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StopNudgeTheme {
                MainScreen(viewModel)
            }
        }
    }
}

enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Alarm", Icons.Default.Notifications),
    SEARCH("Pick Stop", Icons.Default.Place),
    SAVED("Saved", Icons.Default.Star),
    HISTORY("History", Icons.Default.Info),
    READINESS("Battery", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: StopNudgeViewModel) {
    var selectedScreen by remember { mutableStateOf(Screen.HOME) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "StopNudge",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• Bengaluru",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Screen.values().forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedScreen) {
                Screen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToSearch = { selectedScreen = Screen.SEARCH }
                )
                Screen.SEARCH -> TransitStopPickerScreen(
                    viewModel = viewModel,
                    onTrackingStarted = { selectedScreen = Screen.HOME }
                )
                Screen.SAVED -> SavedScreen(
                    viewModel = viewModel,
                    onPlaceSelected = { selectedScreen = Screen.SEARCH }
                )
                Screen.HISTORY -> HistoryScreen(viewModel = viewModel)
                Screen.READINESS -> BatteryReadinessScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: StopNudgeViewModel,
    onNavigateToSearch: () -> Unit
) {
    val context = LocalContext.current
    val activeTrip by viewModel.activeTrip.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (activeTrip != null) {
            ActiveTrackingCard(
                trip = activeTrip!!,
                onStop = { viewModel.stopTracking(context) }
            )
        } else {
            IdleHomeCard(
                onNavigateToSearch = onNavigateToSearch,
                onQuickPick = { name, lat, lon ->
                    viewModel.setCustomCoordinates(ParsedCoordinates(lat, lon, name))
                    onNavigateToSearch()
                }
            )
        }
    }
}

@Composable
fun ActiveTrackingCard(trip: TripEntity, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ALARM MONITORING ACTIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = trip.destinationName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Tracking via GPS in background. High-volume alarm will sound upon arrival.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dismiss Alarm / Stop Tracking", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun IdleHomeCard(
    onNavigateToSearch: () -> Unit,
    onQuickPick: (String, Double, Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Never Miss Your Stop",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bengaluru's offline destination alarm for Namma Metro & BMTC buses. Loud wake-up alerts that bypass silent mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onNavigateToSearch,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Place, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Destination", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Quick Bengaluru Hubs",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SuggestionChip(
                    onClick = { onQuickPick("Majestic Metro Stn", 12.975708, 77.572876) },
                    label = { Text("Majestic") }
                )
                Spacer(modifier = Modifier.width(6.dp))
                SuggestionChip(
                    onClick = { onQuickPick("Silk Board", 12.916582, 77.620567) },
                    label = { Text("Silk Board") }
                )
                Spacer(modifier = Modifier.width(6.dp))
                SuggestionChip(
                    onClick = { onQuickPick("Indiranagar", 12.9783, 77.6385) },
                    label = { Text("Indiranagar") }
                )
            }
        }
    }
}
