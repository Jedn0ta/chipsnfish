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

    fun load() { email = Supa.email }
    val signedIn get() = email.isNotEmpty()

    suspend fun signIn(ctx: Context): Boolean {
        if (!available || busy) return false
        busy = true
        try {
            val raw = UUID.randomUUID().toString()
            val hashed = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
            val opt = GetSignInWithGoogleOption.Builder(SupaCfg.GOOGLE_WEB_CLIENT_ID).setNonce(hashed).build()
            val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
            val c = CredentialManager.create(ctx).getCredential(ctx, req).credential
            if (c !is CustomCredential || c.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return false
            val g = GoogleIdTokenCredential.createFrom(c.data)
            Supa.signInGoogle(g.idToken, raw)
            email = Supa.email
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
            return false
        } catch (e: Exception) {
            ErrorLog.add("google sign-in", e)
            Game.toast(L("Přihlášení přes Google se nepovedlo", "Google sign-in failed", "Google-Anmeldung fehlgeschlagen") + ": " + (e.message ?: "").take(80))
            return false
        } finally { busy = false }
    }
}
