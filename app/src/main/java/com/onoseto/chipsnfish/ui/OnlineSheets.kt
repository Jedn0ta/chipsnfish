@file:OptIn(ExperimentalLayoutApi::class)

package com.onoseto.chipsnfish.ui

import com.onoseto.chipsnfish.online.OFriend
import androidx.compose.ui.text.style.TextAlign
import com.onoseto.chipsnfish.game.Reads
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
    var adv by remember { mutableStateOf(c.advisor) }
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
            Spacer(Modifier.height(4.dp))
            Btn(L("▶ Založit stůl", "▶ Create table", "▶ Tisch erstellen"), modifier = Modifier.fillMaxWidth(), enabled = !O.busy, height = 46.dp) {
                Game.gameCfg = Game.gameCfg.copy(stack = stack, level = level, every = every, advisor = adv); Game.saveCfg()
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
    SheetHead(L("Přátelé", "Friends", "Freunde"))
    if (!SupaCfg.configured) { NotConfigured(); return }
    LaunchedEffect(Unit) { O.loadFriends() }
    FriendsTab()
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
        Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 20.dp, start = 40.dp, end = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(L("Zatím nemáš žádné přátele", "No friends yet", "Noch keine Freunde"), 20.sp, Color.White, FontWeight.ExtraBold, align = TextAlign.Center)
            T(L("Pošli kamarádovi svůj kód přítele, nebo zadej jeho kód nahoře.", "Send a friend your friend code, or enter theirs above.", "Schick einem Freund deinen Freundescode oder gib seinen oben ein."), 14.sp, Color.White.copy(alpha = 0.75f), align = TextAlign.Center)
            T(L("Přidat jde i u stolu: klepni na hráče a dej ★ Přidat do přátel.", "At a table: tap a player and choose ★ Add friend.", "Am Tisch: Spieler antippen und ★ Zu Freunden wählen."), 12.5.sp, GlassMuted, align = TextAlign.Center)
        }
        return
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fs.sortedWith(compareByDescending<OFriend> { it.table != null }.thenByDescending { it.online }.thenBy { it.name.lowercase() }).forEach { f -> FriendMedal(f) }
    }
}

/** A friend as a round medallion: their photo, a status ring and the name. Tap = glass window with actions. */
@Composable
private fun FriendMedal(f: OFriend) {
    watch()
    val ring = when { f.table != null -> LobbyBlue; f.online -> C.win; else -> Color.White.copy(alpha = 0.25f) }
    Column(Modifier.width(86.dp).tap { Ui.open("friend", f) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier.size(66.dp).glass(androidx.compose.foundation.shape.CircleShape, if (f.online) Color(0x3326D07C) else Color(0x2A2A5CA8), lift = 8.dp).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(Player(f.uid, f.name, img = f.img, hat = Game.P[f.uid]?.hat ?: ""), 56.dp, ring = ring, border = 2.5.dp, dim = !f.online && f.table == null) {
                Box(Modifier.align(Alignment.BottomEnd).size(13.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (f.table != null) Color(0xFF2F7BFF) else if (f.online) C.win else Color(0xFF55646E)).border(2.dp, Color(0xFF0E1A33), androidx.compose.foundation.shape.CircleShape))
            }
        }
        T(f.name, 12.5.sp, Color.White, FontWeight.ExtraBold, maxLines = 1, fit = true, align = TextAlign.Center)
        T(when { f.table != null -> L("hraje", "playing", "spielt"); f.online -> "online"; else -> "offline" }, 10.sp,
            if (f.table != null) LobbyBlue else if (f.online) C.win else GlassMuted, FontWeight.Bold, maxLines = 1)
    }
}

/** Glass window with what you can do with a friend. */
@Composable
fun FriendSheet(f: OFriend) {
    watch()
    val O = Online
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(96.dp).glass(androidx.compose.foundation.shape.CircleShape, lift = 10.dp).padding(5.dp), contentAlignment = Alignment.Center) {
            Avatar(Player(f.uid, f.name, img = f.img, hat = Game.P[f.uid]?.hat ?: ""), 84.dp,
                ring = if (f.table != null) LobbyBlue else if (f.online) C.win else Color.White.copy(alpha = 0.3f), border = 3.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(L("PŘÍTEL", "FRIEND", "FREUND"), 10.sp, LobbyBlue, FontWeight.ExtraBold, letter = 2.sp)
            T(f.name, 24.sp, Color.White, FontWeight.ExtraBold, maxLines = 1, fit = true)
            T(when {
                f.table != null -> L("Hraje u stolu ${f.table}", "Playing at table ${f.table}", "Spielt an Tisch ${f.table}")
                f.online -> L("Je teď v aplikaci", "In the app right now", "Gerade in der App")
                else -> L("Teď není připojený", "Not connected right now", "Gerade nicht verbunden")
            }, 13.sp, if (f.table != null) LobbyBlue else if (f.online) C.win else GlassMuted, FontWeight.Bold)
            Game.P[f.uid]?.takeIf { it.hands > 0 }?.let { p ->
                T(L("${p.hands} společných hand · ${Reads.PROF.getValue(Reads.effType(p)).s}", "${p.hands} hands together · ${Reads.PROF.getValue(Reads.effType(p)).s}", "${p.hands} gemeinsame Hände · ${Reads.PROF.getValue(Reads.effType(p)).s}"), 12.sp, GlassMuted)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (f.table != null && f.table != O.code) Btn(L("Přisednout ke stolu", "Join their table", "An den Tisch"), BK.GOLD, Modifier.weight(1f), height = 42.dp) { Ui.close(); O.join(f.table) { goTable() } }
        if (O.code != null && f.table != O.code) Btn(L("Pozvat ke stolu ${O.code}", "Invite to table ${O.code}", "An Tisch ${O.code} einladen"), BK.GOLD, Modifier.weight(1f), height = 42.dp) { O.inviteFriend(f); Ui.close() }
        Btn("⚔ " + L("Vyzvat na souboj", "Challenge", "Herausfordern"), BK.RAISE, Modifier.weight(1f), height = 42.dp, enabled = !O.busy) {
            Ui.close(); O.challenge(f) { goTable() }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(L("Statistiky", "Statistics", "Statistiken"), BK.GHOST, Modifier.weight(1f), height = 38.dp) {
            if (Game.P[f.uid] == null) Game.P[f.uid] = Player(f.uid, f.name, img = f.img, stored = true)
            Ui.open("player", PlayerArg(f.uid))
        }
        Btn(L("Zpět", "Back", "Zurück"), BK.GHOST, Modifier.weight(1f), height = 38.dp) { Ui.open("groups") }
        Btn(L("Odebrat", "Remove", "Entfernen"), BK.DANGER, Modifier.weight(1f), height = 38.dp) {
            Ui.confirm(L("Odebrat z přátel?", "Remove friend?", "Freund entfernen?"), f.name, L("Odebrat", "Remove", "Entfernen")) { O.removeFriend(f.uid); Ui.open("groups") }
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
