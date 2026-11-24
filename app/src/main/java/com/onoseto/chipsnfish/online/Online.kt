package com.onoseto.chipsnfish.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Cards
import com.onoseto.chipsnfish.game.Done
import com.onoseto.chipsnfish.game.GState
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.game.HandRec
import com.onoseto.chipsnfish.game.Player
import com.onoseto.chipsnfish.game.Reads
import com.onoseto.chipsnfish.game.RecSeat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.math.max

class OPlayer(val uid: String, val name: String, val img: String?, val on: Boolean, val pending: Boolean, val left: Boolean)
class OTable(val code: String, val name: String, val status: String, val players: Int, val host: Boolean = false)
class OGroup(val id: String, val name: String, val owner: String, val members: List<OPlayer>, val tables: List<OTable>)

/** The online table session: polls the server about once a second and turns its state into the local view. */
object Online {
    var code by mutableStateOf<String?>(null)
    var host by mutableStateOf("")
    var name by mutableStateOf("")
    var gid by mutableStateOf<String?>(null)
    var advisor by mutableStateOf(true)
    var status by mutableStateOf("lobby")
    var players by mutableStateOf<List<OPlayer>>(emptyList())
    var connected by mutableStateOf(true)
    var busy by mutableStateOf(false)
    var rev by mutableIntStateOf(0)
    var cfg: JsonObject = JsonObject(emptyMap())

    private var ver = 0
    private var raw: GState? = null
    private var hole: List<Int>? = null
    private var holeHand = 0
    private var shows: Map<String, List<Int>> = emptyMap()
    private var timeOffset = 0L
    private var pollJob: Job? = null
    private var lastTimeoutCall = 0L
    private var lastNextCall = 0L
    private val learned = HashSet<Int>()
    private val learnedShows = HashSet<String>()
    var shownHand by mutableIntStateOf(0)

    val me get() = Supa.uid
    val isHost get() = host == me && me.isNotEmpty()
    fun serverNow() = System.currentTimeMillis() + timeOffset
    fun inTable() = code != null

    fun err(e: Throwable): String {
        val m = e.message ?: ""
        return when {
            m.contains("not found", true) || m.contains("notfound") -> L("Stůl s tímhle kódem neexistuje.", "There's no table with this code.", "Kein Tisch mit diesem Code.")
            m.contains("full") -> L("Stůl je plný (max. 10 hráčů).", "The table is full (max 10 players).", "Der Tisch ist voll (max. 10 Spieler).")
            m.contains("game over") -> L("Tahle hra už skončila.", "This game is already over.", "Dieses Spiel ist schon vorbei.")
            m.contains("need two") -> L("Ke hře jsou potřeba aspoň dva hráči.", "At least two players are needed.", "Es braucht mindestens zwei Spieler.")
            e is java.io.IOException -> L("Nejde se připojit k internetu.", "Can't connect to the internet.", "Keine Internetverbindung.")
            m.contains("anonymous", true) || m.contains("signup", true) -> L("Na serveru není zapnuté anonymní přihlášení.", "Anonymous sign-in is not enabled on the server.", "Anonyme Anmeldung ist auf dem Server nicht aktiviert.")
            else -> L("Chyba serveru: ", "Server error: ", "Serverfehler: ") + m.take(120)
        }
    }

    private fun args(vararg p: Pair<String, Any?>) = buildJsonObject {
        for ((k, v) in p) when (v) {
            null -> put(k, JsonNull)
            is String -> put(k, v)
            is Int -> put(k, v)
            is Boolean -> put(k, v)
            is JsonElement -> put(k, v)
            else -> put(k, v.toString())
        }
    }

    /** Runs a server call with a toast on failure. */
    fun call(fn: String, vararg p: Pair<String, Any?>, then: (JsonElement) -> Unit = {}) {
        Game.scope.launch {
            busy = true
            try { val r = Supa.rpc(fn, args(*p)); then(r); poll() }
            catch (e: Exception) { Game.toast(err(e)) }
            finally { busy = false }
        }
    }

    suspend fun syncProfile() {
        val h = Game.P.getValue("hero")
        Supa.rpc("cf_profile", args("p_name" to h.name, "p_img" to h.img))
    }

    // ---------------- session ----------------

    fun open(c: String) {
        leaveLocal()
        code = c.uppercase()
        ver = 0; raw = null; hole = null; shows = emptyMap(); learned.clear(); learnedShows.clear()
        Game.enterOnline()
        startPolling()
    }

    /** Back to the table after visiting the menu or the game vs computer. */
    fun resume() {
        if (code == null) return
        Game.enterOnline()
        ver = 0
        startPolling()
    }

    fun leaveLocal() {
        pollJob?.cancel(); pollJob = null
        code = null
        players = emptyList()
    }

    fun startPolling() {
        if (code == null) return
        pollJob?.cancel()
        pollJob = Game.scope.launch {
            while (isActive && code != null) {
                try { poll(); connected = true } catch (e: Exception) { connected = false }
                val myTurn = Game.mode == "online" && Game.st.turn == 0 && Game.playing()
                delay(if (myTurn) 1500 else 900)
            }
        }
    }

    fun stopPolling() { pollJob?.cancel(); pollJob = null }

    suspend fun poll() {
        val c = code ?: return
        val r = Supa.rpc("cf_view", args("p_code" to c, "p_ver" to ver)).jsonObject
        if (r["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            Game.toast(L("Stůl už neexistuje.", "The table no longer exists.", "Der Tisch existiert nicht mehr."))
            leaveLocal(); Game.leaveOnline(); return
        }
        if (code != c) return
        timeOffset = r["now"]!!.jsonPrimitive.long - System.currentTimeMillis()
        host = r["host"]?.jsonPrimitive?.contentOrNull ?: ""
        name = r["name"]?.jsonPrimitive?.contentOrNull ?: ""
        gid = r["gid"]?.jsonPrimitive?.contentOrNull
        status = r["status"]?.jsonPrimitive?.contentOrNull ?: "lobby"
        cfg = r["cfg"] as? JsonObject ?: JsonObject(emptyMap())
        advisor = cfg["advisor"]?.jsonPrimitive?.booleanOrNull ?: true
        val oldImgs = players.associate { it.uid to it.img }
        players = r["players"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            val uid = o["uid"]!!.jsonPrimitive.content
            OPlayer(uid, o["name"]?.jsonPrimitive?.contentOrNull ?: "?", o["img"]?.jsonPrimitive?.contentOrNull ?: oldImgs[uid],
                o["on"]?.jsonPrimitive?.booleanOrNull ?: false, o["pending"]?.jsonPrimitive?.booleanOrNull ?: false, o["left"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
        val h = r["hole"]?.jsonObject
        holeHand = h?.get("hand")?.jsonPrimitive?.intOrNull ?: 0
        hole = (h?.get("cards") as? JsonArray)?.map { it.jsonPrimitive.int }
        shows = r["shows"]!!.jsonArray.associate { e -> val o = e.jsonObject; o["uid"]!!.jsonPrimitive.content to o["cards"]!!.jsonArray.map { it.jsonPrimitive.int } }
        val stEl = r["state"]
        if (stEl != null && stEl !is JsonNull) raw = Game.json.decodeFromJsonElement(GState.serializer(), stEl)
        ver = r["ver"]!!.jsonPrimitive.int
        rebuild()
        autoActions()
    }

    /** Rotates the server state so that I sit at index 0, like the single-player view expects. */
    private fun rebuild() {
        val s = raw ?: return
        val n = s.seats.size
        val my = s.seats.indexOfFirst { it.pid == me }
        val rot = if (my < 0) 0 else my
        fun m(k: Int) = if (k < 0) k else (k - rot + n) % n
        val v = Game.json.decodeFromString(GState.serializer(), Game.json.encodeToString(GState.serializer(), s))
        v.mode = "online"
        v.n = n
        v.seats = MutableList(n) { s.seats[(it + rot) % n] }
        v.dealer = m(s.dealer)
        v.turn = m(s.turn)
        v.hero = if (my >= 0 && hole != null && holeHand == s.handNo) mutableListOf(hole!![0], hole!![1]) else mutableListOf(null, null)
        s.done?.let { d -> v.done = Done(d.w.map { m(it) }, List(n) { d.amt.getOrElse((it + rot) % n) { 0 } }, d.sd, d.cat.mapKeys { m(it.key) }, 0) }
        val rv = HashMap<Int, List<Int>>()
        for ((uid, cs) in shows) { val k = s.seats.indexOfFirst { it.pid == uid }; if (k >= 0 && uid != me) rv[m(k)] = cs }
        v.reveal = if (rv.isEmpty()) null else rv.toMutableMap()
        v.pos = null
        // player profiles
        for (op in players) {
            val p = Game.P.getOrPut(op.uid) { Player(op.uid, op.name, stored = op.uid != me) }
            p.name = if (op.uid == me) Game.P.getValue("hero").name else op.name
            if (op.img != null) p.img = op.img
            if (op.uid != me) p.stored = true
        }
        for (st in v.seats) if (Game.P[st.pid] == null) Game.P[st.pid] = Player(st.pid, "?", stored = st.pid != me)
        Game.setOnlineView(v)
        learn(v, s)
        rev++
    }

    /** Opponent statistics and my hand history, from what is public. */
    private fun learn(v: GState, s: GState) {
        val d = v.done ?: return
        if (s.handNo !in learned) {
            learned.add(s.handNo)
            v.seats.forEachIndexed { k, seat ->
                if (k == 0 || seat.out) return@forEachIndexed
                val p = Game.P[seat.pid] ?: return@forEachIndexed
                val pre = seat.actions.filter { it.st == 0 }; val post = seat.actions.filter { it.st > 0 }
                p.hands++
                if (pre.any { it.a == "call" || Reads.agg(it.a) }) p.vp++
                if (pre.any { Reads.agg(it.a) }) p.pfr++
                p.agg += post.count { Reads.agg(it.a) }
                p.calls += post.count { it.a == "call" }
                p.t = System.currentTimeMillis()
            }
            if (v.seats.isNotEmpty() && v.seats[0].pid == me && !v.hero.contains(null)) {
                val myWin = d.amt.getOrElse(0) { 0 }
                val res = if (myWin <= 0) "lose" else if (d.w.size > 1 && d.w.contains(0)) "split" else "win"
                Game.H.add(0, HandRec(
                    id = Game.uid("h"), no = v.handNo, t = System.currentTimeMillis(), n = v.n, blinds = Cards.lvlStr(v.curLevel),
                    street = v.betStreet, hero = v.hero.toList(), board = v.board.toList(), result = res,
                    winner = d.w.firstOrNull { it != 0 }?.let { v.seats[it].pid }, eq = null, pot = v.seats.sumOf { it.total },
                    net = myWin - v.seats[0].total,
                    seats = v.seats.map { RecSeat(it.pid, Game.P[it.pid]?.name ?: "?", null, it.folded, it.actions.toList(), "none", null) },
                ))
            }
            Game.saveLocal()
        }
        val bd = v.board.filterNotNull()
        for ((uid, cs) in shows) {
            val key = "${s.handNo}:$uid"
            if (uid == me || key in learnedShows || bd.size < 3) continue
            learnedShows.add(key)
            val k = v.seats.indexOfFirst { it.pid == uid }
            val p = Game.P[uid] ?: continue
            val c = Cards.cat(Cards.ev(cs + bd))
            val ag = k >= 0 && v.seats[k].actions.any { it.st > 0 && Reads.agg(it.a) }
            var rk = "none"
            if (ag && c <= 1 && k !in d.w) { p.caught++; rk = "bluff" } else if (c >= 2) { p.showed++; rk = "strong" }
            val rec = Game.H.firstOrNull { it.no == s.handNo }
            if (rec != null) {
                val i = Game.H.indexOf(rec)
                Game.H[i] = rec.copy(seats = rec.seats.map { if (it.pid == uid) it.copy(cards = cs, reveal = rk) else it })
            }
            Game.saveLocal()
        }
    }

    /** Keeps the game moving: idle players time out after 60 s, next hand starts by itself. */
    private fun autoActions() {
        val s = raw ?: return
        if (s.phase != "play" || s.over != null) return
        val now = serverNow()
        if (s.done == null && s.turn >= 0 && now > s.turnAt + 61000 && System.currentTimeMillis() - lastTimeoutCall > 5000) {
            lastTimeoutCall = System.currentTimeMillis()
            call("cf_timeout", "p_code" to code)
        }
        val doneAt = s.doneAt
        if (s.done != null && now > doneAt + (if (isHost) 8000 else 11000) && System.currentTimeMillis() - lastNextCall > 4000) {
            lastNextCall = System.currentTimeMillis()
            call("cf_next", "p_code" to code)
        }
    }

    // ---------------- actions ----------------

    fun move(type: String, to: Int = 0) {
        val s = raw ?: return
        call("cf_move", "p_code" to code, "p_hand" to s.handNo, "p_type" to type, "p_to" to to)
    }

    fun start() = call("cf_start", "p_code" to code)
    fun next() = call("cf_next", "p_code" to code)
    fun restart() = call("cf_restart", "p_code" to code)
    fun setAdvisor(on: Boolean) = call("cf_set_advisor", "p_code" to code, "p_on" to on)
    fun show() {
        val s = raw ?: return
        shownHand = s.handNo
        call("cf_show", "p_code" to code, "p_hand" to s.handNo)
    }
    fun muck() { raw?.let { shownHand = it.handNo }; rev++ }
    fun didShow(): Boolean = raw?.let { s -> shows.containsKey(me) || shownHand == s.handNo } ?: false

    fun leave(after: () -> Unit = {}) {
        val c = code ?: return
        Game.scope.launch {
            runCatching { Supa.rpc("cf_leave", args("p_code" to c)) }
            leaveLocal(); Game.leaveOnline(); after()
        }
    }

    // ---------------- lobby / groups ----------------

    fun create(tname: String, stack: Int, level: Int, every: Int, adv: Boolean, group: String?, onOk: (String) -> Unit) {
        Game.scope.launch {
            busy = true
            try {
                syncProfile()
                val cfgJ = buildJsonObject { put("stack", stack); put("level", level); put("every", every); put("advisor", adv) }
                val c = Supa.rpc("cf_create", args("p_name" to tname, "p_cfg" to cfgJ, "p_gid" to group)).jsonPrimitive.content
                onOk(c); open(c)
            } catch (e: Exception) { Game.toast(err(e)) } finally { busy = false }
        }
    }

    fun join(c0: String, onOk: () -> Unit = {}) {
        val c = c0.trim().uppercase().filter { it.isLetterOrDigit() }
        if (c.length < 4) { Game.toast(L("Zadej kód stolu.", "Enter the table code.", "Gib den Tischcode ein.")); return }
        Game.scope.launch {
            busy = true
            try {
                syncProfile()
                val r = Supa.rpc("cf_join", args("p_code" to c)).jsonObject
                onOk(); open(r["code"]!!.jsonPrimitive.content)
            } catch (e: Exception) { Game.toast(err(e)) } finally { busy = false }
        }
    }

    var groups by mutableStateOf<List<OGroup>?>(null)
    var myTables by mutableStateOf<List<OTable>>(emptyList())

    private fun parsePlayers(a: JsonElement?): List<OPlayer> = (a as? JsonArray)?.map { e ->
        val o = e.jsonObject
        OPlayer(o["uid"]!!.jsonPrimitive.content, o["name"]?.jsonPrimitive?.contentOrNull ?: "?", o["img"]?.jsonPrimitive?.contentOrNull, true, false, false)
    } ?: emptyList()

    private fun parseTables(a: JsonElement?): List<OTable> = (a as? JsonArray)?.map { e ->
        val o = e.jsonObject
        OTable(o["code"]!!.jsonPrimitive.content, o["name"]?.jsonPrimitive?.contentOrNull ?: "", o["status"]?.jsonPrimitive?.contentOrNull ?: "",
            o["players"]?.jsonPrimitive?.intOrNull ?: 0, o["host"]?.jsonPrimitive?.booleanOrNull ?: false)
    } ?: emptyList()

    fun loadLists() {
        if (!SupaCfg.configured) return
        Game.scope.launch {
            try {
                val g = Supa.rpc("cf_groups") as JsonArray
                groups = g.map { e ->
                    val o = e.jsonObject
                    OGroup(o["id"]!!.jsonPrimitive.content, o["name"]!!.jsonPrimitive.content, o["owner"]!!.jsonPrimitive.content,
                        parsePlayers(o["members"]), parseTables(o["tables"]))
                }
                myTables = parseTables(Supa.rpc("cf_my_tables"))
            } catch (e: Exception) { if (groups == null) groups = emptyList(); Game.toast(err(e)) }
        }
    }

    fun createGroup(n: String, onOk: (String) -> Unit) {
        Game.scope.launch {
            try { syncProfile(); val id = Supa.rpc("cf_group_create", args("p_name" to n)).jsonPrimitive.content; loadLists(); onOk(id) }
            catch (e: Exception) { Game.toast(err(e)) }
        }
    }

    fun joinGroup(id: String, onOk: (String) -> Unit) {
        Game.scope.launch {
            try { syncProfile(); val n = Supa.rpc("cf_group_join", args("p_gid" to id)).jsonPrimitive.content; loadLists(); onOk(n) }
            catch (e: Exception) { Game.toast(L("Pozvánka do skupiny už neplatí.", "The group invite is no longer valid.", "Die Gruppeneinladung ist nicht mehr gültig.")) }
        }
    }

    fun leaveGroup(id: String) = Game.scope.launch { runCatching { Supa.rpc("cf_group_leave", args("p_gid" to id)) }; loadLists() }

    // ---------------- invites ----------------

    const val WEB = "https://onoseto.com/cf/"
    fun tableLink(c: String) = "${WEB}?t=$c"
    fun groupLink(id: String) = "${WEB}?g=$id"

    fun inviteText(): String {
        val c = code ?: return ""
        return L("Pojď hrát poker! Stůl ${if (name.isNotBlank()) "„$name“ " else ""}v Chips&Fish: ${tableLink(c)}  (kód stolu: $c)",
            "Let's play poker! Table ${if (name.isNotBlank()) "\"$name\" " else ""}in Chips&Fish: ${tableLink(c)}  (table code: $c)",
            "Lass uns Poker spielen! Tisch ${if (name.isNotBlank()) "„$name“ " else ""}in Chips&Fish: ${tableLink(c)}  (Tischcode: $c)")
    }

    fun groupInviteText(g: OGroup) = L("Přidej se do pokerové skupiny „${g.name}“ v Chips&Fish: ${groupLink(g.id)}",
        "Join the poker group \"${g.name}\" in Chips&Fish: ${groupLink(g.id)}",
        "Tritt der Pokergruppe „${g.name}“ in Chips&Fish bei: ${groupLink(g.id)}")

    @Suppress("unused") private fun unused() = max(0, 0)
}

