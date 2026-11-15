package com.enes.liquidtube

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.enes.liquidtube.playback.PlayerConnection
import com.enes.liquidtube.ui.navigation.AppScaffold
import com.enes.liquidtube.ui.theme.LiquidTubeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerConnection: PlayerConnection

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A notification (new release) asked to open an album or single. */
    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty().trim()
            // An empty query ("play some music") resumes what you were listening to.
            if (query.isEmpty()) playerConnection.togglePlayPause().also { intent.action = null; return }
            playerConnection.playFromQuery(query)
            intent.action = null
            return
        }
        intent?.getStringExtra(EXTRA_OPEN_COLLECTION)?.let {
            com.enes.liquidtube.ui.navigation.PendingNav.collection.value = it
            intent.removeExtra(EXTRA_OPEN_COLLECTION)
        }
        if (intent?.getBooleanExtra(EXTRA_SHOW_UPDATE, false) == true) {
            // Opened from the "Update available" notification: let the sheet show again.
            com.enes.liquidtube.data.AppUpdater.requestPrompt()
            intent.removeExtra(EXTRA_SHOW_UPDATE)
        }
    }

    companion object {
        const val EXTRA_OPEN_COLLECTION = "open_collection"
        const val EXTRA_SHOW_UPDATE = "show_update"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        playerConnection.initialize()
        handleIntent(intent)
        // Keep the system splash (icon on black) up until the in-app launch screen has drawn,
        // so there's no empty black frame between the two.
        if (savedInstanceState == null) {
            val content = findViewById<android.view.View>(android.R.id.content)
            content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!com.enes.liquidtube.ui.launch.Startup.overlayDrawn) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            })
        }

        setContent {
            val accentIndex by playerConnection.settings.accent.collectAsStateWithLifecycle()
            val refraction by playerConnection.settings.glassRefraction.collectAsStateWithLifecycle()
            val fromArtwork = accentIndex == com.enes.liquidtube.ui.theme.ACCENT_ARTWORK
            val playing by playerConnection.current.collectAsStateWithLifecycle()
            val artAccent = com.enes.liquidtube.ui.theme.rememberArtworkAccent(if (fromArtwork) playing?.thumbnailUrl else null)
            LiquidTubeTheme(
                accent = if (fromArtwork) artAccent
                    else com.enes.liquidtube.ui.theme.AccentPalette.getOrNull(accentIndex)?.second
                        ?: com.enes.liquidtube.ui.theme.AppleRed,
                glassRefraction = refraction,
            ) {
                // Ask for notification permission (Android 13+) so the media
                // notification can show.
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* ignore result */ }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                // Look for a newer LiquidTube on each launch; the update sheet appears if there is one.
                LaunchedEffect(Unit) {
                    com.enes.liquidtube.data.AppUpdater.check(this@MainActivity, notify = false)
                }

                // The launch screen covers the app until it has connected and loaded Home.
                var launching by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
                androidx.compose.foundation.layout.Box {
                    AppScaffold(connection = playerConnection)
                    if (launching) com.enes.liquidtube.ui.launch.LaunchOverlay(onFinished = { launching = false })
                }
            }
        }
    }
}
