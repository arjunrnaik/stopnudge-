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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

        requestRequiredPermissions()
        handleIncomingIntent(intent)

        setContent {
            WakeMeTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()

                // Display user feedback toasts reactively
                LaunchedEffect(viewModel) {
                    viewModel.userMessage.collect { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
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
