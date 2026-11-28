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
        busy = true
        lastError = ""
        try {
            val raw = UUID.randomUUID().toString()
            val hashed = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
            val opt = GetSignInWithGoogleOption.Builder(SupaCfg.GOOGLE_WEB_CLIENT_ID).setNonce(hashed).build()
            val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
            val c = CredentialManager.create(ctx).getCredential(ctx, req).credential
            if (c !is CustomCredential || c.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { lastError = "unexpected credential: " + c.type; return false }
            val g = GoogleIdTokenCredential.createFrom(c.data)
            Supa.signInGoogle(g.idToken, raw)
            email = Supa.email
            if (email.isEmpty()) { lastError = L("Server přihlášení přijal, ale nevrátil e-mail.", "Server accepted the sign-in but returned no e-mail.", "Server lieferte keine E-Mail."); return false }
            // my profile from the server (after a reinstall) – or the one on this phone goes up
            val prof = runCatching { Supa.rpc("cf_me_profile") as? kotlinx.serialization.json.JsonObject }.getOrNull()
            val me = Game.P.getValue("hero")
            val sName = prof?.get("name")?.jsonPrimitive?.contentOrNull
            if (!sName.isNullOrBlank()) {
                me.name = sName
                prof["img"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { me.img = it }
            } else if (me.name.isBlank() || me.name == "Ty") me.name = g.displayName?.substringBefore(' ')?.take(20) ?: me.name
            Game.saveLocal(); Game.bump()
            runCatching { Online.syncProfile() }
            runCatching { Social.loadBank() }
            Game.toast(L("Přihlášeno: ", "Signed in: ", "Angemeldet: ") + email)
            return true
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            // Google also reports a wrong app signature / unlisted tester as "cancelled" ([16] …) – show it
            ErrorLog.add("google sign-in cancelled", e)
            lastError = L("Google přihlášení nedokončil", "Google did not finish the sign-in", "Google hat die Anmeldung nicht beendet") +
                ": " + (e.message ?: "-").take(120) + "\n" + sigHint(ctx)
            return false
        } catch (e: Exception) {
            ErrorLog.add("google sign-in", e)
            lastError = (e::class.simpleName ?: "") + ": " + (e.message ?: "").take(160) + "\n" + sigHint(ctx)
            Game.toast(L("Přihlášení přes Google se nepovedlo", "Google sign-in failed", "Google-Anmeldung fehlgeschlagen") + ": " + (e.message ?: "").take(80))
            return false
        } finally { busy = false }
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
