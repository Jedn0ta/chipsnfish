package com.onoseto.chipsnfish

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.onoseto.chipsnfish.game.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates come from Google Play: the Play build uses Play's in-app update screen,
 * the old GitHub APK only points people to the Play Store.
 */
object Updater {
    const val PLAY_PKG = "com.onoseto.chipsnfish"
    /** Opt-in link of the internal test on Google Play (replace with the link from Play Console → Testers). */
    const val PLAY_TEST = "https://play.google.com/apps/internaltest/"
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=$PLAY_PKG"
    private var info: com.google.android.play.core.appupdate.AppUpdateInfo? = null

    private const val API = "https://api.github.com/repos/Jedn0ta/chipsnfish/releases/tags/latest"
    const val APK = "https://github.com/Jedn0ta/chipsnfish/releases/download/latest/ChipsNFish.apk"

    var state by mutableStateOf("idle") // idle, checking, latest, available, downloading, ready, error
    var latest by mutableIntStateOf(0)
    var progress by mutableFloatStateOf(0f)
    private var apkUrl = APK
    private var lastCheck = 0L

    fun current(ctx: Context): Pair<Int, String> {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
        return code to (pi.versionName ?: "")
    }

    fun check(ctx: Context, quiet: Boolean = false) {
        if (BuildConfig.SELF_UPDATE) { state = "moved"; return }
        if (quiet && System.currentTimeMillis() - lastCheck < 3600_000L) return
        lastCheck = System.currentTimeMillis()
        if (!quiet) state = "checking"
        try {
            com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(ctx).appUpdateInfo
                .addOnSuccessListener { i ->
                    info = i
                    latest = i.availableVersionCode()
                    state = when (i.updateAvailability()) {
                        com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE,
                        com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> "available"
                        else -> "latest"
                    }
                }
                .addOnFailureListener { if (!quiet) state = "error" else if (state == "checking") state = "idle" }
        } catch (e: Exception) { if (!quiet) state = "error" }
    }

    /** Play's own full-screen update; the app restarts by itself when it's done. */
    fun update(act: android.app.Activity) {
        val i = info
        if (i == null || state != "available") { openStore(act); return }
        try {
            Game.saveLocal()
            com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(act).startUpdateFlow(
                i, act, com.google.android.play.core.appupdate.AppUpdateOptions.defaultOptions(com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE))
                .addOnFailureListener { openStore(act) }
        } catch (e: Exception) { openStore(act) }
    }

    /** Resumes an update Play already started (call from onResume). */
    fun resume(act: android.app.Activity) {
        if (BuildConfig.SELF_UPDATE) return
        try {
            val m = com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(act)
            m.appUpdateInfo.addOnSuccessListener { i ->
                if (i.updateAvailability() == com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
                    m.startUpdateFlow(i, act, com.google.android.play.core.appupdate.AppUpdateOptions.defaultOptions(com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE))
            }
        } catch (_: Exception) { }
    }

    fun openStore(ctx: Context, url: String = PLAY_URL) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_PKG")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(if (url == PLAY_URL) market else web) } catch (_: Exception) { runCatching { ctx.startActivity(web) } }
    }

    /** Old GitHub check, kept only for reference builds. */
    @Suppress("unused")
    private fun checkGithub(ctx: Context, quiet: Boolean = false) {
        if (quiet && System.currentTimeMillis() - lastCheck < 6 * 3600_000L) return
        lastCheck = System.currentTimeMillis()
        if (!quiet) state = "checking"
        Game.scope.launch {
            try {
                val txt = withContext(Dispatchers.IO) {
                    val c = URL(API).openConnection() as HttpURLConnection
                    c.connectTimeout = 10000; c.readTimeout = 15000
                    c.setRequestProperty("Accept", "application/vnd.github+json")
                    c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
                }
                val o = Json.parseToJsonElement(txt).jsonObject
                val name = o["name"]?.jsonPrimitive?.content ?: ""
                latest = Regex("#(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                apkUrl = o["assets"]?.jsonArray?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                    ?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content ?: APK
                state = if (latest > current(ctx).first) "available" else "latest"
            } catch (e: Exception) {
                if (!quiet) state = "error" else if (state == "checking") state = "idle"
            }
        }
    }

    fun download(ctx: Context) {
        state = "downloading"; progress = 0f
        val f = File(ctx.cacheDir, "update.apk")
        Game.scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val c = URL(apkUrl).openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = true
                    c.connectTimeout = 15000; c.readTimeout = 30000
                    val total = c.contentLengthLong.coerceAtLeast(1)
                    c.inputStream.use { inp ->
                        f.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = inp.read(buf); if (n < 0) break
                                out.write(buf, 0, n); done += n
                                val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                withContext(Dispatchers.Main) { progress = p }
                            }
                        }
                    }
                    c.disconnect()
                }
                state = "ready"
                install(ctx)
            } catch (e: Exception) { state = "error" }
        }
    }

    fun install(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            Game.toast(L("Povol instalaci z téhle aplikace a pak klepni na Instalovat znovu.", "Allow installs from this app, then tap Install again.", "Erlaube Installationen aus dieser App und tippe dann erneut auf Installieren."))
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)))
            return
        }
        val f = File(ctx.cacheDir, "update.apk")
        if (!f.exists()) { download(ctx); return }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        Game.saveLocal()
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
