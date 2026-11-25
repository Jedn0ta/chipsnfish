package com.onoseto.chipsnfish.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.game.Player
import com.onoseto.chipsnfish.online.Fame
import com.onoseto.chipsnfish.online.Online
import com.onoseto.chipsnfish.online.Social

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FameSheet() {
    watch()
    LaunchedEffect(Fame.period, Fame.scope) { Fame.load() }
    LaunchedEffect(Unit) { Social.loadBank() }
    SheetHead("🏆 " + L("Síň slávy", "Hall of fame", "Ruhmeshalle")) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("week" to L("Tento týden", "This week", "Diese Woche"), "month" to L("Tento měsíc", "This month", "Dieser Monat"), "all" to L("Celkově", "All time", "Gesamt"))
                .forEach { (k, l) -> SmallPill(l, Fame.period == k) { Fame.period = k } }
        }
    }
    Row(Modifier.fillMaxWidth().height(268.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // leaderboard
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SmallPill(L("Všichni", "Everyone", "Alle"), Fame.scope == "all") { Fame.scope = "all" }
                SmallPill(L("Jen přátelé", "Friends only", "Nur Freunde"), Fame.scope == "friends") { Fame.scope = "friends" }
                Spacer(Modifier.weight(1f))
                Fame.champ?.let { (n, p) ->
                    T("👑 " + (if (Fame.period == "week") L("Minulý týden: ", "Last week: ", "Letzte Woche: ") else L("Minulý měsíc: ", "Last month: ", "Letzter Monat: ")) + "$n · $p ⭐",
                        11.5.sp, C.gold, FontWeight.Bold, maxLines = 1)
                }
            }
            val top = Fame.top
            Column(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(C.panel2.copy(alpha = 0.6f))
                    .verticalScroll(rememberScrollState()).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                when {
                    top == null -> T(L("Načítám…", "Loading…", "Lade…"), 13.sp, C.muted, modifier = Modifier.padding(8.dp))
                    top.isEmpty() -> T(
                        if (Fame.failed) L("Síň slávy se nepodařilo načíst. Je potřeba internet (a aktuální SQL na serveru).", "Couldn't load the hall of fame. Needs internet (and the current SQL on the server).", "Ruhmeshalle konnte nicht geladen werden. Internet nötig (und aktuelles SQL auf dem Server).")
                        else L("Tady zatím nikdo nemá body cti. Zahraj si online s přáteli a buď první! ⭐", "No honor points here yet. Play online with friends and be the first! ⭐", "Hier hat noch niemand Ehrenpunkte. Spiel online mit Freunden und sei der Erste! ⭐"),
                        13.sp, C.muted, modifier = Modifier.padding(10.dp))
                    else -> top.forEachIndexed { i, e -> FameRow(i + 1, e) }
                }
            }
        }
        // me + trophy cabinet
        Column(Modifier.width(262.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val me = Game.P.getValue("hero")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.gold.copy(alpha = 0.1f)).border(1.dp, C.gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Avatar(me, 34.dp, border = 2.dp)
                Column(Modifier.weight(1f)) {
                    T(me.name, 14.sp, C.ink, FontWeight.ExtraBold, maxLines = 1)
                    T(
                        "⭐ ${Game.fm(Fame.myPts)}" + (Fame.myRank?.let { "  ·  #$it" } ?: "") + "   " + L("celkem", "total", "gesamt") + " ${Game.fm(Fame.honor ?: 0)}",
                        12.sp, C.gold, FontWeight.Bold, maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    T("💰 " + (Social.bank?.let { Game.fm(it) } ?: "…"), 13.sp, C.ink, num = true)
                    T(L("peněženka", "wallet", "Geldbörse"), 9.5.sp, C.muted)
                }
            }
            T(L("VITRÍNA S POHÁRY", "TROPHY CABINET", "POKALVITRINE"), 11.sp, C.muted, FontWeight.ExtraBold, letter = 1.sp)
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(C.panel2.copy(alpha = 0.6f)).padding(6.dp)) {
                if (Fame.cupList.isEmpty()) {
                    T(L("Poháry jsou speciální event: turnaj o pohár si hráči u online stolu odhlasují před startem (aspoň 3 hráči). Kdo zůstane poslední, bere 🥉 (3 hráči), 🥈 (4–6) nebo 🏆 (7+).",
                        "Cups are a special event: players at an online table vote for a cup tournament before the start (3+ players). The last one standing wins 🥉 (3), 🥈 (4–6) or 🏆 (7+).",
                        "Pokale sind ein Sonder-Event: Die Spieler am Online-Tisch stimmen vor dem Start für ein Pokalturnier (ab 3). Der Letzte gewinnt 🥉 (3), 🥈 (4–6) oder 🏆 (7+)."),
                        11.5.sp, C.muted, modifier = Modifier.padding(4.dp))
                } else FlowRow(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Fame.cupList.forEach { c ->
                        Column(
                            Modifier.width(80.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f)).padding(vertical = 3.dp, horizontal = 2.dp)
                                .tap { Game.toast(Fame.cupName(c.tier) + (if (c.name.isNotBlank()) " · ${c.name}" else "") + " · " + L("${c.players} hráčů", "${c.players} players", "${c.players} Spieler") + " · " + java.text.DateFormat.getDateInstance().format(java.util.Date(c.t))) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            T(Fame.cupIcon(c.tier), 24.sp, lineHeight = 28.sp)
                            T(c.name.ifBlank { Fame.cupName(c.tier) }, 9.5.sp, C.ink, FontWeight.Bold, maxLines = 1, align = TextAlign.Center, lineHeight = 11.sp)
                            T(L("${c.players} hráčů", "${c.players} players", "${c.players} Spieler"), 9.sp, C.muted, maxLines = 1, lineHeight = 11.sp)
                        }
                    }
                }
            }
            T(L("⭐ Body cti jen za hry s přáteli: +1 za vyhranou handu, 10 za každého, kdo vypadl po tobě, vítěz +10 navíc. 💰 Žetony na drinky vyděláš proti počítači.",
                "⭐ Honor points only from games with friends: +1 per pot won, 10 for everyone who busted after you, +10 for the winner. 💰 Drink chips are earned vs the computer.",
                "⭐ Ehrenpunkte nur aus Spielen mit Freunden: +1 pro gewonnenen Pot, 10 für jeden, der nach dir ausschied, +10 für den Sieger. 💰 Drink-Chips verdienst du gegen den Computer."),
                9.5.sp, C.muted, lineHeight = 12.sp, maxLines = 3)
        }
    }
}

@Composable
private fun FameRow(rank: Int, e: Fame.Entry) {
    watch()
    val mine = e.uid == Online.me
    val p = Game.P[e.uid]?.let { if (e.img.isNotEmpty()) it.copy(img = e.img) else it } ?: Player(e.uid, e.name, img = e.img)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (mine) C.gold.copy(alpha = 0.16f) else if (rank <= 3) Color.White.copy(alpha = 0.05f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            when (rank) { 1 -> T("🥇", 18.sp); 2 -> T("🥈", 18.sp); 3 -> T("🥉", 18.sp); else -> T("$rank.", 13.sp, C.muted, num = true) }
        }
        Avatar(p, 28.dp, border = 2.dp)
        T(if (mine) e.name + " (" + L("ty", "you", "du") + ")" else e.name, 14.sp, if (mine) C.gold else C.ink, FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        if (e.wins > 0) T("🏆 ${e.wins}", 12.sp, C.ink, num = true)
        T(L("${e.hands} hand", "${e.hands} pots", "${e.hands} Pots"), 11.sp, C.muted)
        Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) { T("${Game.fm(e.pts)} ⭐", 15.sp, C.gold, num = true) }
    }
}

/** Small chip on the start screen: wallet and honor. */
@Composable
fun WalletBadge() {
    watch()
    if (Social.bank == null && Fame.honor == null) return
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(C.panel2.copy(alpha = 0.85f)).border(1.dp, C.line, RoundedCornerShape(99.dp))
            .tap { com.onoseto.chipsnfish.ui.Ui.open("fame") }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("💰 " + (Social.bank?.let { Game.fm(it) } ?: "–"), 14.sp, C.ink, num = true)
        T("⭐ " + Game.fm(Fame.honor ?: 0), 14.sp, C.gold, num = true)
        if ((Fame.cups ?: 0) > 0) T("🏆 ${Fame.cups}", 14.sp, C.ink, num = true)
    }
}

// ---------------- cup tournament: proposal, vote, banner ----------------

/** Sheet to propose a cup tournament at the online table. */
@Composable
fun CupSheet() {
    watch()
    val O = Online
    var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(O.name.ifBlank { L("Turnaj o pohár", "Cup tournament", "Pokalturnier") }) }
    val n = O.players.count { !it.left }
    val tier = Fame.tierFor(n)
    SheetHead("🏆 " + L("Turnaj o pohár", "Cup tournament", "Pokalturnier"))
    Note(L("Speciální event: hráči u stolu před startem hlasují. Projde, když je pro víc než polovina (u stolu musí být aspoň 3). Kdo zůstane poslední, získá pohár do vitríny.",
        "A special event: the players vote before the start. It passes when more than half agree (at least 3 at the table). The last one standing wins a cup for the cabinet.",
        "Ein Sonder-Event: Die Spieler stimmen vor dem Start ab. Angenommen bei mehr als der Hälfte (mindestens 3 am Tisch). Der Letzte am Tisch gewinnt einen Pokal."))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        T(L("Název", "Name", "Name"), 13.sp, C.muted, FontWeight.Bold)
        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
            androidx.compose.foundation.text.BasicTextField(
                name, { name = it.take(40) }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.gold), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        T(Fame.cupIcon(tier), 30.sp)
        T(L("Teď u stolu: $n hráčů → ", "At the table now: $n players → ", "Jetzt am Tisch: $n Spieler → ") + Fame.cupName(tier) +
            L("  (3 🥉 · 4–6 🥈 · 7+ 🏆)", "  (3 🥉 · 4–6 🥈 · 7+ 🏆)", "  (3 🥉 · 4–6 🥈 · 7+ 🏆)"), 13.sp, C.ink, FontWeight.Bold, modifier = Modifier.weight(1f))
    }
    Btn(L("Spustit hlasování", "Start the vote", "Abstimmung starten"), BK.GOLD, Modifier.fillMaxWidth(), enabled = n >= 3 && name.isNotBlank()) {
        O.proposeCup(name.trim()); Ui.close()
    }
    if (n < 3) T(L("Počkej, až budou u stolu aspoň 3 hráči.", "Wait until at least 3 players are at the table.", "Warte, bis mindestens 3 Spieler am Tisch sind."), 12.sp, C.lose, FontWeight.Bold)
}

/** In the lobby: the running vote or the approved event. */
@Composable
fun CupBanner() {
    watch()
    val O = Online
    val v = O.cupVote
    if (O.cup) {
        val tier = Fame.tierFor(O.players.count { !it.left })
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).background(C.gold.copy(alpha = 0.18f)).border(1.dp, C.gold, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T("🏆 " + L("TURNAJ O POHÁR", "CUP TOURNAMENT", "POKALTURNIER"), 11.sp, C.gold, FontWeight.ExtraBold, letter = 1.5.sp)
            T(O.cupName, 16.sp, C.ink, FontWeight.ExtraBold, maxLines = 1)
            T(L("Vítěz získá ", "The winner gets ", "Der Sieger bekommt ") + Fame.cupIcon(tier) + " " + Fame.cupName(tier), 11.5.sp, Color.White.copy(alpha = 0.8f))
        }
        return
    }
    if (v == null) return
    val act = O.players.count { !it.left }
    val yes = O.cupVotes.count { it.value }; val no = O.cupVotes.count { !it.value }
    val need = act / 2 + 1
    val my = O.cupVotes[O.me]
    val by = O.players.firstOrNull { it.uid == v.second }?.name ?: "?"
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, C.gold.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        T("🏆 " + L("$by navrhuje turnaj o pohár", "$by proposes a cup tournament", "$by schlägt ein Pokalturnier vor"), 12.sp, C.gold, FontWeight.ExtraBold, maxLines = 1)
        T("„${v.first}“", 15.sp, C.ink, FontWeight.ExtraBold, maxLines = 1)
        T(L("Pro $yes · proti $no · potřeba $need z $act", "Yes $yes · no $no · needs $need of $act", "Ja $yes · nein $no · nötig $need von $act"), 11.5.sp, Color.White.copy(alpha = 0.8f))
        if (my == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("👍 " + L("Pro", "Yes", "Ja"), BK.CALL, Modifier.width(96.dp), height = 32.dp, enabled = !O.busy) { O.voteCup(true) }
            Btn("👎 " + L("Proti", "No", "Nein"), BK.DARK, Modifier.width(96.dp), height = 32.dp, enabled = !O.busy) { O.voteCup(false) }
        } else T(if (my) L("Hlasoval jsi pro 👍", "You voted yes 👍", "Du hast mit Ja gestimmt 👍") else L("Hlasoval jsi proti 👎", "You voted no 👎", "Du hast mit Nein gestimmt 👎"), 11.sp, C.muted)
    }
}
