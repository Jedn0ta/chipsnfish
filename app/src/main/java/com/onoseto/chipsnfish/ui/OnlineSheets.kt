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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
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
    watch()
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
    watch()
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
    watch()
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
    if (Game.P.getValue("hero").name == "Ty") ProfileRow()
    if (O.code != null) {
        Btn(L("Zpět ke stolu ${O.code}", "Back to table ${O.code}", "Zurück zu Tisch ${O.code}"), BK.CALL, Modifier.fillMaxWidth()) { goTable() }
    }
    NearbyList()
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
    var stack by remember { mutableStateOf(if (c.stack in listOf(100, 200, 300)) c.stack else 100) }
    var level by remember { mutableStateOf(c.level) }
    var every by remember { mutableStateOf(c.every) }
    var adv by remember { mutableStateOf(true) }
    var gid by remember { mutableStateOf(a.gid) }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(L("Název stolu (nepovinné)", "Table name (optional)", "Tischname (optional)"))
            Field(tname, { tname = it.take(40) }, Modifier.fillMaxWidth(), placeholder = L("Páteční poker", "Friday poker", "Freitagspoker"))
            Label(tr("Startovní žetony"))
            Picks(listOf("100" to "100", "200" to "200", "300" to "300"), stack.toString(), small = true) { stack = it.toInt() }
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
            Picks((0 until 7).map { it.toString() to Cards.lvlStr(it) }, level.toString(), small = true) { level = it.toInt() }
            Label(tr("Zvyšování blindů"))
            Picks(listOf("0", "5", "10", "15", "20").map { it to if (it == "0") tr("Nikdy") else L("$it hand", "$it hands", "$it Hände") }, every.toString(), small = true) { every = it.toInt() }
            val gs = O.groups.orEmpty()
            if (gs.isNotEmpty()) {
                Label(L("Skupina", "Group", "Gruppe"))
                Picks(listOf("" to L("Bez skupiny", "No group", "Keine Gruppe")) + gs.map { it.id to it.name }, gid ?: "", small = true) { gid = it.ifEmpty { null } }
            }
            Spacer(Modifier.height(4.dp))
            Btn(L("▶ Založit stůl", "▶ Create table", "▶ Tisch erstellen"), modifier = Modifier.fillMaxWidth(), enabled = !O.busy, height = 46.dp) {
                Game.gameCfg = Game.gameCfg.copy(stack = stack, level = level, every = every); Game.saveCfg()
                O.create(tname, stack, level, every, adv, gid) { goTable() }
            }
        }
    }
}

@Composable
fun NearbyList() {
    watch()
    val near = com.onoseto.chipsnfish.online.Nearby.tables.filter { it.code != Online.code }
    if (near.isEmpty()) return
    Label(L("Stoly v okolí · Wi-Fi", "Tables nearby · Wi-Fi", "Tische in der Nähe · WLAN"))
    near.forEach { t ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.win.copy(alpha = 0.12f)).border(1.dp, C.win.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .tap { Online.join(t.code) { goTable() } }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T("📶", 16.sp)
            T(t.who.ifBlank { L("Hráč", "Player", "Spieler") }, 15.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            T(t.code, 16.sp, C.gold, num = true, letter = 2.sp)
            T(L("Přisednout ›", "Join ›", "Beitreten ›"), 13.sp, C.win, FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun TableRow(code: String, name: String, players: Int, status: String, onClick: () -> Unit) {
    watch()
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
    watch()
    val O = Online
    var tab by remember { mutableStateOf("friends") }
    SheetHead(L("Přátelé a skupiny", "Friends & groups", "Freunde & Gruppen")) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("friends" to L("Přátelé", "Friends", "Freunde"), "groups" to L("Skupiny", "Groups", "Gruppen")).forEach { (k, l) ->
                val on = tab == k
                Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) C.ink else Color.Transparent).border(1.dp, if (on) C.ink else C.line, RoundedCornerShape(99.dp)).tap { tab = k }.padding(horizontal = 12.dp, vertical = 5.dp)) {
                    T(l, 13.sp, if (on) C.room else C.muted, FontWeight.Bold)
                }
            }
        }
    }
    if (!SupaCfg.configured) { NotConfigured(); return }
    LaunchedEffect(Unit) { O.loadLists(); O.loadFriends() }
    if (tab == "friends") { FriendsTab(); return }
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
    watch()
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
    watch()
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


/** My friend code + field to add a friend by code. */
@Composable
private fun AddFriendBar() {
    watch()
    val O = Online
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { O.loadFriendCode() }
    var c by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.gold.copy(alpha = 0.08f)).border(1.dp, C.gold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            T(L("MŮJ KÓD PŘÍTELE", "MY FRIEND CODE", "MEIN FREUNDESCODE"), 9.5.sp, C.muted, FontWeight.ExtraBold, letter = 1.sp)
            T(O.myFriendCode ?: "······", 19.sp, C.gold, num = true, letter = 2.sp)
        }
        Btn(L("Sdílet", "Share", "Teilen"), BK.DARK, Modifier.width(84.dp), enabled = O.myFriendCode != null, height = 34.dp) {
            O.myFriendCode?.let { share(ctx, O.friendInviteText(it)) }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(140.dp).clip(RoundedCornerShape(10.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (c.isEmpty()) T(L("kód přítele", "friend's code", "Code des Freundes"), 14.sp, C.muted, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                c, { c = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6) }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.gold), modifier = Modifier.fillMaxWidth(),
            )
        }
        Btn("★ " + L("Přidat", "Add", "Hinzufügen"), BK.GOLD, Modifier.width(100.dp), enabled = c.length == 6 && !O.busy, height = 34.dp) {
            O.addFriendByCode(c) { c = "" }
        }
    }
}

@Composable
private fun FriendsTab() {
    watch()
    val O = Online
    val fs = O.friends
    if (O.code != null) Note(L("Sedíš u stolu <b>${O.code}</b>. Klepni u přítele na <b>Pozvat</b> a jemu se v aplikaci ukáže pozvánka.",
        "You're at table <b>${O.code}</b>. Tap <b>Invite</b> next to a friend and they'll see the invite in the app.",
        "Du sitzt an Tisch <b>${O.code}</b>. Tippe bei einem Freund auf <b>Einladen</b>, er sieht die Einladung in der App."))
    AddFriendBar()
    if (fs == null) { Note(L("Načítám…", "Loading…", "Lade…")); return }
    if (fs.isEmpty()) {
        BoxPanel {
            T(L("Zatím nemáš žádné přátele", "No friends yet", "Noch keine Freunde"), 15.sp, weight = FontWeight.ExtraBold)
            Note(L("Pošli kamarádovi svůj <b>kód přítele</b> (Sdílet), nebo zadej jeho kód nahoře. Přidat jde i u stolu: klepni na hráče a dej <b>Přidat do přátel</b>. Pak ho odsud pozveš ke stolu.",
                "Send a friend your <b>friend code</b> (Share) or enter theirs above. You can also tap a player at the table and choose <b>Add to friends</b>. Then invite them to a table from here.",
                "Schick einem Freund deinen <b>Freundescode</b> (Teilen) oder gib seinen oben ein. Am Tisch geht es auch: Spieler antippen, <b>Zu Freunden</b>. Dann lädst du ihn von hier an einen Tisch ein."))
        }
        return
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    fs.forEach { f ->
        Column(
            Modifier.width(178.dp).glass(RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 6.dp), if (f.online) Color(0x3326D07C) else Color(0x2A2A5CA8), lift = 6.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(Player(f.uid, f.name, img = f.img, hat = Game.P[f.uid]?.hat ?: ""), 36.dp, ring = if (f.online) C.win else Color.White.copy(alpha = 0.3f), border = 2.dp) {
                    Box(Modifier.align(Alignment.BottomEnd).size(11.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (f.online) C.win else Color(0xFF55646E)).border(2.dp, Color(0xFF0E1A33), androidx.compose.foundation.shape.CircleShape))
                }
                Column(Modifier.weight(1f)) {
                    T(f.name, 14.sp, Color.White, FontWeight.ExtraBold, maxLines = 1, fit = true)
                    T(when {
                        f.table != null -> L("u stolu ${f.table}", "at table ${f.table}", "an Tisch ${f.table}")
                        f.online -> L("online", "online", "online")
                        else -> L("offline", "offline", "offline")
                    }, 11.sp, if (f.table != null || f.online) C.win else GlassMuted, FontWeight.Bold, maxLines = 1)
                }
                T("✕", 12.sp, GlassMuted, modifier = Modifier.tap {
                    Ui.confirm(L("Odebrat z přátel?", "Remove friend?", "Freund entfernen?"), f.name, L("Odebrat", "Remove", "Entfernen")) { O.removeFriend(f.uid); Ui.open("groups") }
                }.padding(4.dp))
            }
            if (f.table != null && f.table != O.code) Btn(L("Přisednout", "Join", "Beitreten"), BK.CALL, Modifier.fillMaxWidth(), height = 32.dp) { O.join(f.table) { goTable() } }
            else if (O.code != null && f.table != O.code) Btn(L("Pozvat ke stolu", "Invite", "Einladen"), BK.GOLD, Modifier.fillMaxWidth(), height = 32.dp) { O.inviteFriend(f) }
        }
    }
    }
}

/** Invites from friends, shown on top of the menu and the table. */
@Composable
fun InboxBanner(modifier: Modifier = Modifier) {
    watch()
    val inv = Online.inbox.firstOrNull { it.code != Online.code } ?: return
    Row(
        modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(14.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF223B2C), Color(0xFF15281D))))
            .border(1.dp, C.win.copy(alpha = 0.7f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("✉", 18.sp, C.win)
        T(L("${inv.from} tě zve ke stolu ${inv.code}", "${inv.from} invites you to table ${inv.code}", "${inv.from} lädt dich an Tisch ${inv.code} ein") + if (inv.name.isNotBlank()) " · ${inv.name}" else "", 14.sp, C.ink, FontWeight.Bold, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
        Btn(L("Přisednout", "Join", "Beitreten"), BK.CALL, height = 34.dp) { Online.dismissInvite(inv); Online.join(inv.code) { goTable() } }
        T("✕", 14.sp, C.muted, modifier = Modifier.tap { Online.dismissInvite(inv) }.padding(4.dp))
    }
}
