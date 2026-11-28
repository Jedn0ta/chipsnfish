package com.onoseto.chipsnfish.online

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.onoseto.chipsnfish.ErrorLog
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Game
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.launch

/** "Sign in with Google": the player's profile is tied to his Google account, so it is the same on every phone and after reinstalling. */
object GoogleAuth {
    val available get() = SupaCfg.configured && SupaCfg.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    /** E-mail shown in the UI (observable). */
    var email by mutableStateOf("")
    var busy by mutableStateOf(false)
    /** Last problem with signing in, shown on the card. */
    var lastError by mutableStateOf("")

    fun load() { email = Supa.email }
    val signedIn get() = email.isNotEmpty()

    suspend fun signIn(ctx: Context): Boolean {
        if (!available || busy) return false
        lastError = ""
        val pr = ctx.getSharedPreferences("cf_online", Context.MODE_PRIVATE)
        // Google refused this app's fingerprint before: go straight to the browser
        if (pr.getBoolean("g_browser", false)) return browser(ctx)
        busy = true
        try {
            val raw = UUID.randomUUID().toString()
            val hashed = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
            val opt = GetSignInWithGoogleOption.Builder(SupaCfg.GOOGLE_WEB_CLIENT_ID).setNonce(hashed).build()
            val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
            val c = CredentialManager.create(ctx).getCredential(ctx, req).credential
            if (c !is CustomCredential || c.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { lastError = "unexpected credential: " + c.type; return false }
            val g = GoogleIdTokenCredential.createFrom(c.data)
            Supa.signInGoogle(g.idToken, raw)
            return afterLogin(g.displayName)
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            val m = e.message ?: ""
            // "[16] Account reauth failed" = this app's fingerprint isn't registered: use the browser instead
            if (m.contains("[16]") || m.contains("reauth", true)) { pr.edit().putBoolean("g_browser", true).apply(); busy = false; return browser(ctx) }
            return false
        } catch (e: androidx.credentials.exceptions.GetCredentialException) {
            ErrorLog.add("google sign-in (credential manager)", e)
            busy = false
            return browser(ctx)
        } catch (e: Exception) {
            ErrorLog.add("google sign-in", e)
            lastError = (e::class.simpleName ?: "") + ": " + (e.message ?: "").take(160)
            return false
        } finally { busy = false }
    }

    /** Sign-in through the browser (Supabase + Google web page); the result comes back via chipsnfish://auth */
    suspend fun browser(ctx: Context): Boolean {
        busy = true
        return try {
            val link = Supa.linkCodeIfAnonymous()
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Supa.pkceUrl(link)))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            false
        } catch (e: Exception) {
            ErrorLog.add("google sign-in (browser)", e)
            lastError = (e::class.simpleName ?: "") + ": " + (e.message ?: "").take(160)
            false
        } finally { busy = false }
    }

    /** Called from MainActivity with chipsnfish://auth?code=… (or ?error=…) */
    fun onRedirect(u: android.net.Uri) {
        val err = u.getQueryParameter("error_description") ?: u.getQueryParameter("error")
        val code = u.getQueryParameter("code")
        if (code == null) { lastError = L("Přihlášení se nepovedlo", "Sign-in failed", "Anmeldung fehlgeschlagen") + ": " + (err ?: "?"); return }
        Game.scope.launch {
            busy = true; lastError = ""
            try { Supa.pkceFinish(code); afterLogin(null) }
            catch (e: Exception) { ErrorLog.add("google sign-in (finish)", e); lastError = (e::class.simpleName ?: "") + ": " + (e.message ?: "").take(160) }
            finally { busy = false }
        }
    }

    private suspend fun afterLogin(displayName: String?): Boolean {
        run {
            email = Supa.email
            if (email.isEmpty()) { lastError = L("Server přihlášení přijal, ale nevrátil e-mail.", "Server accepted the sign-in but returned no e-mail.", "Server lieferte keine E-Mail."); return false }
            // my profile from the server (after a reinstall) – or the one on this phone goes up
            val prof = runCatching { Supa.rpc("cf_me_profile") as? kotlinx.serialization.json.JsonObject }.getOrNull()
            val me = Game.P.getValue("hero")
            val sName = prof?.get("name")?.jsonPrimitive?.contentOrNull
            if (!sName.isNullOrBlank()) {
                me.name = sName
                prof["img"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { me.img = it }
            } else if (me.name.isBlank() || me.name == "Ty") me.name = displayName?.substringBefore(' ')?.take(20) ?: Supa.email.substringBefore('@').take(20)
            Game.saveLocal(); Game.bump()
            runCatching { Online.syncProfile() }
            runCatching { Social.loadBank() }
            Game.toast(L("Přihlášeno: ", "Signed in: ", "Angemeldet: ") + email)
            return true
        }
    }

    /** SHA-1 of the certificate this installed app is signed with – must be listed in Google Cloud (Android client). */
    fun sha1(ctx: Context): String = runCatching {
        val pm = ctx.packageManager
        val sigs = if (android.os.Build.VERSION.SDK_INT >= 28)
            pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        else @Suppress("DEPRECATION") pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
        val d = MessageDigest.getInstance("SHA-1").digest(sigs!![0].toByteArray())
        d.joinToString(":") { "%02X".format(it) }
    }.getOrDefault("?")

    private fun sigHint(ctx: Context) = L("SHA-1 aplikace: ", "App SHA-1: ", "App-SHA-1: ") + sha1(ctx)
}
