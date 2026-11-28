package com.onoseto.chipsnfish.online

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL

/** Supabase project of Chips&Fish. URL and the publishable (anon) key are public by design. */
object SupaCfg {
    const val URL = "https://leobbaixgbnftirzdudl.supabase.co"
    const val KEY = "sb_publishable_esmekdxKuc2F2NbSobe_kw_yfAZtjm6"
    val configured get() = URL.isNotBlank() && KEY.isNotBlank()
    /** OAuth "Web application" client ID from Google Cloud (public). Empty = Google sign-in not set up yet. */
    const val GOOGLE_WEB_CLIENT_ID = "583566375724-lrpf8ok61ccrcn7ku1omeia4f7ljmte3.apps.googleusercontent.com"
}

class SupaError(val code: Int, message: String) : Exception(message)

/** Minimal Supabase client: anonymous sign-in + RPC calls over HTTPS. */
object Supa {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var prefs: SharedPreferences
    private val lock = Mutex()
    var uid: String = ""
        private set
    /** Google e-mail of the signed-in player ("" = anonymous account on this phone). */
    var email: String = ""
        private set
    private var access = ""
    private var refresh = ""
    private var expires = 0L

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("cf_online", Context.MODE_PRIVATE)
        uid = prefs.getString("uid", "") ?: ""
        access = prefs.getString("access", "") ?: ""
        refresh = prefs.getString("refresh", "") ?: ""
        expires = prefs.getLong("expires", 0)
        email = prefs.getString("email", "") ?: ""
    }

    private fun http(path: String, body: String, auth: String?): Pair<Int, String> {
        val c = URL(SupaCfg.URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.doOutput = true
        c.setRequestProperty("apikey", SupaCfg.KEY)
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("Authorization", "Bearer " + (auth ?: SupaCfg.KEY))
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to txt
    }

    private fun store(o: JsonObject) {
        access = o["access_token"]!!.jsonPrimitive.content
        refresh = o["refresh_token"]!!.jsonPrimitive.content
        expires = System.currentTimeMillis() + ((o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L) - 120) * 1000
        uid = o["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: uid
        o["user"]?.jsonObject?.get("email")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }?.let { email = it }
        prefs.edit().putString("uid", uid).putString("access", access).putString("refresh", refresh).putLong("expires", expires).putString("email", email).apply()
    }

    /** Makes sure we have a valid session (anonymous account, kept on this phone). */
    /** Forgets the anonymous account on this phone; the next server call creates a new one. */
    fun forget() {
        uid = ""; access = ""; refresh = ""; expires = 0
        prefs.edit().clear().apply()
    }

    suspend fun ensureAuth() = lock.withLock {
        withContext(Dispatchers.IO) {
            if (access.isNotEmpty() && System.currentTimeMillis() < expires) return@withContext
            if (refresh.isNotEmpty()) {
                val (c, t) = http("/auth/v1/token?grant_type=refresh_token", """{"refresh_token":"$refresh"}""", null)
                if (c in 200..299) { store(json.parseToJsonElement(t).jsonObject); return@withContext }
            }
            val (c, t) = http("/auth/v1/signup", """{"data":{}}""", null)
            if (c !in 200..299) throw SupaError(c, errMsg(t))
            store(json.parseToJsonElement(t).jsonObject)
        }
    }

    /**
     * Signs in with a Google ID token. If this phone had an anonymous profile, it is handed over
     * to the Google account (wallet, honor, cups, hats), so nothing is lost.
     */
    suspend fun signInGoogle(idToken: String, rawNonce: String) {
        val old = if (email.isEmpty() && access.isNotEmpty()) runCatching { (rpc("cf_link_code") as? kotlinx.serialization.json.JsonPrimitive)?.content }.getOrNull() else null
        lock.withLock {
            withContext(Dispatchers.IO) {
                val body = buildJsonObject {
                    put("provider", kotlinx.serialization.json.JsonPrimitive("google"))
                    put("id_token", kotlinx.serialization.json.JsonPrimitive(idToken))
                    put("nonce", kotlinx.serialization.json.JsonPrimitive(rawNonce))
                }.toString()
                val (c, t) = http("/auth/v1/token?grant_type=id_token", body, null)
                if (c !in 200..299) throw SupaError(c, errMsg(t))
                store(json.parseToJsonElement(t).jsonObject)
            }
        }
        if (old != null) runCatching { rpc("cf_link_claim", buildJsonObject { put("p_code", kotlinx.serialization.json.JsonPrimitive(old)) }) }
    }

    /** Browser sign-in (PKCE): no app fingerprint needed in Google Cloud. Returns the URL to open. */
    fun pkceUrl(linkCode: String?): String {
        val v = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
        val ch = android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(v.toByteArray()),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        prefs.edit().putString("pkce", v).putString("pkce_link", linkCode ?: "").apply()
        return SupaCfg.URL.trimEnd('/') + "/auth/v1/authorize?provider=google&redirect_to=" +
            java.net.URLEncoder.encode(REDIRECT, "UTF-8") + "&code_challenge=" + ch + "&code_challenge_method=s256"
    }
    const val REDIRECT = "chipsnfish://auth"

    suspend fun linkCodeIfAnonymous(): String? =
        if (email.isEmpty() && access.isNotEmpty()) runCatching { (rpc("cf_link_code") as? kotlinx.serialization.json.JsonPrimitive)?.content }.getOrNull() else null

    /** Finishes the browser sign-in with the code from chipsnfish://auth?code=… */
    suspend fun pkceFinish(code: String) {
        val v = prefs.getString("pkce", "") ?: ""
        if (v.isEmpty()) throw SupaError(0, "sign-in was not started on this phone")
        val old = prefs.getString("pkce_link", "")?.takeIf { it.isNotEmpty() }
        lock.withLock {
            withContext(Dispatchers.IO) {
                val body = buildJsonObject {
                    put("auth_code", kotlinx.serialization.json.JsonPrimitive(code))
                    put("code_verifier", kotlinx.serialization.json.JsonPrimitive(v))
                }.toString()
                val (c, t) = http("/auth/v1/token?grant_type=pkce", body, null)
                if (c !in 200..299) throw SupaError(c, errMsg(t))
                store(json.parseToJsonElement(t).jsonObject)
            }
        }
        prefs.edit().remove("pkce").remove("pkce_link").apply()
        if (old != null) runCatching { rpc("cf_link_claim", buildJsonObject { put("p_code", kotlinx.serialization.json.JsonPrimitive(old)) }) }
    }

    private fun errMsg(t: String): String = runCatching {
        val o = json.parseToJsonElement(t).jsonObject
        (o["message"] ?: o["msg"] ?: o["error_description"] ?: o["error"])?.jsonPrimitive?.content
    }.getOrNull() ?: t.take(200)

    suspend fun rpc(fn: String, args: JsonObject = buildJsonObject { }): JsonElement {
        ensureAuth()
        var r = withContext(Dispatchers.IO) { http("/rest/v1/rpc/$fn", args.toString(), access) }
        if (r.first == 401) {
            expires = 0
            ensureAuth()
            r = withContext(Dispatchers.IO) { http("/rest/v1/rpc/$fn", args.toString(), access) }
        }
        if (r.first !in 200..299) throw SupaError(r.first, errMsg(r.second))
        return if (r.second.isBlank()) JsonNull else json.parseToJsonElement(r.second)
    }
}
