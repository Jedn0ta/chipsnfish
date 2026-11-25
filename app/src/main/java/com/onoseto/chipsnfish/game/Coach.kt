package com.onoseto.chipsnfish.game

import com.onoseto.chipsnfish.I18n
import com.onoseto.chipsnfish.L
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Beginner coach for the practice game: explains in plain words what is happening,
 * what the hero holds, what to do and why. Poker terms come with Czech pronunciation.
 * Lines may contain <b>…</b>.
 */
object Coach {
    class Msg(val title: String, val lines: List<String>, val act: String = "", val amt: Int = 0, val label: String = "")

    private val PRON = mapOf(
        "blind" to "blajnd", "small blind" to "smól blajnd", "big blind" to "big blajnd", "dealer" to "dýlr",
        "preflop" to "príflop", "flop" to "flop", "turn" to "törn", "river" to "rivr", "showdown" to "šoudaun",
        "pot" to "pot", "check" to "ček", "call" to "kól", "bet" to "bet", "raise" to "rejz", "fold" to "fould",
        "all-in" to "ól in", "outs" to "auts", "kicker" to "kikr",
    )

    /** "<b>call</b> [kól]" in Czech, plain bold term otherwise. */
    fun w(term: String): String = "<b>$term</b>" + if (I18n.lang == "cs") PRON[term.lowercase()]?.let { " [$it]" } ?: "" else ""

    private fun pct(x: Double) = "${(x * 100).roundToInt()} %"

    private fun catExplain(c: Int): String = when (c) {
        0 -> L("nic se nespojuje, rozhoduje nejvyšší karta", "nothing connects, the highest card decides", "nichts passt zusammen, die höchste Karte entscheidet")
        1 -> L("dvě karty stejné hodnoty", "two cards of the same rank", "zwei Karten gleichen Werts")
        2 -> L("dvě různé dvojice", "two different pairs", "zwei verschiedene Paare")
        3 -> L("tři karty stejné hodnoty", "three cards of the same rank", "drei Karten gleichen Werts")
        4 -> L("pět karet jdoucích po sobě", "five cards in a row", "fünf Karten in Folge")
        5 -> L("pět karet stejné barvy", "five cards of the same suit", "fünf Karten derselben Farbe")
        6 -> L("trojice a k tomu pár", "three of a kind plus a pair", "Drilling plus Paar")
        7 -> L("čtyři karty stejné hodnoty", "four cards of the same rank", "vier Karten gleichen Werts")
        else -> L("pět karet po sobě ve stejné barvě", "five in a row of the same suit", "fünf in Folge derselben Farbe")
    }

    private fun cardStr(c: Int) = Cards.rl(c shr 2) + Cards.SYM[c and 3]

    private fun streetIntro(s: Int): String = when (s) {
        0 -> L("Teď je ${w("preflop")}: každý má jen 2 vlastní karty, společné karty na stole ještě nejsou.",
            "This is the ${w("preflop")}: everyone has just 2 private cards, no shared cards yet.",
            "Das ist der ${w("preflop")}: jeder hat nur 2 eigene Karten, noch keine Gemeinschaftskarten.")
        1 -> L("${w("flop")}: na stůl přišly 3 společné karty. Ruku skládáš z nejlepších 5 karet ze svých 2 a společných.",
            "${w("flop")}: 3 shared cards are on the table. Your hand is the best 5 cards from your 2 and the shared ones.",
            "${w("flop")}: 3 Gemeinschaftskarten liegen offen. Deine Hand sind die besten 5 aus deinen 2 und den offenen.")
        2 -> L("${w("turn")}: přišla čtvrtá společná karta. Ještě jedna přijde.",
            "${w("turn")}: the fourth shared card. One more to come.",
            "${w("turn")}: die vierte Gemeinschaftskarte. Eine kommt noch.")
        else -> L("${w("river")}: pátá a poslední karta. Teď už se nic nezlepší, rozhoduje, co kdo má.",
            "${w("river")}: the fifth and last card. Nothing improves now, the best hand wins.",
            "${w("river")}: die fünfte und letzte Karte. Jetzt verbessert sich nichts mehr.")
    }

    private fun blindsLine(st: GState): String {
        val (sb, bb) = Game.positions()
        val me = when (0) { sb -> L(" Ty jsi teď na small blindu, proto jsi musel vsadit ${st.sb}.", " You're on the small blind now, so you had to put in ${st.sb}.", " Du bist im Small Blind und musstest ${st.sb} setzen.")
            bb -> L(" Ty jsi teď na big blindu, proto jsi musel vsadit ${st.bb}.", " You're on the big blind now, so you had to put in ${st.bb}.", " Du bist im Big Blind und musstest ${st.bb} setzen.")
            else -> "" }
        return L("Na začátku každé handy dávají dva hráči vlevo od ${w("dealer")}a (bílé D) povinné sázky: ${w("small blind")} (${st.sb}) a ${w("big blind")} (${st.bb}). Díky nim je v potu vždycky o co hrát.",
            "At the start of every hand the two players left of the ${w("dealer")} (white D) post forced bets: the ${w("small blind")} (${st.sb}) and the ${w("big blind")} (${st.bb}). That way there's always something in the pot.",
            "Zu Beginn jeder Hand setzen die zwei Spieler links vom ${w("dealer")} (weißes D) Pflichteinsätze: ${w("small blind")} (${st.sb}) und ${w("big blind")} (${st.bb}). So liegt immer etwas im Pot.") + me
    }

    fun actWord(a: String): String = when (a) {
        "fold" -> w("fold"); "check" -> w("check"); "call" -> w("call"); "bet" -> w("bet"); "raise" -> w("raise"); "allin" -> w("all-in"); else -> a
    }

    private fun actExplain(a: String): String = when (a) {
        "fold" -> L("${w("fold")} = zahodíš karty a v téhle handě už nehraješ. Přijdeš jen o to, co už jsi vsadil.", "${w("fold")} = you throw your cards away and sit this hand out. You only lose what you already bet.", "${w("fold")} = du wirfst die Karten weg. Du verlierst nur, was du schon gesetzt hast.")
        "check" -> L("${w("check")} = nic nevsadíš a tah jde dál. Jde to jen tehdy, když nikdo před tebou nevsadil.", "${w("check")} = you bet nothing and pass the turn on. Only possible when nobody has bet.", "${w("check")} = du setzt nichts und gibst weiter. Nur möglich, wenn niemand gesetzt hat.")
        "call" -> L("${w("call")} = dorovnáš sázku soupeře a hraješ dál.", "${w("call")} = you match the opponent's bet and keep playing.", "${w("call")} = du gleichst den Einsatz aus und spielst weiter.")
        "bet" -> L("${w("bet")} = první sázka v tomhle kole. Ostatní ji musí dorovnat, nebo složit.", "${w("bet")} = the first bet this round. Others must call it or fold.", "${w("bet")} = der erste Einsatz der Runde. Andere müssen mitgehen oder folden.")
        "raise" -> L("${w("raise")} = zvýšíš sázku. Soupeři musí dorovnat, nebo složit. Se silnou rukou tak vyhraješ víc.", "${w("raise")} = you increase the bet. Opponents must call or fold. With a strong hand you win more.", "${w("raise")} = du erhöhst. Gegner müssen mitgehen oder folden. Mit starker Hand gewinnst du mehr.")
        else -> L("${w("all-in")} = vsadíš všechny své žetony najednou.", "${w("all-in")} = you bet all your chips at once.", "${w("all-in")} = du setzt alle deine Chips auf einmal.")
    }

    private fun oppAction(name: String, a: Act): String {
        val amt = a.amt?.let { " " + Game.fm(it) } ?: ""
        val meaning = when (a.a) {
            "fold" -> L("složil karty a v téhle handě už nehraje.", "folded and is out of this hand.", "hat gefoldet und ist raus.")
            "check" -> L("dal check, nic nevsadil.", "checked, bet nothing.", "hat gecheckt, nichts gesetzt.")
            "call" -> L("dorovnal sázku$amt.", "called$amt.", "ist mitgegangen$amt.")
            "bet" -> L("vsadil$amt. Kdo sází první, často něco má, ale může i blufovat.", "bet$amt. Whoever bets first often has something, but may be bluffing.", "hat$amt gesetzt. Wer zuerst setzt, hat oft etwas, kann aber bluffen.")
            "raise" -> L("zvýšil na$amt. Zvýšení bývá znamením silné ruky.", "raised to$amt. A raise usually means a strong hand.", "hat auf$amt erhöht. Ein Raise bedeutet meist eine starke Hand.")
            "allin" -> L("šel all-in$amt, vsadil všechno.", "went all-in$amt.", "ging all-in$amt.")
            else -> ""
        }
        return "<b>$name</b> " + meaning
    }

    private var nsKey = ""
    private var nsCache: Reads.NextStats? = null

    fun message(): Msg? {
        val g = Game
        val st = g.st
        if (!g.bot() || !g.playing() || st.over != null) return null
        val hero = st.seats[0]
        fun nm(k: Int) = g.pl(st.seats[k]).name
        // hand finished
        st.done?.let { d ->
            val lines = ArrayList<String>()
            val title: String
            val w0 = d.w.firstOrNull() ?: 0
            if (0 in d.w) {
                title = L("Vyhrál jsi!", "You won!", "Gewonnen!")
                lines.add(L("Získáváš ${w("pot")} ${g.fm(d.amt[0])} žetonů.", "You take the ${w("pot")} of ${g.fm(d.amt[0])} chips.", "Du gewinnst den ${w("pot")} von ${g.fm(d.amt[0])} Chips."))
                lines.add(if (d.sd) L("Na ${w("showdown")}u (porovnání karet na konci) jsi měl nejlepší kombinaci: <b>${I18n.cat(Cards.CAT[d.cat[0] ?: 0])}</b>.", "At the ${w("showdown")} (comparing cards at the end) you had the best hand: <b>${I18n.cat(Cards.CAT[d.cat[0] ?: 0])}</b>.", "Beim ${w("showdown")} hattest du die beste Hand: <b>${I18n.cat(Cards.CAT[d.cat[0] ?: 0])}</b>.")
                else L("Všichni ostatní dali ${w("fold")}, takže pot je tvůj a karty ukazovat nemusíš.", "Everyone else folded, so the pot is yours without showing cards.", "Alle anderen haben gefoldet, der Pot gehört dir ohne Karten zu zeigen."))
            } else {
                title = L("${nm(w0)} vyhrál", "${nm(w0)} won", "${nm(w0)} gewinnt")
                lines.add(if (d.sd) L("Na ${w("showdown")}u měl nejlepší kombinaci: <b>${I18n.cat(Cards.CAT[d.cat[w0] ?: 0])}</b>.", "At the ${w("showdown")} he had the best hand: <b>${I18n.cat(Cards.CAT[d.cat[w0] ?: 0])}</b>.", "Beim ${w("showdown")} hatte er die beste Hand: <b>${I18n.cat(Cards.CAT[d.cat[w0] ?: 0])}</b>.")
                else L("Ostatní složili, takže pot získal bez ukázání karet.", "The others folded, so he took the pot without showing.", "Die anderen haben gefoldet, er gewinnt ohne Zeigen."))
                if (hero.folded) lines.add(L("Ty jsi složil, takže jsi přišel jen o to, co už jsi vsadil (${g.fm(hero.total)}).", "You folded, so you only lost what you had bet (${g.fm(hero.total)}).", "Du hast gefoldet und nur deinen Einsatz verloren (${g.fm(hero.total)})."))
            }
            lines.add(L("V další handě se ${w("dealer")} (bílé D) posune o místo dál a s ním i blindy.", "Next hand the ${w("dealer")} button (white D) moves one seat on, and the blinds with it.", "In der nächsten Hand wandert der ${w("dealer")}-Button (weißes D) weiter, mit ihm die Blinds."))
            return Msg(title, lines)
        }
        val street = st.betStreet
        val lines = ArrayList<String>()
        lines.add(streetIntro(street))
        if (street == 0 && (st.handNo <= 3 || Game.positions().let { it.first == 0 || it.second == 0 })) lines.add(blindsLine(st))
        // what I hold
        val h = st.hero.filterNotNull()
        if (h.size == 2 && !hero.folded) {
            val bd = g.boardKnown()
            if (bd.isEmpty()) {
                val p = Cards.pctOf(h[0], h[1]).toDouble()
                val q = when {
                    p <= 0.1 -> L("výborné, patří mezi 10 % nejlepších startovních rukou", "excellent, in the top 10 % of starting hands", "ausgezeichnet, Top 10 % der Starthände")
                    p <= 0.25 -> L("dobré, lepší než většina rukou", "good, better than most hands", "gut, besser als die meisten Hände")
                    p <= 0.5 -> L("průměrné, hraj je opatrně", "average, play them carefully", "durchschnittlich, vorsichtig spielen")
                    else -> L("slabé, většinou se vyplatí je zahodit", "weak, usually best to fold", "schwach, meist besser folden")
                }
                val pair = h[0] shr 2 == h[1] shr 2
                val suited = h[0] and 3 == h[1] and 3
                val extra = if (pair) L(" Už teď máš pár!", " You already have a pair!", " Du hast schon ein Paar!") else if (suited) L(" Obě karty mají stejnou barvu, můžeš dostat barvu (flush).", " Both cards are the same suit, a flush is possible.", " Beide Karten haben dieselbe Farbe, ein Flush ist möglich.") else ""
                lines.add(L("Tvoje karty <b>${cardStr(h[0])} ${cardStr(h[1])}</b> jsou $q.$extra", "Your cards <b>${cardStr(h[0])} ${cardStr(h[1])}</b> are $q.$extra", "Deine Karten <b>${cardStr(h[0])} ${cardStr(h[1])}</b> sind $q.$extra"))
            } else {
                val sc = Cards.ev(h + bd)
                val c = Cards.cat(sc)
                val onBoard = c <= Cards.cat(Cards.ev(bd)) && c > 0
                lines.add(L("Máš <b>${I18n.cat(Cards.handName(sc).second)}</b>, tedy ${catExplain(c)}.", "You have <b>${I18n.cat(Cards.handName(sc).second)}</b>, that is ${catExplain(c)}.", "Du hast <b>${I18n.cat(Cards.handName(sc).second)}</b>, also ${catExplain(c)}.") +
                    if (onBoard) L(" Pozor, ta kombinace leží na stole, takže ji mají i soupeři.", " Careful, it's on the board, so opponents have it too.", " Achtung, sie liegt auf dem Tisch, die Gegner haben sie auch.") else "")
                if (street < 3) {
                    val key = "${st.handNo}|${st.board}|${st.hero}"
                    if (key != nsKey) { nsKey = key; nsCache = runCatching { Reads.nextStats(st) }.getOrNull() }
                    val v = nsCache?.views?.firstOrNull()
                    if (v != null && v.outs && v.t.imp > 0 && v.t.n > 0) {
                        lines.add(L("Další karta tvou ruku zlepší s pravděpodobností asi ${pct(v.t.imp.toDouble() / v.t.n)}. Karty, které ti pomůžou, se jmenují ${w("outs")} (máš jich ${v.t.imp}).",
                            "The next card improves your hand with about ${pct(v.t.imp.toDouble() / v.t.n)} chance. Helpful cards are called ${w("outs")} (you have ${v.t.imp}).",
                            "Die nächste Karte verbessert deine Hand mit etwa ${pct(v.t.imp.toDouble() / v.t.n)}. Hilfreiche Karten heißen ${w("outs")} (du hast ${v.t.imp})."))
                    }
                }
            }
        }
        // somebody else acts
        if (st.turn != 0) {
            val last = st.seats.withIndex().filter { it.index != 0 }.mapNotNull { (k, s) -> s.actions.lastOrNull()?.let { Triple(k, s, it) } }
                .filter { it.third.st == street && it.third.a != "sb" && it.third.a != "bb" }
            last.lastOrNull()?.let { (k, _, a) -> lines.add(oppAction(nm(k), a)) }
            val title = if (hero.folded) L("Složil jsi, sleduj zbytek handy", "You folded, watch the rest", "Du hast gefoldet, schau zu")
                else if (st.turn > 0) L("Hraje ${nm(st.turn)}", "${nm(st.turn)} to act", "${nm(st.turn)} ist dran") else L("Rozdává se…", "Dealing…", "Es wird gegeben…")
            return Msg(title, lines)
        }
        // my turn
        val toCall = max(0, st.curBet - hero.bet)
        val pot = g.potTotal()
        val e = g.eq
        if (toCall > 0) {
            val need = toCall.toDouble() / (pot + toCall)
            lines.add(L("Abys hrál dál, musíš dorovnat <b>${g.fm(toCall)}</b>, to je ${w("call")}. V ${w("pot")}u je teď ${g.fm(pot)}. Dorovnání se vyplatí, když vyhraješ aspoň v ${pct(need)} případů",
                "To keep playing you must put in <b>${g.fm(toCall)}</b>, that's a ${w("call")}. The ${w("pot")} holds ${g.fm(pot)}. Calling pays off if you win at least ${pct(need)} of the time",
                "Um weiterzuspielen, musst du <b>${g.fm(toCall)}</b> bringen, das ist ein ${w("call")}. Im ${w("pot")} liegen ${g.fm(pot)}. Es lohnt sich, wenn du mindestens in ${pct(need)} der Fälle gewinnst") +
                (if (e != null && !e.allFolded) L(". Tvoje šance je teď asi <b>${pct(e.eq)}</b>, takže ${if (e.eq >= need) "se to vyplatí" else "se to spíš nevyplatí"}.",
                    ". Your chance right now is about <b>${pct(e.eq)}</b>, so it ${if (e.eq >= need) "pays off" else "rather doesn't pay off"}.",
                    ". Deine Chance ist etwa <b>${pct(e.eq)}</b>, es ${if (e.eq >= need) "lohnt sich" else "lohnt sich eher nicht"}.") else "."))
        } else {
            lines.add(L("Nikdo zatím nevsadil. Můžeš dát ${w("check")} a zůstat ve hře zadarmo, nebo vsadit (${w("bet")}).",
                "Nobody has bet yet. You can ${w("check")} and stay in for free, or ${w("bet")}.",
                "Noch hat niemand gesetzt. Du kannst ${w("check")}en und gratis drin bleiben oder setzen (${w("bet")})."))
        }
        val a = if (e != null) runCatching { Advisor.advise() }.getOrNull() else null
        if (a != null && a.act.isNotEmpty()) {
            lines.add(L("Rádce doporučuje: <b>${a.label}</b>.", "The coach suggests: <b>${a.label}</b>.", "Der Berater empfiehlt: <b>${a.label}</b>.") + " " + actExplain(if (a.act == "allin") "allin" else a.act))
            return Msg(L("Jsi na tahu", "Your turn", "Du bist dran"), lines, a.act, a.amt, a.label)
        }
        lines.add(L("Počítám šance…", "Calculating…", "Berechne…"))
        return Msg(L("Jsi na tahu", "Your turn", "Du bist dran"), lines)
    }
}
