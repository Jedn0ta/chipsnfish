package com.onoseto.chipsnfish.online

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.onoseto.chipsnfish.MainActivity
import com.onoseto.chipsnfish.R
import java.util.concurrent.TimeUnit

/**
 * Keeps the free Supabase project awake: every two days the app quietly pings the server.
 * If the app wasn't opened for five days, the fish sends a friendly reminder.
 */
class KeepAliveWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (SupaCfg.configured) {
            runCatching { Supa.init(ctx); Supa.rpc("cf_ping") }
        }
        KeepAlive.maybeRemind(ctx)
        return Result.success()
    }
}

object KeepAlive {
    private const val DAY = 24L * 3600 * 1000
    private const val CH = "reminders"

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<KeepAliveWorker>(2, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("cf-keepalive", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cf_online", Context.MODE_PRIVATE)

    fun opened(ctx: Context) { prefs(ctx).edit().putLong("lastOpen", System.currentTimeMillis()).apply() }

    fun maybeRemind(ctx: Context) {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val last = p.getLong("lastOpen", now)
        val lastNote = p.getLong("lastNote", 0)
        if (now - last < 5 * DAY || now - lastNote < 5 * DAY) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val lang = ctx.getSharedPreferences("chipsfish", Context.MODE_PRIVATE).getString("hr-lang", "cs")?.trim('"') ?: "cs"
        val lines = when (lang) {
            "en" -> listOf("Blub. Fancy a game?", "The chips are getting bored.", "The fish is waiting at the table.", "Haven't seen you at the table in a while.")
            "de" -> listOf("Blub. Eine Runde?", "Die Chips langweilen sich.", "Der Fisch wartet am Tisch.", "Lange nicht am Tisch gesehen.")
            else -> listOf("Blub. Nezahrajeme si?", "Žetony se nudí.", "Ryba čeká u stolu.", "Dlouho jsi u stolu nebyl.")
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, if (lang == "cs") "Připomínky" else if (lang == "de") "Erinnerungen" else "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle("Chips&Fish")
            .setContentText(lines.random())
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(5, n) }
        p.edit().putLong("lastNote", now).apply()
    }
}
