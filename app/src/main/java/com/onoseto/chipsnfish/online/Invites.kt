package com.onoseto.chipsnfish.online

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Time of a scheduled game, as epoch millis (null = right now). */
fun parseAt(s: String?): Long? = s?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
fun isoAt(ms: Long): String = Instant.ofEpochMilli(ms).toString()
fun hhmm(ms: Long): String = DateTimeFormatter.ofPattern("H:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms))

/**
 * Every ~15 minutes (the shortest period Android allows) the app checks for invites and duel challenges
 * from friends, even when it is closed, and shows them as notifications with Accept / Decline.
 * It also reminds both players shortly before a scheduled game.
 */
class InviteWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        if (!SupaCfg.configured) return Result.success()
        runCatching { Invites.check(applicationContext) }
        return Result.success()
    }
}

object Invites {
    private const val CH = "invites"
    const val ACT_YES = "com.onoseto.chipsnfish.INVITE_YES"
    const val ACT_NO = "com.onoseto.chipsnfish.INVITE_NO"

    /** How often to look for invites in the background: 0 = off, else minutes (15 or 60). */
    fun every(ctx: Context) = prefs(ctx).getInt("every", 15)

    fun setEvery(ctx: Context, m: Int) {
        prefs(ctx).edit().putInt("every", m).apply()
        schedule(ctx, ExistingPeriodicWorkPolicy.UPDATE)
    }

    fun schedule(ctx: Context, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
        val m = every(ctx)
        val wm = WorkManager.getInstance(ctx)
        if (m <= 0) { wm.cancelUniqueWork("cf-invites"); return }
        val req = PeriodicWorkRequestBuilder<InviteWorker>(m.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork("cf-invites", policy, req)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cf_invites", Context.MODE_PRIVATE)
    private fun seen(ctx: Context, key: String): Boolean {
        val p = prefs(ctx)
        val set = p.getStringSet("seen", emptySet())!!
        if (key in set) return true
        p.edit().putStringSet("seen", (set + key).toList().takeLast(200).toSet()).apply()
        return false
    }

    private fun lang(ctx: Context) = ctx.getSharedPreferences("chipsfish", Context.MODE_PRIVATE).getString("hr-lang", "cs")?.trim('"') ?: "cs"
    private fun l(ctx: Context, cs: String, en: String, de: String) = when (lang(ctx)) { "en" -> en; "de" -> de; else -> cs }

    suspend fun check(ctx: Context) {
        Supa.init(ctx)
        if (Supa.uid.isEmpty()) return
        val now = System.currentTimeMillis()
        val inbox = Supa.rpc("cf_inbox") as? JsonArray ?: JsonArray(emptyList())
        for (e in inbox) {
            val o = e.jsonObject
            val id = o["id"]!!.jsonPrimitive.long
            val code = o["code"]!!.jsonPrimitive.content
            val from = o["from"]?.jsonPrimitive?.contentOrNull ?: "?"
            val at = parseAt(o["at"]?.jsonPrimitive?.contentOrNull)
            val duel = o["kind"]?.jsonPrimitive?.contentOrNull == "duel"
            val ans = o["ans"]?.jsonPrimitive?.contentOrNull
            if (ans == null && !seen(ctx, "in$id")) {
                val title = if (duel) l(ctx, "$from tě vyzývá na duel ⚔", "$from challenges you to a duel ⚔", "$from fordert dich zum Duell ⚔")
                            else l(ctx, "$from tě zve ke stolu", "$from invites you to a table", "$from lädt dich an den Tisch ein")
                val text = if (at != null && at > now + 60_000) l(ctx, "Hra v ${hhmm(at)}", "Game at ${hhmm(at)}", "Spiel um ${hhmm(at)}")
                           else l(ctx, "Stůl $code · hraje se teď", "Table $code · playing now", "Tisch $code · jetzt")
                notify(ctx, id, code, title, text, at != null && at > now + 60_000, withActions = true)
            }
            // reminder shortly before an accepted game
            if (ans == "yes" && at != null && at - now in 0..20 * 60_000L && !seen(ctx, "rem$id")) {
                notify(ctx, id, code, l(ctx, "Za chvíli hraješ s $from ⚔", "Game with $from soon ⚔", "Gleich Spiel mit $from ⚔"),
                    l(ctx, "V ${hhmm(at)} u stolu $code", "At ${hhmm(at)} at table $code", "Um ${hhmm(at)} an Tisch $code"), false, withActions = false)
            }
        }
        val out = Supa.rpc("cf_outbox") as? JsonArray ?: JsonArray(emptyList())
        for (e in out) {
            val o = e.jsonObject
            val id = o["id"]!!.jsonPrimitive.long
            val code = o["code"]!!.jsonPrimitive.content
            val to = o["to"]?.jsonPrimitive?.contentOrNull ?: "?"
            val at = parseAt(o["at"]?.jsonPrimitive?.contentOrNull)
            val ans = o["ans"]?.jsonPrimitive?.contentOrNull
            if (ans != null && !seen(ctx, "ans$id")) {
                val txt = if (ans == "yes") l(ctx, "$to přijal(a) výzvu", "$to accepted", "$to hat angenommen") else l(ctx, "$to výzvu odmítl(a)", "$to declined", "$to hat abgelehnt")
                notify(ctx, 100_000 + id, code, txt, at?.let { l(ctx, "Hra v ${hhmm(it)}", "Game at ${hhmm(it)}", "Spiel um ${hhmm(it)}") } ?: code, false, withActions = false)
            }
            if (ans == "yes" && at != null && at - now in 0..20 * 60_000L && !seen(ctx, "orem$id")) {
                notify(ctx, 200_000 + id, code, l(ctx, "Za chvíli hraješ s $to ⚔", "Game with $to soon ⚔", "Gleich Spiel mit $to ⚔"),
                    l(ctx, "V ${hhmm(at)} u stolu $code", "At ${hhmm(at)} at table $code", "Um ${hhmm(at)} an Tisch $code"), false, withActions = false)
            }
        }
    }

    private fun notify(ctx: Context, id: Long, code: String, title: String, text: String, later: Boolean, withActions: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, l(ctx, "Pozvánky a výzvy", "Invites and challenges", "Einladungen"), NotificationManager.IMPORTANCE_HIGH))
        }
        val nid = (id % Int.MAX_VALUE).toInt()
        // tapping the notification (or "Accept" for a game right now) opens the table
        val open = PendingIntent.getActivity(ctx, nid, Intent(Intent.ACTION_VIEW, Uri.parse("chipsnfish://t/$code?inv=$id"), ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (withActions) {
            fun bc(action: String) = PendingIntent.getBroadcast(ctx, nid * 2 + (if (action == ACT_YES) 1 else 0),
                Intent(ctx, InviteReceiver::class.java).setAction(action).putExtra("id", id).putExtra("nid", nid),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(0, l(ctx, "Přijmout", "Accept", "Annehmen"), if (later) bc(ACT_YES) else open)
            b.addAction(0, l(ctx, "Odmítnout", "Decline", "Ablehnen"), bc(ACT_NO))
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(nid, b.build()) }
    }

    fun answer(ctx: Context, id: Long, yes: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { Supa.init(ctx); Supa.rpc("cf_invite_answer", buildJsonObject { put("p_id", id); put("p_yes", yes) }) }
        }
    }
}

/** Accept / Decline straight from the notification. */
class InviteReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val id = i.getLongExtra("id", -1)
        if (id < 0) return
        val yes = i.action == Invites.ACT_YES
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Supa.init(ctx); Supa.rpc("cf_invite_answer", buildJsonObject { put("p_id", id); put("p_yes", yes) }) }
            catch (_: Exception) { }
            finally {
                NotificationManagerCompat.from(ctx).cancel(i.getIntExtra("nid", 0))
                pr.finish()
            }
        }
    }
}
