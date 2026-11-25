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
                    T(L("Pohár vyhraješ, když u online stolu s přáteli zůstaneš poslední. 2–3 hráči 🥉, 4–6 🥈, 7 a víc 🏆. Pojmenuj stůl a hraj turnaj o vlastní pohár!",
                        "Win a cup by being the last one standing at an online table. 2–3 players 🥉, 4–6 🥈, 7+ 🏆. Name the table and play for your own cup!",
                        "Einen Pokal gewinnst du als Letzter am Online-Tisch. 2–3 Spieler 🥉, 4–6 🥈, ab 7 🏆. Benenne den Tisch und spiele um deinen eigenen Pokal!"),
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
