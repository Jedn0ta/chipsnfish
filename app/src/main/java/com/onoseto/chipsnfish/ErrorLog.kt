package com.onoseto.chipsnfish

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects crashes and errors into a small file in the app, so a player can send it to the author
 * (Settings → App → Send error log). Nothing leaves the phone unless he sends it himself.
 */
object ErrorLog {
    private const val MAX = 200_000
    private lateinit var file: File
    const val TO = "moribundusek@gmail.com"

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "errors.log")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { add("CRASH in ${t.name}", e) }
            prev?.uncaughtException(t, e)
        }
    }

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    fun add(what: String, e: Throwable? = null) {
        if (!::file.isInitialized) return
        runCatching {
            val text = buildString {
                append("[").append(stamp()).append("] ").append(what).append('\n')
                if (e != null) append(e.stackTraceToString().take(4000)).append('\n')
            }
            if (file.length() > MAX) file.writeText(file.readText().takeLast(MAX / 2))
            file.appendText(text)
        }
    }

    fun count(): Int = if (!::file.isInitialized || !file.exists()) 0 else file.readLines().count { it.startsWith("[") }

    fun clear() { if (::file.isInitialized) file.delete() }

    /** Report = app + phone info, the error log, and the app's own recent system log lines. */
    private fun report(ctx: Context): String {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val logcat = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "400", "--pid=${android.os.Process.myPid()}"))
            p.inputStream.bufferedReader().readText().takeLast(60_000)
        }.getOrDefault("")
        return buildString {
            append("Hold'em Chips&Fish ").append(pi.versionName).append(" (#").append(if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()).append(")\n")
            append("Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append("), ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Sent: ").append(stamp()).append("\n\n=== Errors ===\n")
            append(if (::file.isInitialized && file.exists()) file.readText() else "(none)\n")
            append("\n=== System log (this app) ===\n").append(logcat)
        }
    }

    /** Opens the e-mail app (or any share target) with the report attached. */
    fun send(ctx: Context, note: String = "") {
        val f = File(ctx.cacheDir, "chipsfish-log.txt")
        f.writeText((if (note.isNotBlank()) "Note: $note\n\n" else "") + report(ctx))
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(TO))
            .putExtra(Intent.EXTRA_SUBJECT, "Chips&Fish – error log")
            .putExtra(Intent.EXTRA_TEXT, (if (note.isNotBlank()) note + "\n\n" else "") + "Log is attached.")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Chips&Fish").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
