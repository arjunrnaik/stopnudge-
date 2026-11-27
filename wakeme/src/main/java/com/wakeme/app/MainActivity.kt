package com.wakeme.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wakeme.app.ui.WakeMeActiveTripScreen
import com.wakeme.app.ui.WakeMeLandingScreen
import com.wakeme.app.ui.WakeMeModeSelectionScreen
import com.wakeme.app.ui.WakeMeScreen
import com.wakeme.app.ui.WakeMeViewModel
import com.wakeme.app.ui.theme.WakeMeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: WakeMeViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled reactively
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashPrefs = getSharedPreferences("wakeme_crash_log", MODE_PRIVATE)
        val prevCrash = crashPrefs.getString("last_crash", null)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            crashPrefs.edit().putString("last_crash", "${e::class.java.simpleName}: ${e.message}\n${e.stackTraceToString().take(900)}").commit()
            defaultHandler?.uncaughtException(t, e)
        }

        requestRequiredPermissions()
        handleIncomingIntent(intent)

        setContent {
            val themePref by viewModel.themePreference.collectAsState()
            val colorSchemePreset by viewModel.colorSchemePreset.collectAsState()
            var crashDialogText by remember { mutableStateOf(prevCrash) }
            WakeMeTheme(themePreference = themePref, colorSchemePreset = colorSchemePreset) {
                if (crashDialogText != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = {
                            crashPrefs.edit().remove("last_crash").apply()
                            crashDialogText = null
                        },
                        title = { androidx.compose.material3.Text("Previous Crash Details") },
                        text = { androidx.compose.material3.Text(crashDialogText ?: "", style = MaterialTheme.typography.bodySmall) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                crashPrefs.edit().remove("last_crash").apply()
                                crashDialogText = null
                            }) { androidx.compose.material3.Text("Dismiss") }
                        }
                    )
                }
                val currentScreen by viewModel.currentScreen.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                // Display accessible, dismissible user feedback via SnackBar
                LaunchedEffect(viewModel) {
                    viewModel.userMessage.collect { msg ->
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            message = msg,
                            duration = SnackbarDuration.Short
                        )
                    }
                }

                // Handle Android Back Navigation gracefully across the 3 screens
                BackHandler(enabled = currentScreen == WakeMeScreen.MODE_SELECTION || currentScreen == WakeMeScreen.ACTIVE_TRIP) {
                    viewModel.navigateTo(WakeMeScreen.SEARCH_LANDING)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "WakeMeScreenTransition"
                        ) { screen ->
                            when (screen) {
                                WakeMeScreen.SEARCH_LANDING -> {
                                    WakeMeLandingScreen(viewModel = viewModel)
                                }
                                WakeMeScreen.MODE_SELECTION -> {
                                    WakeMeModeSelectionScreen(viewModel = viewModel)
                                }
                                WakeMeScreen.ACTIVE_TRIP -> {
                                    WakeMeActiveTripScreen(viewModel = viewModel)
                                }
                            }
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        var text: String? = null
        if (intent.action == Intent.ACTION_SEND) {
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val mainText = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: intent.clipData?.let { clip ->
                    if (clip.itemCount > 0) {
                        clip.getItemAt(0).text?.toString() ?: clip.getItemAt(0).uri?.toString()
                    } else null
                }
                ?: intent.dataString
                ?: intent.data?.toString()

            text = when {
                !subject.isNullOrBlank() && !mainText.isNullOrBlank() -> "$subject\n$mainText"
                !mainText.isNullOrBlank() -> mainText
                !subject.isNullOrBlank() -> subject
                else -> null
            }
        } else if (intent.action == Intent.ACTION_VIEW) {
            text = intent.dataString ?: intent.data?.toString() ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        }

        if (!text.isNullOrBlank()) {
            Toast.makeText(this, "📍 Loading shared location...", Toast.LENGTH_SHORT).show()
            viewModel.handleSharedText(text)
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
