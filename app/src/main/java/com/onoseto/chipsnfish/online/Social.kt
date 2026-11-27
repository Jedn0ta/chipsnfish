package com.onoseto.chipsnfish.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Game
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Emoji messages and drinks at the table. Drinks are paid from the wallet, never from the chips in play. */
object Social {
    /** The emoji set; the server only ever sees indexes into this list (max 5 per message). */
    val EMOJI = listOf(
        "😂", "😎", "🤔", "😱", "😡", "😭", "🤑", "😏", "🙄", "😴",
        "🥳", "🤯", "😬", "🤞", "👍", "👎", "👏", "🙏", "💪", "👋",
        "🔥", "💀", "🐟", "🦈", "🍀", "🎉", "💸", "💩", "🃏", "❤️",
    )
    const val MAX = 5

    class Drink(val id: String, val icon: String, val cs: String, val en: String, val de: String, val price: Int) {
        fun name() = L(cs, en, de)
    }
    val DRINKS = listOf(
        Drink("coffee", "☕", "Káva", "Coffee", "Kaffee", 20),
        Drink("beer", "🍺", "Pivo", "Beer", "Bier", 50),
        Drink("wine", "🍷", "Víno", "Wine", "Wein", 80),
        Drink("cocktail", "🍹", "Koktejl", "Cocktail", "Cocktail", 120),
        Drink("whisky", "🥃", "Whisky", "Whisky", "Whisky", 150),
        Drink("champagne", "🍾", "Šampaňské", "Champagne", "Champagner", 400),
    )
    fun drink(id: String) = DRINKS.firstOrNull { it.id == id }

    /** Hats are worn on the avatar and stay (online: on the server profile). */
    val HATS = listOf(
        Drink("cap", "🧢", "Kšiltovka", "Cap", "Kappe", 100),
        Drink("helmet", "⛑️", "Helma", "Helmet", "Helm", 120),
        Drink("straw", "👒", "Slamák", "Straw hat", "Strohhut", 150),
        Drink("grad", "🎓", "Absolventská", "Grad cap", "Doktorhut", 200),
        Drink("top", "🎩", "Cylindr", "Top hat", "Zylinder", 300),
        Drink("crown", "👑", "Koruna", "Crown", "Krone", 1000),
    )
    fun hat(id: String) = HATS.firstOrNull { it.id == id }
    fun hatIcon(id: String) = hat(id)?.icon ?: ""

    /** Quick phrases for the short chat. */
    fun phrases() = listOf(
        L("Pěkná hra 👍", "Nice game 👍", "Schönes Spiel 👍"), "GG", L("Hodně štěstí 🍀", "Good luck 🍀", "Viel Glück 🍀"),
        L("Bluf! 😏", "Bluff! 😏", "Bluff! 😏"), L("To jsem nečekal 😱", "Didn't see that coming 😱", "Das kam unerwartet 😱"),
        L("Ještě jednu?", "One more?", "Noch eine?"), L("Díky 🙏", "Thanks 🙏", "Danke 🙏"), L("Sorry 😅", "Sorry 😅", "Sorry 😅"),
        L("Pospěš si ⏳", "Hurry up ⏳", "Beeil dich ⏳"), L("Na zdraví 🍻", "Cheers 🍻", "Prost 🍻"),
    )
    const val TEXT_MAX = 60
    private val BAD = listOf("kurv", "píč", "pič", "prdel", "kokot", "debil", "zmrd", "hovn", "čurák", "curak", "mrdk", "šuk", "fuck", "shit", "bitch", "cunt", "dick", "asshole")
    /** Short chat: one line, max 60 characters, rude words starred out. */
    fun clean(t: String): String {
        val one = t.replace(Regex("\\s+"), " ").trim().take(TEXT_MAX)
        return one.split(" ").joinToString(" ") { w -> if (BAD.any { w.lowercase().contains(it) }) w.take(1) + "*".repeat((w.length - 1).coerceIn(2, 6)) else w }
    }
    var myHats by mutableStateOf<List<String>>(emptyList())

    class Bubble(val text: String, val id: Long)
    class TDrink(val id: Long, val from: String, val to: String, val kind: String, val t: Long)
    class Fly(val id: Long, val from: String, val to: String, val icon: String)

    /** pid → current speech bubble */
    val bubbles = mutableStateMapOf<String, Bubble>()
    var drinks by mutableStateOf<List<TDrink>>(emptyList())
    val flies = mutableStateListOf<Fly>()
    var bank by mutableStateOf<Int?>(null)

    // panel state
    var open by mutableStateOf(false)
    var tab by mutableStateOf("emoji")
    var target by mutableStateOf<String?>(null)

    private var lastChat = -1L
    private var lastDrink = -1L
    private var localId = 1_000_000_000_000L
    private const val KEEP = 15 * 60 * 1000L

    fun myPid(): String = Game.st.seats.getOrNull(0)?.pid ?: "hero"
    fun available() = Game.online() || (Game.bot() && Game.playing())

    fun openPanel(t: String = "emoji", who: String? = null) {
        tab = t; target = who ?: target; open = true
        if (t == "drink" || t == "hat" || bank == null) loadBank()
        if (t == "hat") loadHats()
    }

    fun resetTable() {
        bubbles.clear(); drinks = emptyList(); flies.clear(); lastChat = -1; lastDrink = -1; open = false; target = null
    }

    fun say(pid: String, text: String) { bubbles[pid] = Bubble(text, localId++) }

    /** Drinks on the table for this player, newest first (only the last 15 minutes). */
    fun drinksFor(pid: String): List<TDrink> {
        val now = System.currentTimeMillis()
        return drinks.filter { it.to == pid && !it.kind.startsWith("hat:") && now - it.t < KEEP }.sortedByDescending { it.id }
    }

    // ---------------- sending ----------------

    fun send(idx: List<Int>) {
        if (idx.isEmpty()) return
        val m = idx.take(MAX)
        say(myPid(), m.joinToString("") { EMOJI[it] })
        if (Game.online()) {
            val c = Online.code ?: return
            Game.scope.launch {
                try { Supa.rpc("cf_say", buildJsonObject { put("p_code", c); put("p_msg", m.joinToString(",")) }) }
                catch (e: Exception) { Game.toast(err(e)) }
            }
        } else botsAnswer(m)
    }

    fun sendText(raw: String) {
        val t = clean(raw)
        if (t.isEmpty()) return
        say(myPid(), t)
        if (Game.online()) {
            val c = Online.code ?: return
            Game.scope.launch {
                try { Supa.rpc("cf_say_text", buildJsonObject { put("p_code", c); put("p_text", t) }) }
                catch (e: Exception) { Game.toast(err(e)) }
            }
        } else botsAnswer(emptyList())
    }

    /** Buy a hat for someone at the table or for yourself; it goes straight on their head. */
    fun buyHat(to: String, kind: String, onOk: () -> Unit = {}) {
        val d = hat(kind) ?: return
        val b = bank
        if (b != null && b < d.price) { Game.toast(L("V peněžence nemáš dost žetonů.", "Not enough chips in your wallet.", "Nicht genug Chips in deiner Geldbörse.")); return }
        val self = to == myPid()
        Game.scope.launch {
            try {
                val online = Game.online()
                if (!SupaCfg.configured) throw IllegalStateException("offline")
                Supa.ensureAuth()
                val r = Supa.rpc("cf_hat_buy", buildJsonObject {
                    if (online) put("p_code", Online.code) else put("p_code", JsonNull)
                    if (online && !self) put("p_to", to) else put("p_to", JsonNull)
                    put("p_kind", kind); put("p_self", self)
                }).jsonObject
                Fame.applyWallet(r)
                if (self) { myHats = (myHats + kind).distinct() }
                onOk()
                if (online) Online.poll()
                else {
                    Game.P[to]?.hat = kind
                    if (!self) {
                        flies.add(Fly(localId++, myPid(), to, d.icon)); Game.saveLocal()
                        Game.scope.launch { delay(1600); say(to, listOf("😎", "🙏", "🥳", "👍", "❤️").random()) }
                    }
                    Game.bump()
                }
            } catch (e: Exception) { Game.toast(err(e)) }
        }
    }

    fun wearHat(kind: String) {
        Game.scope.launch {
            try {
                Supa.rpc("cf_hat_wear", buildJsonObject { put("p_kind", kind) })
                Game.P["hero"]?.hat = kind; Game.P[Online.me]?.hat = kind; Game.bump()
                if (Game.online()) Online.poll()
            } catch (e: Exception) { Game.toast(err(e)) }
        }
    }

    fun loadHats() {
        if (!SupaCfg.configured) return
        Game.scope.launch {
            try {
                val o = Supa.rpc("cf_my_hats").jsonObject
                myHats = (o["hats"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val h = o["hat"]?.jsonPrimitive?.contentOrNull ?: ""
                Game.P["hero"]?.hat = h; Game.P[Online.me]?.hat = h; Game.bump()
            } catch (_: Exception) { }
        }
    }

    fun buy(to: String, kind: String, onOk: () -> Unit = {}) {
        val d = drink(kind) ?: return
        val b = bank
        if (b != null && b < d.price) { Game.toast(L("V peněžence nemáš dost žetonů.", "Not enough chips in your wallet.", "Nicht genug Chips in deiner Geldbörse.")); return }
        Game.scope.launch {
            try {
                val online = Game.online()
                if (!SupaCfg.configured) throw IllegalStateException("offline")
                Supa.ensureAuth()
                val r = Supa.rpc("cf_drink", buildJsonObject {
                    if (online) put("p_code", Online.code) else put("p_code", JsonNull)
                    if (online) put("p_to", to) else put("p_to", JsonNull)
                    put("p_kind", kind)
                })
                bank = r.jsonPrimitive.intOrNull ?: bank
                onOk()
                if (online) Online.poll()
                else {
                    val id = localId++
                    drinks = drinks + TDrink(id, myPid(), to, kind, System.currentTimeMillis())
                    flies.add(Fly(id, myPid(), to, d.icon))
                    if (to != myPid()) Game.scope.launch {
                        delay(1600)
                        say(to, listOf("🙏", "🍻", "😎👍", "❤️", "🥳", "🙏🍀").random())
                    }
                }
            } catch (e: Exception) { Game.toast(err(e)) }
        }
    }

    fun loadBank() {
        if (!SupaCfg.configured) return
        Game.scope.launch {
            try {
                Supa.ensureAuth()
                Fame.applyWallet(Supa.rpc("cf_wallet").jsonObject)
                Fame.flush()
            } catch (_: Exception) { }
        }
    }

    private fun err(e: Exception): String {
        val m = e.message ?: ""
        return when {
            m.contains("no money") -> L("V peněžence nemáš dost žetonů.", "Not enough chips in your wallet.", "Nicht genug Chips in deiner Geldbörse.")
            m.contains("too fast") -> L("Pomaleji 🙂", "Slow down 🙂", "Langsamer 🙂")
            m.contains("bad message") -> L("Zpráva je moc dlouhá (max 60 znaků).", "Message too long (max 60 characters).", "Nachricht zu lang (max. 60 Zeichen).")
            m == "offline" -> L("Drinky jdou koupit jen s připojením k internetu.", "Drinks need an internet connection.", "Drinks brauchen eine Internetverbindung.")
            m.contains("Could not find the function", true) || m.contains("PGRST202") ->
                L("Na serveru chybí aktualizace (spusť znovu schema.sql).", "The server needs an update (run schema.sql again).", "Der Server braucht ein Update (schema.sql erneut ausführen).")
            else -> Online.err(e)
        }
    }

    // ---------------- receiving (online) ----------------

    fun ingest(chat: JsonElement?, dr: JsonElement?) {
        val me = Online.me
        (chat as? JsonArray)?.let { a ->
            val first = lastChat < 0
            var mx = lastChat
            for (e in a) {
                val o = e.jsonObject
                val id = o["id"]!!.jsonPrimitive.long
                if (id <= lastChat) continue
                mx = maxOf(mx, id)
                val uid = o["uid"]?.jsonPrimitive?.contentOrNull ?: continue
                if (first || uid == me) continue
                val raw = o["m"]?.jsonPrimitive?.contentOrNull ?: ""
                val txt = if (raw.startsWith("t:")) clean(raw.drop(2))
                    else raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in EMOJI.indices }.take(MAX).joinToString("") { EMOJI[it] }
                if (txt.isNotEmpty()) bubbles[uid] = Bubble(txt, id)
            }
            lastChat = maxOf(mx, 0)
        }
        (dr as? JsonArray)?.let { a ->
            val first = lastDrink < 0
            val now = System.currentTimeMillis()
            val list = a.map { e ->
                val o = e.jsonObject
                TDrink(o["id"]!!.jsonPrimitive.long, o["from"]?.jsonPrimitive?.contentOrNull ?: "", o["to"]?.jsonPrimitive?.contentOrNull ?: "",
                    o["kind"]?.jsonPrimitive?.contentOrNull ?: "", now - (o["age"]?.jsonPrimitive?.long ?: 0L))
            }
            if (!first) for (d in list) if (d.id > lastDrink) {
                val isHat = d.kind.startsWith("hat:")
                val dk = (if (isHat) hat(d.kind.drop(4)) else drink(d.kind)) ?: continue
                flies.add(Fly(d.id, d.from, d.to, dk.icon))
                if (d.to == me && d.from != me) {
                    val who = Game.P[d.from]?.name ?: "?"
                    Game.toast(L("$who ti koupil(a) ${dk.icon} ${dk.cs}!", "$who bought you ${dk.icon} ${dk.en}!", "$who hat dir ${dk.icon} ${dk.de} spendiert!"))
                    if (isHat) myHats = (myHats + d.kind.drop(4)).distinct()
                }
            }
            lastDrink = maxOf(lastDrink, list.maxOfOrNull { it.id } ?: 0L, 0L)
            drinks = list
        }
    }

    // ---------------- computer players (practice) ----------------

    private val BOT_REPLY = listOf("😂", "😎", "🤔", "😏", "🙄", "👍", "🍀", "🦈", "💪", "😬", "🤞", "🐟", "🔥")

    private fun botsAnswer(sent: List<Int>) {
        val bots = Game.st.seats.drop(1).filter { !it.out }.map { it.pid }
        if (bots.isEmpty() || Math.random() > 0.55) return
        val who = bots.random()
        val reply = if (sent.any { EMOJI[it] in listOf("👋", "❤️", "🙏", "👏") } && Math.random() < 0.6) listOf("👋", "😎", "👍", "🙏").random()
            else List(if (Math.random() < 0.3) 2 else 1) { BOT_REPLY.random() }.joinToString("")
        Game.scope.launch { delay(900 + (Math.random() * 1400).toLong()); say(who, reply) }
    }

    /** After a hand vs the computer: sometimes a winner gloats or a loser sulks. */
    fun botsReact(winners: List<Int>, big: Boolean) {
        if (!Game.bot() || Math.random() > (if (big) 0.45 else 0.18)) return
        val st = Game.st
        val w = winners.filter { it > 0 }.randomOrNull()
        val lose = st.seats.indices.filter { it > 0 && it !in winners && !st.seats[it].folded && !st.seats[it].out }.randomOrNull()
        val (k, set) = if (w != null && (lose == null || Math.random() < 0.6)) w to listOf("🤑", "😎", "💪", "🦈", "🔥", "😏")
            else if (lose != null) lose to listOf("😭", "😡", "🙄", "💀", "😬", "🤯") else return
        val pid = st.seats[k].pid
        Game.scope.launch { delay(700); say(pid, set.random()) }
    }
}
