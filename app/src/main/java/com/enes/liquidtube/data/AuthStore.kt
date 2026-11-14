package com.enes.liquidtube.data

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.enes.innertube.InnerTube
import com.enes.innertube.models.AccountInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the signed-in YouTube Music session (the music.youtube.com cookie captured
 * by the login WebView) and the account's name/avatar. Stored only on this device.
 */
@Singleton
class AuthStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _account = MutableStateFlow(
        prefs.getString(KEY_ACCOUNT, null)?.let { runCatching { json.decodeFromString(AccountInfo.serializer(), it) }.getOrNull() }
    )
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        prefs.getString(KEY_COOKIE, null)?.let { applyCookie(it) }
        if (_isLoggedIn.value) refreshAccount()
    }

    /** Returns false if the cookie doesn't contain a usable session. */
    fun login(cookie: String): Boolean {
        applyCookie(cookie)
        if (!_isLoggedIn.value) return false
        prefs.edit().putString(KEY_COOKIE, cookie).apply()
        refreshAccount()
        return true
    }

    fun logout() {
        InnerTube.cookie = null
        _isLoggedIn.value = false
        _account.value = null
        prefs.edit().clear().apply()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    private fun applyCookie(cookie: String) {
        InnerTube.cookie = cookie
        _isLoggedIn.value = InnerTube.isLoggedIn
    }

    private fun refreshAccount() {
        scope.launch {
            runCatching { InnerTube.accountInfo() }
                .onSuccess { info ->
                    if (info != null) {
                        _account.value = info
                        prefs.edit().putString(KEY_ACCOUNT, json.encodeToString(AccountInfo.serializer(), info)).apply()
                    }
                }
                .onFailure { Log.w("LiquidTube", "account_menu failed", it) }
        }
    }

    private companion object {
        const val KEY_COOKIE = "cookie"
        const val KEY_ACCOUNT = "account"
    }
}
