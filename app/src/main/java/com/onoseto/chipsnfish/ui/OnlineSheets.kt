@file:OptIn(ExperimentalLayoutApi::class)

package com.onoseto.chipsnfish.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Cards
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.game.Player
import com.onoseto.chipsnfish.online.OGroup
import com.onoseto.chipsnfish.online.Online
import com.onoseto.chipsnfish.online.SupaCfg
import com.onoseto.chipsnfish.tr

class OnlineArg(var gid: String? = null, var tab: String = "new")
class GroupArg(val id: String)

@Composable
private fun NotConfigured() {
    BoxPanel {
        T(L("Online server zatím není připojený", "The online server isn't connected yet", "Der Online-Server ist noch nicht verbunden"), 16.sp, C.gold, FontWeight.ExtraBold)
        Note(L("Aplikace ještě nemá adresu serveru Supabase. Jakmile ji doplníme, půjde zakládat stoly a zvát přátele.",
            "The app doesn't have the Supabase server address yet. Once it's added, you can create tables and invite friends.",
            "Die App kennt die Supabase-Serveradresse noch nicht. Sobald sie ergänzt ist, kannst du Tische erstellen und Freunde einladen."))
    }
}

fun goTable() {
    if (Online.code != null && !Game.online()) Online.resume()
    Ui.sheet = null; Ui.menu = false; Ui.solo = false; Ui.goView("table")
}

@Composable
fun ProfileRow() {
    val me = Game.P.getValue("hero")
    var name by remember { mutableStateOf(if (me.name == "Ty") "" else me.name) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Avatar(me, 44.dp)
        Column(Modifier.weight(1f)) {
            Label(L("Tvoje jméno u stolu", "Your name at the table", "Dein Name am Tisch"))
            Field(name, { name = it.take(24); me.name = it.trim().ifEmpty { "Ty" }; Game.saveLocal() }, Modifier.fillMaxWidth(), placeholder = L("Např. Dan", "e.g. Dan", "z. B. Dan"), big = true)
        }
    }
}

@Composable
fun OnlineSheet(a: OnlineArg) {
    val O = Online
    SheetHead(L("Hrát s přáteli", "Play with friends", "Mit Freunden spielen")) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("new" to L("Nový stůl", "New table", "Neuer Tisch"), "join" to L("Připojit", "Join", "Beitreten")).forEach { (k, l) ->
                val on = a.tab == k
                Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) C.ink else Color.Transparent).border(1.dp, if (on) C.ink else C.line, RoundedCornerShape(99.dp)).tap { a.tab = k; Ui.redraw() }.padding(horizontal = 12.dp, vertical = 5.dp)) {
                    T(l, 13.sp, if (on) C.room else C.muted, FontWeight.Bold)
                }
            }
        }
    }
    if (!SupaCfg.configured) { NotConfigured(); return }
    LaunchedEffect(Unit) { O.loadLists() }
    ProfileRow()
    if (O.code != null) {
        Btn(L("Zpět ke stolu ${O.code}", "Back to table ${O.code}", "Zurück zu Tisch ${O.code}"), BK.CALL, Modifier.fillMaxWidth()) { goTable() }
    }
    if (a.tab == "join") {
        var code by remember { mutableStateOf("") }
        Label(L("Kód stolu", "Table code", "Tischcode"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, Modifier.weight(1f), placeholder = "ABC123", big = true)
            Btn(L("Připojit", "Join", "Beitreten"), height = 46.dp, enabled = !O.busy) { O.join(code) { goTable() } }
        }
        Note(L("Kód stolu najdeš v pozvánce. Když máš odkaz, stačí na něj klepnout.", "You'll find the table code in the invite. If you have the link, just tap it.", "Den Tischcode findest du in der Einladung. Mit dem Link reicht ein Tippen."))
        if (O.myTables.isNotEmpty()) {
            Label(L("Moje rozehrané stoly", "My running tables", "Meine laufenden Tische"))
            O.myTables.forEach { t ->
                TableRow(t.code, t.name, t.players, t.status) { O.join(t.code) { goTable() } }
            }
        }
        return
    }
    var tname by remember { mutableStateOf("") }
    val c = Game.gameCfg
    var stack by remember { mutableStateOf(c.stack) }
    var level by remember { mutableStateOf(c.level) }
    var every by remember { mutableStateOf(c.every) }
    var adv by remember { mutableStateOf(true) }
    var gid by remember { mutableStateOf(a.gid) }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(L("Název stolu (nepovinné)", "Table name (optional)", "Tischname (optional)"))
            Field(tname, { tname = it.take(40) }, Modifier.fillMaxWidth(), placeholder = L("Páteční poker", "Friday poker", "Freitagspoker"))
            Label(tr("Startovní žetony"))
            Picks(listOf("500" to "500", "1000" to Game.fm(1000), "2000" to Game.fm(2000), "5000" to Game.fm(5000)), stack.toString()) { stack = it.toInt() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(adv, { adv = it }, colors = SwitchDefaults.colors(checkedThumbColor = C.goldInk, checkedTrackColor = C.gold))
                Column {
                    T(L("Povolit rádce (žárovka)", "Allow the coach (bulb)", "Berater erlauben (Glühbirne)"), 14.sp, weight = FontWeight.Bold)
                    T(L("Šance a statistiky jen z veřejných informací", "Odds and stats from public information only", "Chancen und Statistiken nur aus öffentlichen Infos"), 11.5.sp, C.muted)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(tr("Startovní blindy"))
            Picks((0 until 7).map { it.toString() to Cards.lvlStr(it) }, level.toString()) { level = it.toInt() }
            Label(tr("Zvyšování blindů"))
            Picks(listOf("0", "5", "10", "15", "20").map { it to if (it == "0") tr("Nikdy") else L("$it hand", "$it hands", "$it Hände") }, every.toString()) { every = it.toInt() }
            val gs = O.groups.orEmpty()
            if (gs.isNotEmpty()) {
                Label(L("Skupina", "Group", "Gruppe"))
                Picks(listOf("" to L("Bez skupiny", "No group", "Keine Gruppe")) + gs.map { it.id to it.name }, gid ?: "") { gid = it.ifEmpty { null } }
            }
        }
    }
    Btn(L("▶ Založit stůl", "▶ Create table", "▶ Tisch erstellen"), modifier = Modifier.fillMaxWidth(), enabled = !O.busy) {
        Game.gameCfg = Game.gameCfg.copy(stack = stack, level = level, every = every); Game.saveCfg()
        O.create(tname, stack, level, every, adv, gid) { goTable() }
    }
}

@Composable
private fun TableRow(code: String, name: String, players: Int, status: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.panel2).tap(onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T(code, 18.sp, C.gold, num = true, letter = 2.sp)
        T(name.ifBlank { L("Stůl", "Table", "Tisch") }, 14.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        T(L("$players hráčů", "$players players", "$players Spieler") + " · " + when (status) { "play" -> L("hraje se", "playing", "läuft"); else -> L("čeká", "waiting", "wartet") }, 12.sp, C.muted)
        T("›", 18.sp, C.gold, num = true)
    }
}

@Composable
fun GroupsSheet() {
    val O = Online
    SheetHead(L("Skupiny", "Groups", "Gruppen"))
    if (!SupaCfg.configured) { NotConfigured(); return }
    LaunchedEffect(Unit) { O.loadLists() }
    Note(L("Skupina je parta, se kterou hraješ pravidelně. Pošli členům pozvánku a pak ve skupině uvidí každý rozehraný stůl.",
        "A group is the crew you play with regularly. Send members an invite, then everyone sees the running tables in the group.",
        "Eine Gruppe ist die Runde, mit der du regelmäßig spielst. Schick eine Einladung, dann sehen alle die laufenden Tische der Gruppe."))
    val gs = O.groups
    if (gs == null) Note(L("Načítám…", "Loading…", "Lade…"))
    else if (gs.isEmpty()) Note("<b>" + L("Zatím nejsi v žádné skupině.", "You're not in any group yet.", "Du bist noch in keiner Gruppe.") + "</b>")
    gs?.forEach { g ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(12.dp)).tap { Ui.open("group", GroupArg(g.id), Ui.sheet) }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                T(g.name, 16.sp, weight = FontWeight.ExtraBold)
                T(L("${g.members.size} členů", "${g.members.size} members", "${g.members.size} Mitglieder") + if (g.tables.isNotEmpty()) " · " + L("${g.tables.size} stůl", "${g.tables.size} table(s)", "${g.tables.size} Tisch(e)") else "", 12.sp, if (g.tables.isNotEmpty()) C.win else C.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { g.members.take(5).forEach { m -> Avatar(Player(m.uid, m.name, img = m.img ?: ""), 28.dp, ring = C.panel2, border = 2.dp) } }
            T("›", 18.sp, C.gold, num = true)
        }
    }
    var nm by remember { mutableStateOf("") }
    Label(L("Nová skupina", "New group", "Neue Gruppe"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(nm, { nm = it.take(40) }, Modifier.weight(1f), placeholder = L("Např. Páteční poker", "e.g. Friday poker", "z. B. Freitagspoker"))
        Btn(L("Vytvořit", "Create", "Erstellen"), height = 44.dp) {
            O.createGroup(nm.ifBlank { "Poker" }) { id -> nm = ""; Ui.open("group", GroupArg(id), Ui.sheet) }
        }
    }
}

@Composable
fun GroupSheet(a: GroupArg) {
    val O = Online
    val ctx = LocalContext.current
    val g: OGroup? = O.groups?.find { it.id == a.id }
    LaunchedEffect(a.id) { O.loadLists() }
    SheetHead(g?.name ?: L("Skupina", "Group", "Gruppe"))
    if (g == null) { Note(L("Načítám…", "Loading…", "Lade…")); return }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(L("Pozvat do skupiny", "Invite to group", "In Gruppe einladen"), BK.CALL, Modifier.weight(1f)) { share(ctx, O.groupInviteText(g)) }
        Btn(L("Nový stůl ve skupině", "New table in group", "Neuer Tisch in Gruppe"), modifier = Modifier.weight(1f)) { Ui.open("online", OnlineArg(gid = g.id)) }
    }
    Label(L("Rozehrané stoly", "Running tables", "Laufende Tische"))
    if (g.tables.isEmpty()) Note(L("Teď se ve skupině nehraje. Založ stůl a ostatní ho tu uvidí.", "Nobody's playing in the group right now. Create a table and the others will see it here.", "Gerade wird nicht gespielt. Erstelle einen Tisch, die anderen sehen ihn hier."))
    g.tables.forEach { t -> TableRow(t.code, t.name, t.players, t.status) { O.join(t.code) { goTable() } } }
    Label(L("Členové", "Members", "Mitglieder") + " · ${g.members.size}")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        g.members.forEach { m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                Avatar(Player(m.uid, m.name, img = m.img ?: ""), 44.dp, ring = if (m.uid == g.owner) C.gold else C.line)
                T(m.name, 11.sp, maxLines = 1)
            }
        }
    }
    T(L("Opustit skupinu", "Leave group", "Gruppe verlassen"), 12.5.sp, C.muted, modifier = Modifier.tap {
        Ui.confirm(L("Opustit skupinu?", "Leave the group?", "Gruppe verlassen?"), L("Do skupiny se vrátíš jen novou pozvánkou.", "You can only come back with a new invite.", "Zurück nur mit neuer Einladung."), L("Opustit", "Leave", "Verlassen")) {
            O.leaveGroup(g.id); Ui.open("groups")
        }
    }.padding(vertical = 4.dp))
}

/** Settings → game tab while sitting at an online table. */
@Composable
fun OnlineSettingsGame() {
    val O = Online
    val st = Game.st
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            L("Stůl", "Table", "Tisch") to (O.code ?: ""),
            tr("Blindy teď") to Cards.lvlStr(st.curLevel),
            tr("Start žetony") to Game.fm(st.startStack),
            tr("Zvyšování blindů") to if (st.blindHands > 0) L("každých ${st.blindHands} hand", "every ${st.blindHands} hands", "alle ${st.blindHands} Hände") else tr("nikdy"),
        ).forEach { (l, v) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(C.panel2).padding(horizontal = 10.dp, vertical = 8.dp)) {
                T(l.uppercase(), 10.5.sp, C.muted, FontWeight.ExtraBold, maxLines = 1); T(v, 18.sp, num = true, maxLines = 1)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(O.advisor, { if (O.isHost) O.changeAdvisor(it) }, enabled = O.isHost, colors = SwitchDefaults.colors(checkedThumbColor = C.goldInk, checkedTrackColor = C.gold))
        Column {
            T(L("Rádce u stolu povolen", "Coach allowed at this table", "Berater an diesem Tisch erlaubt"), 14.sp, weight = FontWeight.Bold)
            T(if (O.isHost) L("Platí pro všechny hráče u stolu.", "Applies to every player at the table.", "Gilt für alle Spieler am Tisch.") else L("Může změnit jen zakladatel stolu.", "Only the host can change this.", "Nur der Gastgeber kann das ändern."), 11.5.sp, C.muted)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(L("Pozvat přátele", "Invite friends", "Freunde einladen"), BK.CALL, Modifier.weight(1f)) { share(ctx, O.inviteText()) }
        Btn(tr("♠ Výherní kombinace"), BK.GHOST, Modifier.weight(1f)) { Ui.open("hands", null, Ui.sheet) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(tr("☰ Hlavní menu"), BK.GHOST, Modifier.weight(1f)) { Ui.close(); Ui.showMenu() }
        Btn(L("Opustit stůl", "Leave table", "Tisch verlassen"), BK.DANGER, Modifier.weight(1f)) {
            Ui.confirm(L("Opustit stůl?", "Leave the table?", "Tisch verlassen?"), L("Pokud právě hraješ, tvoje karty se zahodí.", "If you're in a hand, your cards are folded.", "Wenn du in einer Hand bist, wird gefoldet."), L("Opustit", "Leave", "Verlassen")) {
                O.leave { Ui.showMenu() }
            }
        }
    }
}
