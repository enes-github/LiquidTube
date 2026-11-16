package com.enes.liquidtube.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enes.liquidtube.playback.cast.Cast
import com.enes.liquidtube.ui.components.bounceClick
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.theme.tr

/**
 * "Play On": this phone (Android's own picker for speaker / headphones / Bluetooth) or a
 * Cast speaker/TV on the same Wi-Fi. Cast is new, so it's marked Beta.
 */
@Composable
internal fun PlayOnSheet(onDismiss: () -> Unit, openPhoneOutputs: () -> Unit) {
    val context = LocalContext.current
    val devices by Cast.devices.collectAsStateWithLifecycle()
    val castingTo by Cast.castingTo.collectAsStateWithLifecycle()
    val connecting by Cast.connecting.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    DisposableEffect(Unit) {
        Cast.scan(context, true)
        onDispose { Cast.scan(context, false) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(c.elevated)
                .padding(vertical = 18.dp),
        ) {
            Text(
                tr("Play On", "Şurada Çal"), style = AppTheme.type.title2, color = c.label,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(12.dp))

            Choice(
                icon = Icons.Rounded.PhoneAndroid,
                title = tr("This Phone", "Bu Telefon"),
                subtitle = tr("Speaker and Bluetooth", "Hoparlör ve Bluetooth"),
                selected = castingTo == null,
                busy = false,
            ) {
                if (castingTo != null) Cast.stop(context) else openPhoneOutputs()
                onDismiss()
            }

            Row(
                Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("SPEAKERS & TVS", "HOPARLÖRLER VE TV'LER"),
                    style = AppTheme.type.footnote, color = c.secondaryLabel,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(c.accent.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("BETA", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (devices.isEmpty() && castingTo == null) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = c.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tr("Looking for Cast devices on this Wi-Fi…", "Bu Wi-Fi'daki Cast cihazları aranıyor…"),
                        style = AppTheme.type.subhead, color = c.secondaryLabel,
                    )
                }
            }
            devices.forEach { device ->
                val active = castingTo != null && castingTo == device.name
                Choice(
                    icon = if (active) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    title = device.name,
                    subtitle = when {
                        active -> tr("Playing here", "Burada çalıyor")
                        else -> device.description?.takeIf { it.isNotBlank() }
                    },
                    selected = active,
                    busy = connecting && !active && castingTo == null,
                ) {
                    if (!active) Cast.select(context, device)
                    onDismiss()
                }
            }
            if (castingTo != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.padding(horizontal = 22.dp).fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
                        .background(c.fill).bounceClick { Cast.stop(context); onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Stop Casting", "Yayını Durdur"), color = c.accent, style = AppTheme.type.body, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Choice(icon: ImageVector, title: String, subtitle: String?, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().bounceClick(pressedScale = 0.97f, onClick = onClick).padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (selected) c.accent else c.fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (selected) androidx.compose.ui.graphics.Color.White else c.label, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.body, color = c.label, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = AppTheme.type.footnote, color = c.secondaryLabel, maxLines = 1)
        }
        when {
            busy -> CircularProgressIndicator(color = c.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            selected -> Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
    }
}
