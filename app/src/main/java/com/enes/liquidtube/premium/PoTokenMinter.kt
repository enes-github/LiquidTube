package com.enes.liquidtube.premium

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mints YouTube "PO tokens" (proof-of-origin) with Google's BotGuard, inside [JsHost].
 *
 * Web clients must attach a PO token (`pot=`) to stream URLs, or googlevideo stops after
 * the first ~1MB. The flow, as the web player does it:
 *  1. jnn/v1/Create  → a BotGuard challenge (an interpreter script + a program),
 *  2. run it, take a snapshot → botguardResponse,
 *  3. jnn/v1/GenerateIT → an integrity token (valid for hours),
 *  4. the challenge hands back a minter; mint(identifier) → the PO token.
 * The identifier is the account's DataSync ID when signed in (visitorData otherwise).
 * Minted tokens are cached per identifier until the integrity token expires.
 */
@Singleton
class PoTokenMinter @Inject constructor(private val js: JsHost) {
    private val lock = Mutex()
    private val cache = HashMap<String, Pair<String, Long>>()

    suspend fun mint(identifier: String): String = lock.withLock {
        cache[identifier]?.let { (token, until) -> if (System.currentTimeMillis() < until) return@withLock token }
        js.eval("window.__bg ? 'ok' : (${SCRIPT})")
        val token = js.eval("window.__bg.mint(${JSONObject.quote(identifier)})", timeoutMs = 45_000)
        val ttlMs = js.eval("window.__bg.expiresAt()").toLongOrNull()?.minus(System.currentTimeMillis()) ?: 3_600_000L
        cache[identifier] = token to (System.currentTimeMillis() + ttlMs.coerceIn(60_000L, 6 * 3600_000L))
        Log.i(JsHost.TAG, "PO token minted (${token.length} chars, valid ${ttlMs / 60_000} min)")
        token
    }

    fun invalidate() {
        cache.clear()
    }

    private companion object {
        /** Installs window.__bg = { mint(identifier), expiresAt() }. Evaluates to 'ok'. */
        val SCRIPT = """
            (() => {
              const REQUEST_KEY = 'O43z0dpjhgX20SCx4KAo';
              const API_KEY = 'AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw';
              const BASE = 'https://www.youtube.com/api/jnn/v1';
              const b64ToU8 = (b64) => {
                b64 = b64.replace(/-/g, '+').replace(/_/g, '/').replace(/\./g, '=');
                const bin = atob(b64); const u = new Uint8Array(bin.length);
                for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);
                return u;
              };
              const u8ToB64 = (u8) => {
                let bin = ''; for (let i = 0; i < u8.length; i++) bin += String.fromCharCode(u8[i]);
                return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_');
              };
              const post = async (path, body) => {
                const r = await fetch(BASE + path, { method: 'POST', body: JSON.stringify(body), headers: {
                  'content-type': 'application/json+protobuf', 'x-goog-api-key': API_KEY, 'x-user-agent': 'grpc-web-javascript/0.1' } });
                if (!r.ok) throw new Error(path + ' HTTP ' + r.status);
                return r.json();
              };
              const parseChallenge = (raw) => {
                let cd;
                if (raw.length > 1 && typeof raw[1] === 'string') {
                  const bytes = b64ToU8(raw[1]);
                  for (let i = 0; i < bytes.length; i++) bytes[i] = (bytes[i] + 97) & 0xff;
                  cd = JSON.parse(new TextDecoder().decode(bytes));
                } else {
                  cd = raw[0];
                }
                const [messageId, wrappedScript, wrappedUrl, interpreterHash, program, globalName] = cd;
                const pick = (v) => Array.isArray(v) ? v.find((x) => x && typeof x === 'string') : null;
                return { script: pick(wrappedScript), url: pick(wrappedUrl), program, globalName };
              };
              const loadUrl = (src) => new Promise((res, rej) => {
                const s = document.createElement('script'); s.src = src.startsWith('//') ? 'https:' + src : src;
                s.onload = res; s.onerror = () => rej(new Error('interpreter load failed')); document.head.appendChild(s);
              });
              let minter = null, expires = 0;
              const init = async () => {
                const ch = parseChallenge(await post('/Create', [REQUEST_KEY]));
                if (ch.script) { (0, eval)(ch.script); } else if (ch.url) { await loadUrl(ch.url); } else throw new Error('no interpreter');
                const vm = globalThis[ch.globalName];
                if (!vm || typeof vm.a !== 'function') throw new Error('BotGuard VM missing: ' + ch.globalName);
                let fns = {};
                const cb = (asyncSnapshotFunction, shutdownFunction, passEventFunction, checkCameraFunction) =>
                  { fns = { asyncSnapshotFunction, shutdownFunction, passEventFunction, checkCameraFunction }; };
                await vm.a(ch.program, cb, true, undefined, () => {}, [[], []])[0];
                for (let i = 0; i < 100 && !fns.asyncSnapshotFunction; i++) await new Promise((r) => setTimeout(r, 20));
                if (!fns.asyncSnapshotFunction) throw new Error('BotGuard did not start');
                const webPoSignalOutput = [];
                const botguardResponse = await new Promise((res) =>
                  fns.asyncSnapshotFunction((r) => res(r), [undefined, undefined, webPoSignalOutput, undefined]));
                const it = await post('/GenerateIT', [REQUEST_KEY, botguardResponse]);
                const integrityToken = it[0], ttl = it[1] || 3600;
                const getMinter = webPoSignalOutput[0];
                if (typeof getMinter !== 'function') throw new Error('no minter in BotGuard output');
                const mintCallback = await getMinter(b64ToU8(integrityToken));
                if (typeof mintCallback !== 'function') throw new Error('bad minter');
                minter = mintCallback;
                expires = Date.now() + ttl * 1000 - 5 * 60 * 1000;
              };
              window.__bg = {
                mint: async (identifier) => {
                  if (!minter || Date.now() > expires) await init();
                  const out = await minter(new TextEncoder().encode(identifier));
                  if (!out) throw new Error('mint returned nothing');
                  return u8ToB64(out);
                },
                expiresAt: () => expires,
              };
              return 'ok';
            })()
        """.trimIndent()
    }
}
