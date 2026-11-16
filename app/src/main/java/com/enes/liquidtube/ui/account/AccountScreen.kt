package com.enes.liquidtube.ui.account

import androidx.compose.foundation.layout.imePadding
import com.enes.liquidtube.ui.theme.tr
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Image
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.ui.components.Gutter
import com.enes.liquidtube.ui.components.LargeTitle
import com.enes.liquidtube.ui.components.bounceClick
import com.enes.liquidtube.ui.library.BackRow
import com.enes.liquidtube.ui.theme.AppTheme

@Composable
fun AccountScreen(onBack: () -> Unit, onSignIn: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val auth = connection.auth
    val loggedIn by auth.isLoggedIn.collectAsStateWithLifecycle()
    val account by auth.account.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    val dataSaver by connection.settings.dataSaver.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackRow(tr("Back", "Geri"), onBack)
        LargeTitle(tr("Account", "Hesap"), withStatusBar = false)
        Spacer(Modifier.height(24.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(c.fill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, null, tint = c.secondaryLabel, modifier = Modifier.size(56.dp))
                account?.photoUrl?.let {
                    AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(14.dp))

            if (loggedIn) {
                Text(account?.name ?: tr("Signed in", "Giriş yapıldı"), style = AppTheme.type.title2, color = c.label)
                account?.handle?.let { Text(it, style = AppTheme.type.subhead, color = c.secondaryLabel) }
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Home, Library and Liked Songs come from your YouTube Music account. Favoriting a song also likes it there.", "Ana Sayfa, Kitaplık ve Beğenilen Şarkılar YouTube Music hesabınızdan gelir. Bir şarkıyı favorilere eklemek onu orada da beğenir."),
                    style = AppTheme.type.footnote,
                    color = c.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                WideButton(tr("Sign Out", "Çıkış Yap"), destructive = true) { auth.logout() }
            } else {
                Text(tr("Not signed in", "Giriş yapılmadı"), style = AppTheme.type.title2, color = c.label)
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Sign in with your Google account to get your personal YouTube Music home, playlists, albums, artists and liked songs.", "Kişisel YouTube Music ana sayfanız, çalma listeleriniz, albümleriniz, sanatçılarınız ve beğendiğiniz şarkılar için Google hesabınızla giriş yapın."),
                    style = AppTheme.type.footnote,
                    color = c.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                WideButton(tr("Sign in with Google", "Google ile Giriş Yap"), onClick = onSignIn)
            }
        }

        Spacer(Modifier.height(36.dp))
        Text(
            tr("PLAYBACK", "OYNATMA"),
            style = AppTheme.type.footnote,
            color = c.secondaryLabel,
            modifier = Modifier.padding(horizontal = Gutter + 16.dp, vertical = 6.dp),
        )
        Column(
            Modifier
                .padding(horizontal = Gutter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.elevated),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Data Saver", "Veri Tasarrufu"), style = AppTheme.type.body, color = c.label)
                    Text(
                        tr("Stream at the lowest quality to save mobile data.", "Mobil veriden tasarruf için en düşük kalitede çal."),
                        style = AppTheme.type.footnote, color = c.secondaryLabel,
                    )
                }
                Switch(
                    checked = dataSaver,
                    onCheckedChange = connection.settings::setDataSaver,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
                )
            }
        }
        SettingsGroup(tr("OFFLINE MIX", "ÇEVRİMDIŞI MİKS")) {
            val smart by connection.settings.smartDownloads.collectAsStateWithLifecycle()
            val size by connection.settings.smartSize.collectAsStateWithLifecycle()
            val cellular by connection.settings.smartOnCellular.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Smart Downloads", "Akıllı İndirmeler"), style = AppTheme.type.body, color = c.label)
                    Text(
                        tr("Keeps your favorites, liked and most-played songs downloaded automatically, so there is always music offline.", "Favorilerinizi, beğendiklerinizi ve en çok dinlediklerinizi otomatik indirir; böylece çevrimdışıyken de hep müzik olur."),
                        style = AppTheme.type.footnote, color = c.secondaryLabel,
                    )
                }
                Switch(
                    checked = smart,
                    onCheckedChange = { on ->
                        connection.settings.setSmartDownloads(on)
                        if (on) connection.smart.refresh() else { connection.downloads.pruneAuto(emptySet()); connection.smart.clearMix() }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
                )
            }
            if (smart) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(tr("Mix Size", "Miks Boyutu"), style = AppTheme.type.body, color = c.label)
                    Spacer(Modifier.height(8.dp))
                    com.enes.liquidtube.ui.components.FilterChips(
                        options = listOf(25, 50, 100, 200),
                        selected = size,
                        label = { tr("$it songs", "$it şarkı") },
                        onSelect = { connection.settings.setSmartSize(it); connection.smart.refresh() },
                        edge = 0.dp,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Use Mobile Data", "Mobil Veri Kullan"), style = AppTheme.type.body, color = c.label)
                        Text(tr("Off: the mix only downloads on Wi-Fi.", "Kapalı: miks yalnızca Wi-Fi'da indirilir."), style = AppTheme.type.footnote, color = c.secondaryLabel)
                    }
                    Switch(
                        checked = cellular,
                        onCheckedChange = { connection.settings.setSmartOnCellular(it); if (it) connection.smart.refresh() },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
                    )
                }
            }
        }

        SettingsGroup(tr("LISTENING CACHE", "DİNLEME ÖNBELLEĞİ")) {
            val cacheMb by connection.settings.cacheSizeMb.collectAsStateWithLifecycle()
            val used by connection.playCache.bytesUsed.collectAsStateWithLifecycle()
            val cachedCount = connection.playCache.cached.collectAsStateWithLifecycle().value.size
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                val mb = used / 1_048_576f
                val usedText = if (mb >= 1024f) "%.1f GB".format(mb / 1024f) else "%.0f MB".format(mb)
                Text(tr("$usedText used \u00B7 $cachedCount ${if (cachedCount == 1) "song" else "songs"} ready offline", "$usedText kullan\u0131l\u0131yor \u00B7 $cachedCount \u015Fark\u0131 \u00E7evrimd\u0131\u015F\u0131 haz\u0131r"), style = AppTheme.type.body, color = c.label)
                Text(
                    tr("Every song you play is saved as it streams, so it works offline afterwards. The least recently played go first when it\u2019s full.", "Çaldığınız her şarkı çalarken kaydedilir, böylece sonra çevrimdışı da çalar. Dolunca en uzun süredir çalınmayanlar silinir."),
                    style = AppTheme.type.footnote, color = c.secondaryLabel,
                )
                Spacer(Modifier.height(10.dp))
                com.enes.liquidtube.ui.components.FilterChips(
                    options = listOf(512, 1024, 2048, 4096, 8192),
                    selected = cacheMb,
                    label = { if (it < 1024) "$it MB" else "${it / 1024} GB" },
                    onSelect = { connection.settings.setCacheSizeMb(it); connection.playCache.trim() },
                    edge = 0.dp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Clear Cache", "Önbelleği Temizle"),
                    style = AppTheme.type.body,
                    color = c.accent,
                    modifier = Modifier.clickable { connection.playCache.clear() }.padding(vertical = 8.dp),
                )
            }
        }

        SettingsGroup(tr("LYRICS", "ŞARKI SÖZLERİ")) {
            val lockLyrics by connection.settings.lockScreenLyrics.collectAsStateWithLifecycle()
            val translateTo by connection.settings.translateTo.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Lyrics on Lock Screen", "Kilit Ekranında Sözler"), style = AppTheme.type.body, color = c.label)
                    Text(
                        tr("The line being sung shows under the song title on the lock screen and in the notification.", "Söylenen satır, kilit ekranında ve bildirimde şarkı adının altında görünür."),
                        style = AppTheme.type.footnote, color = c.secondaryLabel,
                    )
                }
                Switch(
                    checked = lockLyrics,
                    onCheckedChange = connection.settings::setLockScreenLyrics,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(tr("Translate Lyrics To", "Sözleri Şu Dile Çevir"), style = AppTheme.type.body, color = c.label)
                Text(
                    tr("Tap the translate button above the lyrics to show a translation under each line.", "Her satırın altında çeviri görmek için sözlerin üstündeki çeviri düğmesine dokunun."),
                    style = AppTheme.type.footnote, color = c.secondaryLabel,
                )
                Spacer(Modifier.height(10.dp))
                com.enes.liquidtube.ui.components.FilterChips(
                    options = com.enes.liquidtube.data.LyricsTranslator.LANGUAGES,
                    selected = com.enes.liquidtube.data.LyricsTranslator.LANGUAGES.firstOrNull { it.first == translateTo }
                        ?: com.enes.liquidtube.data.LyricsTranslator.LANGUAGES.first(),
                    label = { tr(it.second, TRANSLATE_LANG_TR[it.first] ?: it.second) },
                    onSelect = { connection.settings.setTranslateTo(it.first) },
                    edge = 0.dp,
                )
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DREAM_SETTINGS))
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(tr("Screen Saver While Charging", "Şarj Olurken Ekran Koruyucu"), style = AppTheme.type.body, color = c.accent)
                Text(
                    tr("Shows the cover and the lyrics while your phone charges. Tap, then choose LiquidTube as the screen saver.", "Telefonunuz şarj olurken kapağı ve sözleri gösterir. Dokunun, ardından ekran koruyucu olarak LiquidTube'u seçin."),
                    style = AppTheme.type.footnote, color = c.secondaryLabel,
                )
            }
        }

        SettingsGroup(tr("SOUND", "SES")) {
            val eq by connection.settings.eqPreset.collectAsStateWithLifecycle()
            val soundCheck by connection.settings.soundCheck.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(tr("Equalizer", "Ekolayzır"), style = AppTheme.type.body, color = c.label)
                Spacer(Modifier.height(10.dp))
                com.enes.liquidtube.ui.components.FilterChips(
                    options = com.enes.liquidtube.playback.AudioFx.PRESETS,
                    selected = com.enes.liquidtube.playback.AudioFx.PRESETS.firstOrNull { it.id == eq } ?: com.enes.liquidtube.playback.AudioFx.PRESETS.first(),
                    label = { it.label },
                    onSelect = { connection.settings.setEqPreset(it.id) },
                    edge = 0.dp,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Sound Check", "Ses Dengeleme"), style = AppTheme.type.body, color = c.label)
                    Text(tr("Plays every song at the same volume, using YouTube’s loudness data.", "YouTube'un ses yüksekliği verisiyle her şarkıyı aynı seviyede çalar."), style = AppTheme.type.footnote, color = c.secondaryLabel)
                }
                Switch(
                    checked = soundCheck,
                    onCheckedChange = connection.settings::setSoundCheck,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
                )
            }
        }

        SettingsGroup(tr("AUTOMATIC", "OTOMATİK")) {
            val autoResume by connection.settings.autoResume.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            // Android 12+: the Nearby devices permission lets it resume even when LiquidTube is closed.
            val needsPermission = android.os.Build.VERSION.SDK_INT >= 31
            var btGranted by remember {
                mutableStateOf(!needsPermission || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            }
            val askBluetooth = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { btGranted = it }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Resume Automatically", "Otomatik Devam Et"), style = AppTheme.type.body, color = c.label)
                    Text(
                        tr("Pick up where you left off when you connect headphones, a Bluetooth speaker or your car (if you paused in the last 4 hours).", "Kulaklık, Bluetooth hoparlör veya araba bağlandığında kaldığınız yerden devam eder (son 4 saat içinde durdurduysanız)."),
                        style = AppTheme.type.footnote, color = c.secondaryLabel,
                    )
                }
                Switch(
                    checked = autoResume,
                    onCheckedChange = {
                        connection.settings.setAutoResume(it)
                        if (it && !btGranted && needsPermission) askBluetooth.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
                )
            }
            if (autoResume && !btGranted) {
                Text(
                    tr("Allow Nearby devices so it also works when LiquidTube is closed", "LiquidTube kapalıyken de çalışması için Yakındaki cihazlar iznini verin"),
                    style = AppTheme.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = c.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { askBluetooth.launch(android.Manifest.permission.BLUETOOTH_CONNECT) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            val alerts by connection.settings.releaseAlerts.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("New Release Alerts", "Yeni Çıkış Bildirimleri"), style = AppTheme.type.body, color = c.label)
                    Text(
                        tr("A notification when artists you follow put out something new. They also show on the New tab.", "Takip ettiğiniz sanatçılar yeni bir şey çıkardığında bildirim gelir. Yeni sekmesinde de görünür."),
                        style = AppTheme.type.footnote, color = c.secondaryLabel,
                    )
                }
                Switch(
                    checked = alerts,
                    onCheckedChange = connection.settings::setReleaseAlerts,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
                )
            }
        }

        SettingsGroup(tr("UPDATES", "GÜNCELLEMELER")) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val updScope = androidx.compose.runtime.rememberCoroutineScope()
            val autoUpdate by connection.settings.autoUpdateCheck.collectAsStateWithLifecycle()
            val available by com.enes.liquidtube.data.AppUpdater.available.collectAsStateWithLifecycle()
            var checking by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    tr("You’re on LiquidTube ", "LiquidTube ") +
                        com.enes.liquidtube.data.AppUpdater.currentVersionName(ctx) +
                        tr("", " sürümündesiniz"),
                    style = AppTheme.type.body, color = c.label,
                )
                Text(
                    available?.let { tr("Version ${it.versionName} is available.", "${it.versionName} sürümü hazır.") }
                        ?: tr("Updates install straight from GitHub, no store needed.", "Güncellemeler mağaza olmadan doğrudan GitHub’dan yüklenir."),
                    style = AppTheme.type.footnote,
                    color = if (available != null) c.accent else c.secondaryLabel,
                )
                Spacer(Modifier.height(12.dp))
                WideButton(
                    if (available != null) tr("Install Update", "Güncellemeyi Yükle")
                    else if (checking) tr("Checking…", "Kontrol ediliyor…")
                    else tr("Check for Updates", "Güncellemeleri Denetle"),
                ) {
                    if (available != null) {
                        com.enes.liquidtube.data.AppUpdater.requestPrompt()
                    } else if (!checking) {
                        checking = true
                        updScope.launch {
                            val found = com.enes.liquidtube.data.AppUpdater.check(ctx, notify = false)
                            checking = false
                            if (found == null) android.widget.Toast.makeText(
                                ctx, tr("You’re up to date", "En son sürümdesiniz"), android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = c.separator)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Check Automatically", "Otomatik Denetle"), style = AppTheme.type.body, color = c.label)
                    Text(
                        tr("Look for a new version in the background and notify you quietly.", "Arka planda yeni sürüm arar ve sessizce bildirir."),
                        style = AppTheme.type.footnote, color = c.secondaryLabel,
                    )
                }
                Switch(
                    checked = autoUpdate,
                    onCheckedChange = connection.settings::setAutoUpdateCheck,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
                )
            }
        }

        SettingsGroup(tr("HELP", "YARDIM")) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val helpScope = androidx.compose.runtime.rememberCoroutineScope()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    tr("Something not working? Send a report with your app version, phone model and recent log. It opens as a GitHub issue you can read and edit before sending. No account details are included.",
                        "Bir şey çalışmıyor mu? Uygulama sürümü, telefon modeli ve son kayıtlarla bir rapor gönderin. Göndermeden önce okuyup düzenleyebileceğiniz bir GitHub sayfası olarak açılır. Hesap bilgileri eklenmez."),
                    style = AppTheme.type.footnote, color = c.secondaryLabel,
                )
                Spacer(Modifier.height(12.dp))
                WideButton(tr("Report a Problem", "Sorun Bildir")) {
                    helpScope.launch { com.enes.liquidtube.data.ProblemReport.send(ctx, null) }
                }
            }
        }

        SettingsGroup(tr("STATIONS", "İSTASYONLAR")) {
            val style by connection.settings.stationStyle.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    when (style) {
                        "familiar" -> tr("Familiar", "Tanıdık")
                        "discovery" -> tr("Discovery", "Keşif")
                        else -> tr("Balanced", "Dengeli")
                    },
                    style = AppTheme.type.body, color = c.label,
                )
                Text(
                    when (style) {
                        "familiar" -> tr("Stations and Autoplay mix in your favorites and most-played songs.", "İstasyonlar ve Otomatik Oynatma, favorilerinizi ve en çok dinlediklerinizi karıştırır.")
                        "discovery" -> tr("Stations and Autoplay skip songs you already play a lot, so you hear new music.", "İstasyonlar ve Otomatik Oynatma çok dinlediğiniz şarkıları atlar, böylece yeni müzik duyarsınız.")
                        else -> tr("Stations play YouTube Music’s radio as it is.", "İstasyonlar YouTube Music radyosunu olduğu gibi çalar.")
                    },
                    style = AppTheme.type.footnote, color = c.secondaryLabel,
                )
                Spacer(Modifier.height(10.dp))
                com.enes.liquidtube.ui.components.FilterChips(
                    options = listOf("familiar", "balanced", "discovery"),
                    selected = style,
                    label = { it.replaceFirstChar { ch -> ch.uppercase() } },
                    onSelect = connection.settings::setStationStyle,
                    edge = 0.dp,
                )
            }
        }

        SettingsGroup(tr("CROSSFADE", "GEÇİŞ EFEKTİ")) {
            val crossfade by connection.settings.crossfadeSeconds.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    if (crossfade == 0) tr("Off — gapless", "Kapalı — boşluksuz") else tr("$crossfade seconds", "$crossfade saniye"),
                    style = AppTheme.type.body, color = c.label,
                )
                Text(
                    tr("Fade songs into each other, like AutoMix. Off plays albums gaplessly.", "Şarkıları AutoMix gibi birbirine geçirir. Kapalıyken albümler boşluksuz çalar."),
                    style = AppTheme.type.footnote, color = c.secondaryLabel,
                )
                Spacer(Modifier.height(10.dp))
                com.enes.liquidtube.ui.components.FilterChips(
                    options = listOf(0, 3, 6, 9, 12),
                    selected = crossfade,
                    label = { if (it == 0) tr("Off", "Kapalı") else "${it}s" },
                    onSelect = connection.settings::setCrossfadeSeconds,
                    edge = 0.dp,
                )
            }
        }

        SettingsGroup(tr("APPEARANCE", "GÖRÜNÜM")) {
            val accent by connection.settings.accent.collectAsStateWithLifecycle()
            val refraction by connection.settings.glassRefraction.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(tr("Accent Color", "Vurgu Rengi"), style = AppTheme.type.body, color = c.label)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    com.enes.liquidtube.ui.theme.AccentPalette.forEachIndexed { i, (name, color) ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { connection.settings.setAccent(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == accent) Icon(Icons.Rounded.Check, name, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    // From the artwork: follows the colours of the song that's playing.
                    val artwork = com.enes.liquidtube.ui.theme.ACCENT_ARTWORK
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Brush.sweepGradient(com.enes.liquidtube.ui.theme.AccentPalette.map { it.second } + com.enes.liquidtube.ui.theme.AccentPalette.first().second))
                            .clickable { connection.settings.setAccent(artwork) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (accent == artwork) Icons.Rounded.Check else Icons.Rounded.Image,
                            tr("From Artwork", "Kapaktan"), tint = Color.White, modifier = Modifier.size(if (accent == artwork) 20.dp else 17.dp),
                        )
                    }
                }
                if (accent == com.enes.liquidtube.ui.theme.ACCENT_ARTWORK) {
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Follows the colours of the cover that’s playing.", "Çalan şarkının kapak renklerini izler."), style = AppTheme.type.footnote, color = c.secondaryLabel)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Glass Refraction", "Cam Kırılması"), style = AppTheme.type.body, color = c.label)
                    Text(
                        tr("Bend light at the edges of glass. Turn off for plain frosted glass and longer battery life.", "Camın kenarlarında ışığı büker. Düz buzlu cam ve daha uzun pil ömrü için kapatın."),
                        style = AppTheme.type.footnote, color = c.secondaryLabel,
                    )
                }
                Switch(
                    checked = refraction,
                    onCheckedChange = connection.settings::setGlassRefraction,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
                )
            }
        }

        SettingsGroup(tr("LANGUAGE", "DİL")) {
            val appLanguage by connection.settings.appLanguage.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(tr("App Language", "Uygulama Dili"), style = AppTheme.type.body, color = c.label)
                Text(
                    tr("The language of the app, and of YouTube Music’s recommendations and charts.",
                        "Uygulamanın ve YouTube Music önerilerinin, listelerinin dili."),
                    style = AppTheme.type.footnote, color = c.secondaryLabel,
                )
                Spacer(Modifier.height(10.dp))
                val options = remember { connection.settings.languageOptions() }
                com.enes.liquidtube.ui.components.FilterChips(
                    options = options,
                    selected = options.firstOrNull { it.code == appLanguage } ?: options.first(),
                    label = { opt -> if (opt.code.isEmpty()) tr("Phone Language", "Telefon Dili") else opt.nativeName },
                    onSelect = { opt -> connection.settings.setAppLanguage(opt.code) },
                    edge = 0.dp,
                )
            }
        }

        SettingsGroup(tr("AUDIO QUALITY", "SES KALİTESİ")) {
            val premiumOn by connection.settings.premiumAudio.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Premium Audio (256kbps)", "Premium Ses (256kbps)"), style = AppTheme.type.body, color = c.label)
                    Text(
                        tr("Uses your YouTube Music Premium streams. Falls back to standard quality (~150kbps) whenever YouTube blocks them.", "YouTube Music Premium yayınlarınızı kullanır. YouTube engellediğinde standart kaliteye (~150kbps) döner."),
                        style = AppTheme.type.footnote, color = c.secondaryLabel,
                    )
                }
                Switch(
                    checked = premiumOn,
                    onCheckedChange = connection.settings::setPremiumAudio,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
                )
            }
            if (premiumOn) {
                val pending by connection.upgrader.pending.collectAsStateWithLifecycle()
                val upgrading by connection.upgrader.running.collectAsStateWithLifecycle()
                val upgraded = connection.quality.premiumIds.collectAsStateWithLifecycle().value.size
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(tr("Upgrade Saved Songs", "Kayıtlı Şarkıları Yükselt"), style = AppTheme.type.body, color = c.label)
                    Text(
                        when {
                            upgrading -> tr("Upgrading to 256kbps\u2026 $pending left", "256kbps'e yükseltiliyor… $pending kaldı")
                            pending > 0 -> tr("$pending saved songs will be upgraded to 256kbps on Wi-Fi. $upgraded already are.", "$pending kayıtlı şarkı Wi-Fi'da 256kbps'e yükseltilecek. $upgraded tanesi zaten yükseltildi.")
                            else -> tr("All saved songs are 256kbps ($upgraded).", "Tüm kayıtlı şarkılar 256kbps ($upgraded).")
                        },
                        style = AppTheme.type.footnote, color = c.secondaryLabel,
                    )
                }
            }
        }
        Text(
            if (loggedIn) tr("Premium Audio needs a signed-in YouTube Music Premium account.", "Premium Ses için YouTube Music Premium hesabıyla giriş yapılmalı.")
            else tr("Sign in to use Premium Audio.", "Premium Ses için giriş yapın."),
            style = AppTheme.type.footnote,
            color = c.secondaryLabel,
            modifier = Modifier.padding(horizontal = Gutter + 16.dp, vertical = 8.dp),
        )

        // Backup & restore: favorites, stats, settings and history in a file you keep.
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val overlay = com.enes.liquidtube.ui.components.LocalOverlay.current
        val toast = { msg: String -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show() }
        val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) scope.launch {
                com.enes.liquidtube.data.Backup.export(context, uri)
                    .onSuccess { toast(tr("Backup saved", "Yedek kaydedildi")) }
                    .onFailure { toast(tr("Couldn\u2019t save the backup", "Yedek kaydedilemedi")) }
            }
        }
        val importer = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) overlay.prompt(
                com.enes.liquidtube.ui.components.PromptSpec(
                    title = tr("Restore Backup?", "Yedek Geri Yüklensin mi?"),
                    message = tr("This replaces your favorites, stats, history and settings with the backup\u2019s. LiquidTube restarts afterwards. Type RESTORE to continue.", "Favorileriniz, istatistikleriniz, geçmişiniz ve ayarlarınız yedektekilerle değiştirilir. Ardından LiquidTube yeniden başlar. Devam etmek için RESTORE yazın."),
                    placeholder = "RESTORE",
                    confirm = tr("Restore", "Geri Yükle"),
                    destructive = true,
                    onConfirm = { typed ->
                        if (typed.trim().equals("restore", ignoreCase = true)) scope.launch {
                            com.enes.liquidtube.data.Backup.restore(context, uri)
                                // Restart straight away: nothing in memory may write over the restored data.
                                .onSuccess { com.enes.liquidtube.data.Backup.restart(context) }
                                .onFailure { toast(tr("That file isn\u2019t an LiquidTube backup", "Bu dosya bir LiquidTube yedeği değil")) }
                        }
                    },
                )
            )
        }
        // Online backup: one file in Google Drive (or another cloud app), updated every day.
        val autoTarget by com.enes.liquidtube.data.AutoBackup.target.collectAsStateWithLifecycle()
        val chooser = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) scope.launch {
                connection.library.saveStats()
                com.enes.liquidtube.data.AutoBackup.setTarget(context, uri)
                    .onSuccess {
                        val t = com.enes.liquidtube.data.AutoBackup.target.value
                        if (t != null && com.enes.liquidtube.data.AutoBackup.isLocal(t)) {
                            toast(tr("That\u2019s on this phone, not online. Tap Change\u2026 and pick Drive from the \u2630 menu.", "Bu dosya çevrimiçi değil, telefonda. Değiştir…'e dokunup ☰ menüsünden Drive'ı seçin."))
                        } else toast(tr("Backing up to ${t?.where ?: "the cloud"} every day", "Her gün ${t?.where ?: "buluta"} yedekleniyor"))
                    }
                    .onFailure { toast(tr("Couldn’t write to that file", "Bu dosyaya yazılamadı")) }
            }
        }
        SettingsGroup(tr("BACKUP", "YEDEKLEME")) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(tr("Online Backup", "Çevrimiçi Yedekleme"), style = AppTheme.type.body, color = c.label)
                val t = autoTarget
                Text(
                    if (t == null) tr("Keep a backup in Google Drive, updated every day. On the next screen, tap \u2630 (top left), choose Drive, pick a folder and tap Save.", "Google Drive'da her gün güncellenen bir yedek tutun. Sonraki ekranda ☰'ye (sol üst) dokunun, Drive'ı ve bir klasör seçip Kaydet'e dokunun.")
                    else buildString {
                        appendLine("${t.where.ifBlank { tr("Saved", "Kaydedildi") }} · ${t.name}")
                        append(
                            when {
                                t.lastFailed -> tr("The last backup didn’t go through. It’ll try again.", "Son yedekleme başarısız oldu. Tekrar denenecek.")
                                t.lastAt > 0 -> tr("Last backed up ", "Son yedekleme: ") + android.text.format.DateUtils.getRelativeTimeSpanString(t.lastAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
                                else -> tr("Not backed up yet", "Henüz yedeklenmedi")
                            }
                        )
                    },
                    style = AppTheme.type.footnote, color = if (t?.lastFailed == true) c.accent else c.secondaryLabel,
                )
                if (t != null && com.enes.liquidtube.data.AutoBackup.isLocal(t)) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("This file is only on this phone, so it isn\u2019t online. Tap Change\u2026 and choose Drive from the \u2630 menu.", "Bu dosya yalnızca bu telefonda, çevrimiçi değil. Değiştir…'e dokunup ☰ menüsünden Drive'ı seçin."),
                        style = AppTheme.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = c.accent,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (t == null) {
                    WideButton(tr("Set Up Online Backup", "Çevrimiçi Yedeklemeyi Kur")) { chooser.launch("LiquidTube Backup.json") }
                } else {
                    WideButton(tr("Back Up Now", "Şimdi Yedekle")) {
                        connection.library.saveStats()
                        scope.launch {
                            com.enes.liquidtube.data.AutoBackup.runNow(context)
                                .onSuccess { toast(tr("Backed up to ${t.where.ifBlank { "your backup file" }}", "${t.where.ifBlank { "Yedek dosyanıza" }} yedeklendi")) }
                                .onFailure { toast(tr("Couldn’t back up. Check that the file still exists.", "Yedeklenemedi. Dosyanın hâlâ var olduğunu kontrol edin.")) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { WideButton(tr("Change…", "Değiştir…"), destructive = true) { chooser.launch("LiquidTube Backup.json") } }
                        Box(Modifier.weight(1f)) { WideButton(tr("Turn Off", "Kapat"), destructive = true) { com.enes.liquidtube.data.AutoBackup.clear(context) } }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = c.separator)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(tr("Backup File", "Yedek Dosyası"), style = AppTheme.type.body, color = c.label)
                Text(
                    tr("Save a one-off copy anywhere (Drive, Files, email it to yourself), or restore from one. Includes favorites, play counts, Replay stats, Recently Played and settings.", "Tek seferlik bir kopyayı herhangi bir yere kaydedin (Drive, Dosyalar, kendinize e-posta) veya ondan geri yükleyin. Favoriler, çalma sayıları, Replay istatistikleri, Son Çalınanlar ve ayarlar dahildir."),
                    style = AppTheme.type.footnote, color = c.secondaryLabel,
                )
                Spacer(Modifier.height(12.dp))
                WideButton(tr("Upload a Copy to Google Drive", "Google Drive'a Kopya Yükle"), destructive = true) {
                    connection.library.saveStats()
                    scope.launch {
                        com.enes.liquidtube.data.AutoBackup.sendToDrive(context)
                            .onFailure { toast(tr("Couldn\u2019t prepare the backup", "Yedek hazırlanamadı")) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                WideButton(tr("Save a Copy\u2026", "Kopya Kaydet…"), destructive = true) {
                    connection.library.saveStats()
                    val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    exporter.launch("LiquidTube Backup $day.json")
                }
                Spacer(Modifier.height(10.dp))
                WideButton(tr("Restore from Backup\u2026", "Yedekten Geri Yükle…"), destructive = true) {
                    importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
            }
        }
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Spacer(Modifier.height(22.dp))
    Text(
        title,
        style = AppTheme.type.footnote,
        color = c.secondaryLabel,
        modifier = Modifier.padding(horizontal = Gutter + 16.dp, vertical = 6.dp),
    )
    Column(
        Modifier
            .padding(horizontal = Gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.elevated),
    ) { content() }
}

@Composable
private fun WideButton(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(if (destructive) c.elevated else c.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AppTheme.type.headline, color = if (destructive) c.accent else Color.White)
    }
}

/**
 * Google sign-in inside a WebView. Once the flow lands back on music.youtube.com
 * with a session cookie, the cookie is handed to AuthStore and we close.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    val c = AppTheme.colors

    // Its own full-screen window above the app: the tab bar can't cover it, and the glass
    // effect (which re-captures the page under it every frame) can't make the web page flicker.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDone,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
    // The keyboard resizes the page instead of covering the fields.
    val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
    androidx.compose.runtime.SideEffect {
        dialogWindow?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackRow(tr("Cancel", "İptal"), onDone)
            Spacer(Modifier.weight(1f))
            if (loading) {
                CircularProgressIndicator(
                    color = c.secondaryLabel, strokeWidth = 2.dp,
                    modifier = Modifier.padding(end = Gutter).size(20.dp),
                )
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Google refuses sign-in from user agents marked as a WebView ("; wv").
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            if (finished || url == null || !url.startsWith("https://music.youtube.com")) return
                            val cookie = cookies.getCookie("https://music.youtube.com") ?: return
                            if ("SAPISID" !in cookie) return
                            finished = true
                            cookies.flush()
                            if (connection.auth.login(cookie)) {
                                Toast.makeText(context, tr("Signed in", "Giriş yapıldı"), Toast.LENGTH_SHORT).show()
                            }
                            onDone()
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            },
        )
    }
    }
}

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=" +
        "https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

/** Names of the lyrics translation languages, in Turkish. */
private val TRANSLATE_LANG_TR = mapOf(
    "" to "Telefon Dili", "en" to "İngilizce", "tr" to "Türkçe", "es" to "İspanyolca",
    "de" to "Almanca", "fr" to "Fransızca", "it" to "İtalyanca", "pt" to "Portekizce",
    "ru" to "Rusça", "ar" to "Arapça", "ja" to "Japonca", "ko" to "Korece",
)
