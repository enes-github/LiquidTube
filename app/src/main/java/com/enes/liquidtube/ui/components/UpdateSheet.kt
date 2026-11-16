package com.enes.liquidtube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enes.liquidtube.data.AppUpdater
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.theme.tr
import kotlinx.coroutines.launch

/**
 * The update prompt. It shows itself whenever a newer LiquidTube is out and the user
 * hasn't tapped "Later" for it. Tapping Update downloads the APK and opens the installer,
 * with progress shown in place. Drop it once near the top of the app.
 */
@Composable
fun UpdateSheet() {
    val update by AppUpdater.available.collectAsStateWithLifecycle()
    val dismissed by AppUpdater.promptDismissed.collectAsStateWithLifecycle()
    val info = update ?: return
    if (dismissed) return

    val status by AppUpdater.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val busy = status is AppUpdater.Status.Downloading || status is AppUpdater.Status.Installing

    Dialog(onDismissRequest = { if (!busy) AppUpdater.dismissPrompt() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(c.elevated)
                .padding(22.dp),
        ) {
            Text(tr("Update Available", "Güncelleme Var"), style = AppTheme.type.title2, color = c.label)
            Spacer(Modifier.height(4.dp))
            Text(
                "LiquidTube ${info.versionName}" + if (info.sizeBytes > 0) " · ${formatSize(info.sizeBytes)}" else "",
                style = AppTheme.type.subhead, color = c.secondaryLabel,
            )

            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    info.notes,
                    style = AppTheme.type.subhead, color = c.secondaryLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }

            Spacer(Modifier.height(20.dp))

            when (val s = status) {
                is AppUpdater.Status.Downloading -> {
                    Text(
                        tr("Downloading… ${(s.fraction * 100).toInt()}%", "İndiriliyor… %${(s.fraction * 100).toInt()}"),
                        style = AppTheme.type.footnote, color = c.secondaryLabel,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { s.fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = c.accent,
                        trackColor = c.fill,
                    )
                }
                is AppUpdater.Status.Installing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text(
                            "  " + tr("Opening the installer…", "Yükleyici açılıyor…"),
                            style = AppTheme.type.footnote, color = c.secondaryLabel,
                        )
                    }
                }
                is AppUpdater.Status.Failed -> {
                    Text(
                        if (s.message.contains("signature", ignoreCase = true) || s.message.contains("INCONSISTENT", ignoreCase = true))
                            tr("Couldn’t install — this build is signed with a different key. Uninstall the old LiquidTube first, then install this one.",
                                "Yüklenemedi — bu sürüm farklı bir anahtarla imzalanmış. Önce eski LiquidTube’u kaldırıp bunu yükleyin.")
                        else tr("Couldn’t install the update. Please try again.", "Güncelleme yüklenemedi. Lütfen tekrar deneyin."),
                        style = AppTheme.type.footnote, color = c.accent,
                    )
                    Spacer(Modifier.height(14.dp))
                    Buttons(
                        confirmLabel = tr("Try Again", "Tekrar Dene"),
                        onConfirm = { scope.launch { AppUpdater.downloadAndInstall(context) } },
                        onLater = { AppUpdater.dismissPrompt() },
                    )
                }
                AppUpdater.Status.Idle -> {
                    Buttons(
                        confirmLabel = tr("Update", "Güncelle"),
                        onConfirm = { scope.launch { AppUpdater.downloadAndInstall(context) } },
                        onLater = { AppUpdater.dismissPrompt() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Buttons(confirmLabel: String, onConfirm: () -> Unit, onLater: () -> Unit) {
    val c = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp)).background(c.fill)
                .bounceClick(onClick = onLater),
            contentAlignment = Alignment.Center,
        ) {
            Text(tr("Later", "Sonra"), style = AppTheme.type.headline, color = c.label)
        }
        Box(
            Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp)).background(c.accent)
                .bounceClick(onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text(confirmLabel, style = AppTheme.type.headline, color = Color.White)
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}

/**
 * After a crash, asks once whether to report it. Nothing leaves the phone unless the user
 * taps Report, and even then they see the GitHub issue before submitting it.
 */
@Composable
fun CrashReportSheet() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var crash by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.enes.liquidtube.data.ProblemReport.pendingCrash())
    }
    val text = crash ?: return
    val c = AppTheme.colors
    val close = { com.enes.liquidtube.data.ProblemReport.clearCrash(); crash = null }

    Dialog(onDismissRequest = close) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(c.elevated)
                .padding(22.dp),
        ) {
            Text(tr("LiquidTube Crashed", "LiquidTube Çöktü"), style = AppTheme.type.title2, color = c.label)
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    "Sorry about that. Send a report so it can be fixed? It opens a GitHub issue you can read and edit first. No account details are included.",
                    "Bunun için üzgünüz. Düzeltilebilmesi için rapor gönderilsin mi? Önce okuyup düzenleyebileceğiniz bir GitHub sayfası açılır. Hesap bilgileri eklenmez.",
                ),
                style = AppTheme.type.subhead, color = c.secondaryLabel,
            )
            Spacer(Modifier.height(20.dp))
            Buttons(
                confirmLabel = tr("Report", "Bildir"),
                onConfirm = {
                    scope.launch { com.enes.liquidtube.data.ProblemReport.send(context, text) }
                    close()
                },
                onLater = close,
            )
        }
    }
}
