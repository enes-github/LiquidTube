package com.enes.liquidtube.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether music can actually be fetched right now.
 *
 * Android's own "connected" flag isn't enough: Wi-Fi without internet, a dead mobile
 * signal or a captive portal all still look connected. So on top of the system callbacks
 * this checks for real with a tiny request:
 *
 *  - whenever the system reports a network change,
 *  - whenever a stream or request fails ([reportFailure]),
 *  - every 10 s while offline (to notice quickly when it's back),
 *  - every 2 min while online (to catch a silent drop).
 *
 * Going offline needs two failed checks in a row, so a single slow request doesn't flip
 * the whole app; coming back needs one success.
 */
@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val networks = HashSet<Network>()

    private val _online = MutableStateFlow(systemSaysOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /** On Wi-Fi or another unmetered connection (safe for background downloads). */
    fun isUnmetered(): Boolean = _online.value && !cm.isActiveNetworkMetered

    private var checkJob: Job? = null
    private var failures = 0
    /** Bumped on every network change: a check that ran across a change is thrown away. */
    @Volatile private var generation = 0

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(networks) { networks += network }
                generation++
                checkSoon()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                synchronized(networks) { networks += network }
                checkSoon()
            }

            override fun onLost(network: Network) {
                val none = synchronized(networks) {
                    networks -= network
                    networks.isEmpty()
                }
                generation++
                // No network at all: offline right away, no need to ask the internet.
                if (none && !systemSaysOnline()) set(false, "network lost") else checkSoon()
                // Networks often go down one after another (airplane mode: mobile, then
                // Wi-Fi): look again once things have settled.
                scope.launch {
                    delay(3_000)
                    check()
                }
            }
        })
        scope.launch {
            while (true) {
                check()
                delay(if (_online.value) 60_000L else 10_000L)
            }
        }
    }

    /** A stream or request just failed for network reasons: check right now. */
    fun reportFailure() = checkSoon()

    private fun checkSoon() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            delay(400) // let a burst of callbacks settle
            check()
            // A first failure is confirmed (or not) by a second one shortly after.
            if (failures == 1) {
                delay(1_500)
                check()
            }
        }
    }

    private suspend fun check() {
        if (!systemSaysOnline() && synchronized(networks) { networks.isEmpty() }) {
            failures = 2
            set(false, "no network")
            return
        }
        val gen = generation
        val ok = reachable()
        // The network changed while checking: this answer is about a network that's gone.
        if (gen != generation) return
        if (ok && !systemSaysOnline()) return
        if (ok) {
            failures = 0
            set(true, "check ok")
        } else {
            failures++
            if (failures >= 2) set(false, "check failed")
        }
    }

    private fun set(value: Boolean, why: String) {
        if (_online.value != value) Log.i("LiquidTube", "Connection: ${if (value) "online" else "offline"} ($why)")
        _online.value = value
    }

    private suspend fun reachable(): Boolean = withContext(Dispatchers.IO) {
        PROBES.any { url ->
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 4_000
                conn.readTimeout = 4_000
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                try {
                    conn.responseCode == 204
                } finally {
                    conn.disconnect()
                }
            }.getOrDefault(false)
        }
    }

    private fun systemSaysOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        // Tiny "204 No Content" endpoints (also what Android uses for its own check).
        val PROBES = listOf(
            "https://www.gstatic.com/generate_204",
            "https://www.youtube.com/generate_204",
        )
    }
}
