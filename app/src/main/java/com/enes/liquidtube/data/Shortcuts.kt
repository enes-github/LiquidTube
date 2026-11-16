package com.enes.liquidtube.data

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.enes.liquidtube.MainActivity
import com.enes.liquidtube.R
import com.enes.liquidtube.ui.theme.tr

/**
 * Long-press the app icon → Search, Shuffle Favorites, Offline Mix. Set each launch so
 * the labels follow the app's language. Each one opens [MainActivity] with an action it
 * runs once it's up.
 */
object Shortcuts {
    const val EXTRA_ACTION = "shortcut_action"
    const val SEARCH = "search"
    const val SHUFFLE_FAVORITES = "shuffle_favorites"
    const val OFFLINE_MIX = "offline_mix"

    fun refresh(context: Context) {
        val shortcuts = listOf(
            build(context, SEARCH, R.drawable.ic_shortcut_search, tr("Search", "Ara")),
            build(context, SHUFFLE_FAVORITES, R.drawable.ic_shortcut_shuffle, tr("Shuffle Favorites", "Favorileri Karıştır")),
            build(context, OFFLINE_MIX, R.drawable.ic_shortcut_offline, tr("Offline Mix", "Çevrimdışı Miks")),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun build(context: Context, action: String, icon: Int, label: String): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = Intent.ACTION_VIEW
            putExtra(EXTRA_ACTION, action)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return ShortcutInfoCompat.Builder(context, action)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent)
            .build()
    }
}
