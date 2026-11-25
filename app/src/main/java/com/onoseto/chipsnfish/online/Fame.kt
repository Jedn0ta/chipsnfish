package com.onoseto.chipsnfish.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Game
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Rewards and the hall of fame.
 * - practice vs the computer earns ordinary chips into the wallet (spent on drinks),
 * - online games with friends earn honor points ⭐ and cups 🏆 (counted by the server).
 */
object Fame {
    class Entry(val uid: String, val name: String, val img: String, val pts: Int, val hands: Int, val wins: Int)
    class Cup(val name: String, val players: Int, val tier: String, val t: Long)

    var honor by mutableStateOf<Int?>(null)
    var cups by mutableStateOf<Int?>(null)

    var period by mutableStateOf("week")
    var scope by mutableStateOf("all")
    var top by mutableStateOf<List<Entry>?>(null)
    var myPts by mutableStateOf(0)
    var myRank by mutableStateOf<Int?>(null)
    var champ by mutableStateOf<Pair<String, Int>?>(null)
    var cupList by mutableStateOf<List<Cup>>(emptyList())
    var failed by mutableStateOf(false)

    fun cupIcon(tier: String) = when (tier) { "gold" -> "🏆"; "silver" -> "🥈"; else -> "🥉" }
    fun cupName(tier: String) = when (tier) {
        "gold" -> L("Zlatý pohár", "Gold cup", "Goldpokal")
        "silver" -> L("Stříbrný pohár", "Silver cup", "Silberpokal")
        else -> L("Bronzový pohár", "Bronze cup", "Bronzepokal")
    }
    fun tierFor(players: Int) = if (players >= 7) "gold" else if (players >= 4) "silver" else "bronze"

    /** Wallet / honor summary from cf_wallet or cf_earn. */
    fun applyWallet(o: JsonObject) {
        o["bank"]?.jsonPrimitive?.intOrNull?.let { Social.bank = it }
        o["honor"]?.jsonPrimitive?.intOrNull?.let { honor = it }
        o["cups"]?.jsonPrimitive?.intOrNull?.let { cups = it }
        val bonus = o["bonus"]?.jsonPrimitive?.intOrNull ?: 0
        if (bonus > 0) Game.toast(L("Denní bonus 🎁 +$bonus žetonů do peněženky", "Daily bonus 🎁 +$bonus chips to your wallet", "Tagesbonus 🎁 +$bonus Chips in die Geldbörse"))
    }

    fun load() {
        if (!SupaCfg.configured) { failed = true; return }
        val p = period; val s = scope
        Game.scope.launch {
            try {
                val o = Supa.rpc("cf_fame", buildJsonObject { put("p_period", p); put("p_scope", s) }).jsonObject
                if (p != period || s != scope) return@launch
                top = (o["top"] as? JsonArray)?.map { e ->
                    val x = e.jsonObject
                    Entry(x["uid"]!!.jsonPrimitive.content, x["name"]?.jsonPrimitive?.contentOrNull ?: "?", x["img"]?.jsonPrimitive?.contentOrNull ?: "",
                        x["pts"]?.jsonPrimitive?.intOrNull ?: 0, x["hands"]?.jsonPrimitive?.intOrNull ?: 0, x["wins"]?.jsonPrimitive?.intOrNull ?: 0)
                } ?: emptyList()
                val me = o["me"] as? JsonObject
                myPts = me?.get("pts")?.jsonPrimitive?.intOrNull ?: 0
                myRank = me?.get("rank")?.jsonPrimitive?.intOrNull
                champ = (o["champ"] as? JsonObject)?.let { (it["name"]?.jsonPrimitive?.contentOrNull ?: "?") to (it["pts"]?.jsonPrimitive?.intOrNull ?: 0) }
                cupList = (o["cups"] as? JsonArray)?.map { e ->
                    val x = e.jsonObject
                    Cup(x["name"]?.jsonPrimitive?.contentOrNull ?: "", x["players"]?.jsonPrimitive?.intOrNull ?: 2, x["tier"]?.jsonPrimitive?.contentOrNull ?: "bronze",
                        x["t"]?.jsonPrimitive?.longOrNull ?: 0L)
                } ?: emptyList()
                o["total"]?.jsonPrimitive?.intOrNull?.let { honor = it }
                cups = cupList.size.coerceAtLeast(cups ?: 0)
                failed = false
            } catch (e: Exception) {
                failed = true
                if (top == null) top = emptyList()
            }
        }
    }

    // ---------------- practice earnings ----------------

    private var pending = 0
    private var capToast = false
    private var sending = false

    /** Called after every hand vs the computer. */
    fun practiceHand(net: Int, bb: Int, gameWon: Boolean, opponents: Int) {
        var r = 0
        if (net > 0) r += (net / maxOf(1, bb)).coerceIn(1, 40)
        val bonus = if (gameWon) 25 * maxOf(1, opponents) else 0
        if (r + bonus <= 0) return
        pending += r + bonus
        val me = Social.myPid()
        Game.scope.launch {
            kotlinx.coroutines.delay(500)
            Social.say(me, "+${r + bonus} 💰")
            if (gameWon) Game.toast(L("Výhra! +${r + bonus} žetonů do peněženky 💰", "You won! +${r + bonus} chips to your wallet 💰", "Gewonnen! +${r + bonus} Chips in die Geldbörse 💰"))
        }
        flush()
    }

    fun flush() {
        if (pending <= 0 || !SupaCfg.configured || sending) return
        val n = minOf(pending, 300)
        sending = true
        Game.scope.launch {
            var again = false
            try {
                val o = Supa.rpc("cf_earn", buildJsonObject { put("p_amount", n) }).jsonObject
                pending -= n
                val added = o["added"]?.jsonPrimitive?.intOrNull ?: n
                if (added < n && !capToast) {
                    capToast = true
                    Game.toast(L("Dnešní limit výdělku proti počítači (1 500) je vyčerpaný. Zítra dál!", "Today's practice earnings limit (1,500) is reached. More tomorrow!", "Das heutige Trainingslimit (1.500) ist erreicht. Morgen geht's weiter!"))
                }
                applyWallet(o)
                again = pending > 0
            } catch (_: Exception) { /* stays pending, sent with the next hand */ }
            sending = false
            if (again) flush()
        }
    }
}
