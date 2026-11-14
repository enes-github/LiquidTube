package com.enes.liquidtube.playback

import androidx.compose.runtime.compositionLocalOf

/** Lets any composable reach the shared player without prop-drilling. */
val LocalPlayerConnection = compositionLocalOf<PlayerConnection?> { null }
