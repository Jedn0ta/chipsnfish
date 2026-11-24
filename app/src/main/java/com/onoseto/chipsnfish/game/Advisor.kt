package com.onoseto.chipsnfish.game

import com.onoseto.chipsnfish.I18n
import com.onoseto.chipsnfish.L
import com.onoseto.chipsnfish.game.Game.fm
import com.onoseto.chipsnfish.game.Game.pc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Concrete action advice ("Rádce doporučuje"), ported from the original advise(). Lines may contain <b>…</b>. */
object Advisor {
    class Advice {
        var act = ""
        var amt = 0
        var label = ""
        val lines = ArrayList<String>()
        var tone = "n" // g good, r red, n neutral
        var myTurn = false
    }

    class PosInfo(val name: String, val ip: Boolean, val behindPre: Int)

    fun seatPosInfo(): PosInfo {
        val st = Game.st
        val n = st.n; val d = st.dealer
        val (sb, bb) = Game.positions()
        val name: String
        if (n == 2) name = if (d == 0) "BTN/SB" else "BB"
        else if (d == 0) name = "BTN"
        else if (sb == 0) name = "SB"
        else if (bb == 0) name = "BB"
        else {
            val fromBB = (0 - bb + n) % n
            val left = n - 3
            val after = left - fromBB
            var nm = if (after <= 0) "CO" else if (after == 1) "HJ" else if (fromBB == 1) "UTG" else if (fromBB == 2 && n >= 8) "UTG+1" else "MP"
            if (0 == (d - 1 + n) % n) nm = "CO"
            name = nm
        }
        var behind = 0
        for (i in 1 until n) {
            val j = i % n
            if (st.seats[j].folded) continue
            behind++
            if (j == bb) break
        }
        val liveIdx = st.seats.indices.filter { !st.seats[it].folded }
        fun ord(i: Int) = (i - d - 1 + n) % n
        val heroLast = liveIdx.all { ord(it) <= ord(0) }
        return PosInfo(name, heroLast, behind)
    }

    fun openThreshold(name: String, n: Int): Double {
        val base = mapOf("UTG" to 0.1, "UTG+1" to 0.12, "MP" to 0.15, "HJ" to 0.19, "CO" to 0.27, "BTN" to 0.45, "BTN/SB" to 0.6, "SB" to 0.36, "BB" to 1.0)[name] ?: 0.15
        val adj = if (n <= 3) 1.35 else if (n <= 4) 1.2 else if (n <= 6) 1.0 else 0.85
        return min(0.95, base * adj)
    }

    private fun pct(x: Double) = "${max(1, (x * 100).roundToInt())}"

    fun advise(): Advice? {
        val g = Game
        val st = g.st
        val s = st.seats[0]
        val out = Advice()
        if (!g.playing()) return null
        if (s.folded) {
            out.label = L("Jsi mimo handu", "You're out of the hand", "Du bist nicht in der Hand")
            out.lines.add(L("Sleduj, jak soupeři hrají, a klepni na ně pro jejich čtení. Hodí se to v dalších handách.", "Watch how the opponents play and tap them for their reads. It helps in the next hands.", "Beobachte, wie die Gegner spielen, und tippe sie für ihre Reads an. Das hilft in den nächsten Händen."))
            return out
        }
        if (st.hero.contains(null)) {
            out.label = L("Nejdřív zadej své karty", "Enter your cards first", "Gib zuerst deine Karten ein")
            out.lines.add(L("Bez tvých karet ti rádce nedá konkrétní tah.", "Without your cards the coach can't give a concrete move.", "Ohne deine Karten kann der Berater keinen konkreten Zug geben."))
            return out
        }
        val hero = st.hero.map { it!! }
        val bd = g.boardKnown()
        val street = st.betStreet
        val pot = g.potTotal()
        val toCall = max(0, st.curBet - s.bet)
        val (mn, mx) = g.minMax(s)
        val stackBB = (s.stack + s.bet).toDouble() / max(1, st.bb)
        val opps = g.liveOpps()
        val nOpp = opps.size
        val pos = seatPosInfo()
        val hp = Cards.pctOf(hero[0], hero[1]).toDouble()
        val hc = Cards.hcls(hero[0], hero[1])
        g.ensureEq()
        val e0 = g.eq ?: return null
        if (e0.allFolded) {
            out.act = "check"; out.label = L("Všichni složili, pot je tvůj", "Everyone folded, the pot is yours", "Alle haben gefoldet, der Pot gehört dir")
            return out
        }
        val eq = e0.eq
        val need = if (toCall > 0) toCall.toDouble() / (pot + toCall) else 0.0
        val myTurn = st.turn == 0
        fun R(x: Double): Int = (((x / g.step()).roundToInt()) * g.step()).coerceIn(mn, mx)
        val aggr = opps.map { o -> Triple(o, Reads.model(st, g.P, o), g.pl(o)) }
            .filter { it.first.bet >= st.curBet && st.curBet > 0 }
            .maxByOrNull { it.first.bet }
        val facing = toCall > 0
        val raises = st.seats.sumOf { x -> x.actions.count { q -> q.st == street && (q.a == "raise" || q.a == "bet" || q.a == "allin") } }
        fun Ln(t: String) = out.lines.add(t)
        fun set(a: String, amt: Int, label: String, tone: String) { out.act = a; out.amt = amt; out.label = label; out.tone = tone }
        val ipTxt = if (street > 0) (if (pos.ip) L(" (hraješ poslední)", " (you act last)", " (du handelst zuletzt)") else L(" (hraješ dřív než soupeř)", " (you act before the opponent)", " (du handelst vor dem Gegner)")) else ""
        Ln(L("Tvoje ruka: <b>$hc</b>", "Your hand: <b>$hc</b>", "Deine Hand: <b>$hc</b>") +
            (if (street == 0) L(" · top ${pct(hp)} % startovních rukou", " · top ${pct(hp)} % of starting hands", " · Top ${pct(hp)} % der Starthände") else "") +
            L(" · pozice <b>${pos.name}</b>", " · position <b>${pos.name}</b>", " · Position <b>${pos.name}</b>") + ipTxt)
        Ln(L("Šance proti čteným rozsahům: <b>${pc(eq, 0)}</b>", "Equity vs read ranges: <b>${pc(eq, 0)}</b>", "Equity gegen gelesene Ranges: <b>${pc(eq, 0)}</b>") +
            (if (facing) L(" · na call potřebuješ <b>${pc(need, 0)}</b> (pot odds)", " · to call you need <b>${pc(need, 0)}</b> (pot odds)", " · zum Callen brauchst du <b>${pc(need, 0)}</b> (Pot Odds)") else "") +
            L(" · efektivní stack ${stackBB.roundToInt()} BB", " · effective stack ${stackBB.roundToInt()} BB", " · effektiver Stack ${stackBB.roundToInt()} BB"))

        fun finish(o: Advice): Advice {
            if (!myTurn && g.playing()) o.lines.add(0, L("<i>Teď nejsi na tahu. Doporučení platí pro aktuální stav, až na tebe tah přijde.</i>", "<i>It's not your turn. The advice is for the current state, for when it's your turn.</i>", "<i>Du bist nicht am Zug. Der Rat gilt für den aktuellen Stand, wenn du dran bist.</i>"))
            o.myTurn = myTurn
            return o
        }
        val ALLIN = "ALL-IN"
        fun raiseTo(a: Int) = L("RAISE na ${fm(a)}", "RAISE to ${fm(a)}", "RAISE auf ${fm(a)}")

        // ===== PREFLOP =====
        if (street == 0) {
            val lim = st.seats.withIndex().count { (i, x) -> i != 0 && !x.folded && x.actions.any { q -> q.st == 0 && q.a == "call" } }
            if (stackBB <= 12) {
                val pushT = if (stackBB <= 5) 0.55 else if (stackBB <= 8) 0.35 else 0.22
                val callT = if (stackBB <= 8) 0.18 else 0.12
                if (!facing || st.curBet <= st.bb) {
                    if (hp <= pushT) {
                        set("allin", mx, "$ALLIN ${fm(mx)}", "g")
                        Ln(L("Krátký stack (${stackBB.roundToInt()} BB): hraj push/fold. $hc patří do tvého all-in rozsahu (top ${(pushT * 100).roundToInt()} %).",
                            "Short stack (${stackBB.roundToInt()} BB): play push/fold. $hc belongs to your all-in range (top ${(pushT * 100).roundToInt()} %).",
                            "Kurzer Stack (${stackBB.roundToInt()} BB): spiele Push/Fold. $hc gehört in deine All-in-Range (Top ${(pushT * 100).roundToInt()} %)."))
                    } else {
                        set(if (toCall > 0) "fold" else "check", 0, if (toCall > 0) "FOLD" else "CHECK", "r")
                        Ln(L("S ${stackBB.roundToInt()} BB nemá smysl limpovat ani min-raisovat. $hc je mimo push rozsah.",
                            "With ${stackBB.roundToInt()} BB limping or min-raising makes no sense. $hc is outside the push range.",
                            "Mit ${stackBB.roundToInt()} BB lohnt weder Limpen noch Min-Raise. $hc liegt außerhalb der Push-Range."))
                    }
                } else {
                    if (eq >= need + 0.03 || hp <= callT) {
                        set("call", toCall, "CALL ${fm(toCall)}", "g")
                        Ln(L("Proti all-in rozhoduje čistá matematika: tvoje šance pokrývá pot odds.", "Against an all-in it's pure math: your equity covers the pot odds.", "Gegen ein All-in entscheidet reine Mathematik: deine Equity deckt die Pot Odds."))
                    } else {
                        set("fold", 0, "FOLD", "r")
                        Ln(L("Proti tomuhle all-inu nemáš dost šancí.", "You don't have enough equity against this all-in.", "Gegen dieses All-in hast du nicht genug Equity."))
                    }
                }
                return finish(out)
            }
            if (!facing || (st.curBet == st.bb && raises == 0)) {
                if (g.positions().second == 0 && toCall == 0) {
                    if (hp <= 0.12 && lim > 0) {
                        val amt = R(st.bb * (3.5 + lim))
                        set("raise", amt, raiseTo(amt), "g")
                        Ln(L("Limpeři před tebou: s $hc trestej limp izolačním raisem.", "Limpers before you: punish the limp with $hc with an isolation raise.", "Limper vor dir: bestrafe den Limp mit $hc mit einem Isolation-Raise."))
                    } else {
                        set("check", 0, "CHECK", "n")
                        Ln(L("Jsi na big blindu a nikdo nezvýšil, takže vidíš flop zadarmo.", "You're in the big blind and nobody raised, so you see the flop for free.", "Du bist im Big Blind und niemand hat erhöht, also siehst du den Flop gratis."))
                    }
                    return finish(out)
                }
                val thr = openThreshold(pos.name, st.n) * (if (lim > 0) 0.75 else 1.0)
                if (hp <= thr) {
                    val amt = R(st.bb * ((if (pos.name == "SB") 3.0 else 2.5) + lim))
                    set(if (toCall > 0) "raise" else "bet", amt, raiseTo(amt), "g")
                    Ln(L("Z pozice ${pos.name} otevírej zhruba top ${(thr * 100).roundToInt()} % rukou. $hc tam patří.",
                        "From ${pos.name} open roughly the top ${(thr * 100).roundToInt()} % of hands. $hc belongs there.",
                        "Aus ${pos.name} eröffne etwa die Top ${(thr * 100).roundToInt()} % der Hände. $hc gehört dazu."))
                    Ln(if (lim > 0) L("Za každého limpera přidej 1 BB: ${fm(amt)}.", "Add 1 BB per limper: ${fm(amt)}.", "Pro Limper 1 BB mehr: ${fm(amt)}.")
                    else { val x = if (pos.name == "SB") "3" else L("2,5", "2.5", "2,5"); L("Standardní open je $x BB, tedy ${fm(amt)}.", "The standard open is $x BB, i.e. ${fm(amt)}.", "Der Standard-Open ist $x BB, also ${fm(amt)}.") })
                } else if (pos.name == "SB" && lim == 0 && hp <= 0.55 && toCall <= st.sb) {
                    set("call", toCall, "CALL ${fm(toCall)}", "n")
                    Ln(L("Ze small blindu proti BB můžeš levně dorovnat (complete) se spekulativní rukou.", "From the small blind vs the BB you can cheaply complete with a speculative hand.", "Aus dem Small Blind gegen den BB kannst du mit einer spekulativen Hand günstig completen."))
                } else {
                    set(if (toCall > 0) "fold" else "check", 0, if (toCall > 0) "FOLD" else "CHECK", "r")
                    Ln(L("$hc je z pozice ${pos.name} moc slabá na otevření (hranice top ${(thr * 100).roundToInt()} %). Limpovat se nevyplácí.",
                        "$hc is too weak to open from ${pos.name} (threshold top ${(thr * 100).roundToInt()} %). Limping doesn't pay.",
                        "$hc ist aus ${pos.name} zu schwach zum Eröffnen (Grenze Top ${(thr * 100).roundToInt()} %). Limpen lohnt nicht."))
                }
                return finish(out)
            }
            val rw = aggr?.second?.w ?: 0.2
            val rName = aggr?.third?.name ?: L("soupeř", "opponent", "Gegner")
            val rType = aggr?.let { Reads.prof(it.third).l } ?: ""
            if (raises >= 2) {
                val fourT = min(0.035, rw * 0.25)
                val callT = min(0.06, rw * 0.45)
                if (hp <= fourT) {
                    val amt = if (stackBB < 40) mx else R(st.curBet * 2.3)
                    set(if (amt >= mx) "allin" else "raise", amt, if (amt >= mx) "$ALLIN ${fm(mx)}" else L("4-BET na ${fm(amt)}", "4-BET to ${fm(amt)}", "4-BET auf ${fm(amt)}"), "g")
                    Ln(L("Proti 3-betu jsou $hc na vrcholu rozsahu. Navyšuj pro hodnotu.", "Against a 3-bet $hc is at the top of the range. Raise for value.", "Gegen eine 3-Bet ist $hc ganz oben in der Range. Erhöhe auf Value."))
                } else if (hp <= callT && eq >= need) {
                    set("call", toCall, "CALL ${fm(toCall)}", "n")
                    Ln(L("$hc je dost silná na call, ale ne na další navýšení.", "$hc is strong enough to call, but not to raise again.", "$hc ist stark genug zum Callen, aber nicht für eine weitere Erhöhung."))
                } else {
                    set("fold", 0, "FOLD", "r")
                    Ln(L("Proti 3-betu se drž úzkého rozsahu (cca top ${(callT * 100).roundToInt()} %). $hc sem nepatří.", "Against a 3-bet stick to a narrow range (about top ${(callT * 100).roundToInt()} %). $hc doesn't belong.", "Gegen eine 3-Bet bleib eng (etwa Top ${(callT * 100).roundToInt()} %). $hc gehört nicht dazu."))
                }
                return finish(out)
            }
            val threeT = max(0.025, min(0.09, rw * 0.28))
            val callT = max(0.05, min(0.22, rw * (if (pos.ip) 0.75 else 0.55)))
            val rTypeTxt = if (rType.isNotEmpty()) " ($rType)" else ""
            Ln(L("$rName otevřel$rTypeTxt. Model mu dává zhruba top ${(rw * 100).roundToInt()} % rukou.", "$rName opened$rTypeTxt. The model gives him roughly the top ${(rw * 100).roundToInt()} % of hands.", "$rName hat eröffnet$rTypeTxt. Das Modell gibt ihm etwa die Top ${(rw * 100).roundToInt()} % der Hände."))
            if (hp <= threeT) {
                val amt = R(st.curBet * (if (pos.ip) 3.0 else 3.6) + (if (nOpp > 1) st.curBet * (nOpp - 1.0) else 0.0))
                set("raise", amt, L("3-BET na ${fm(amt)}", "3-BET to ${fm(amt)}", "3-BET auf ${fm(amt)}"), "g")
                Ln(L("$hc je výrazně nad jeho rozsahem. 3-betuj pro hodnotu, ${if (pos.ip) "v pozici 3× jeho sázku" else "mimo pozici zhruba 3,5×"}.",
                    "$hc is well ahead of his range. 3-bet for value, ${if (pos.ip) "in position 3× his bet" else "out of position about 3.5×"}.",
                    "$hc liegt klar über seiner Range. 3-Bette auf Value, ${if (pos.ip) "in Position 3× seinen Einsatz" else "ohne Position etwa 3,5×"}."))
            } else if (hp <= callT && eq >= need - 0.02) {
                set("call", toCall, "CALL ${fm(toCall)}", "n")
                Ln(if (pos.ip) L("V pozici můžeš $hc dorovnat a vidět flop.", "In position you can call with $hc and see the flop.", "In Position kannst du $hc callen und den Flop sehen.")
                else L("Mimo pozici dorovnávej opatrně. $hc na to ještě stačí.", "Out of position call carefully. $hc is still good enough.", "Ohne Position vorsichtig callen. $hc reicht noch."))
                if (pos.name == "BB") Ln(L("Na big blindu máš slevu, už máš v potu zaplacený blind.", "In the big blind you get a discount, your blind is already in the pot.", "Im Big Blind hast du Rabatt, dein Blind ist schon im Pot."))
            } else if (aggr != null && (Reads.effType(aggr.third) == "lag" || Reads.effType(aggr.third) == "maniac") && hp <= 0.14 && pos.ip) {
                val amt = R(st.curBet * 3.0)
                set("raise", amt, L("3-BET na ${fm(amt)}", "3-BET to ${fm(amt)}", "3-BET auf ${fm(amt)}"), "n")
                Ln(L("Proti agresivnímu hráči s širokým rozsahem můžeš 3-betovat i jako light 3-bet.", "Against an aggressive player with a wide range you can also light 3-bet.", "Gegen einen aggressiven Spieler mit breiter Range kannst du auch light 3-betten."))
            } else {
                set("fold", 0, "FOLD", "r")
                Ln(L("Na call by $hc musela být aspoň v top ${(callT * 100).roundToInt()} %. Proti raisu zahoď.", "To call, $hc would need to be at least in the top ${(callT * 100).roundToInt()} %. Fold to the raise.", "Zum Callen müsste $hc mindestens in den Top ${(callT * 100).roundToInt()} % sein. Gegen den Raise folden."))
            }
            return finish(out)
        }

        // ===== POSTFLOP =====
        val sc = Cards.ev(hero + bd)
        val c = Cards.cat(sc)
        val bc = Cards.cat(Cards.ev(bd))
        val t = Cards.tex(bd)
        val wet = (t.ms >= 2 && bd.size < 5) || t.sw >= 3 || t.ms >= 3
        val spr = s.stack.toDouble() / max(1, pot)
        var outs = 0
        var impP = 0.0
        val nc = g.nextCache ?: Reads.nextStats(st).also { g.nextCache = it }
        if (nc.views.isNotEmpty() && street < 3) {
            val v = nc.views[0]
            impP = v.t.imp.toDouble() / v.t.n
            if (v.outs) outs = v.t.imp
        }
        val drawStrong = street < 3 && outs >= 8
        val sprS = String.format(I18n.locale, "%.1f", spr)
        val catTxt = I18n.cat(Cards.CAT[c] + " " + Cards.rl((sc shr 16) and 15))
        Ln(L("Máš: <b>$catTxt</b>", "You have: <b>$catTxt</b>", "Du hast: <b>$catTxt</b>") +
            (if (c <= bc && c > 0) L(" (je na boardu, ne v ruce)", " (it's on the board, not in your hand)", " (liegt auf dem Board, nicht in der Hand)") else "") +
            (if (wet) L(" · board <b>mokrý</b> (drawy)", " · board <b>wet</b> (draws)", " · Board <b>nass</b> (Draws)") else L(" · board <b>suchý</b>", " · board <b>dry</b>", " · Board <b>trocken</b>")) + " · SPR $sprS")
        if (street < 3 && outs > 0) Ln(L("Outy: $outs → zlepšení na další kartě ${pc(impP, 0)}.", "Outs: $outs → improvement on the next card ${pc(impP, 0)}.", "Outs: $outs → Verbesserung mit der nächsten Karte ${pc(impP, 0)}."))
        val valueT = 0.62 + 0.05 * max(0, nOpp - 1)
        if (!facing) {
            if (eq >= valueT) {
                val frac = if (spr < 1.2) 1.0 else if (wet) 0.7 else 0.4
                val amt = if (spr < 1.2) mx else R(st.curBet + pot * frac)
                set(if (amt >= mx) "allin" else "bet", amt, if (amt >= mx) "$ALLIN ${fm(mx)}" else "BET ${fm(amt)}", "g")
                val why = if (spr < 1.2) L("stack je malý vůči potu, jdi all-in.", "the stack is small relative to the pot, go all-in.", "der Stack ist klein im Verhältnis zum Pot, geh all-in.")
                else if (wet) L("na mokrém boardu větší sázkou (~70 % potu) zdraž drawy.", "on a wet board bet bigger (~70 % pot) to charge draws.", "auf nassem Board größer setzen (~70 % Pot), um Draws zahlen zu lassen.")
                else L("na suchém boardu stačí malá sázka (~⅓ potu), ať zaplatí horší ruce.", "on a dry board a small bet (~⅓ pot) is enough so worse hands pay.", "auf trockenem Board reicht ein kleiner Bet (~⅓ Pot), damit schlechtere Hände zahlen.")
                Ln(L("Jsi favorit (${pc(eq, 0)}). Sázej pro hodnotu: $why", "You're the favorite (${pc(eq, 0)}). Bet for value: $why", "Du bist Favorit (${pc(eq, 0)}). Setze auf Value: $why"))
            } else if (drawStrong && nOpp <= 2 && street < 3) {
                val amt = R(pot * 0.6)
                set("bet", amt, "BET ${fm(amt)} (semi-bluff)", "n")
                Ln(L("Silný draw ($outs outů): semi-bluff. Vyhraješ hned, když složí, nebo trefíš.", "Strong draw ($outs outs): semi-bluff. You win now if they fold, or when you hit.", "Starker Draw ($outs Outs): Semi-Bluff. Du gewinnst sofort, wenn er foldet, oder wenn du triffst."))
            } else if (eq >= 0.4) {
                set("check", 0, "CHECK", "n")
                Ln(if (pos.ip) L("Střední síla: v pozici checkni za sebou, kontroluj pot a podívej se na další kartu zadarmo.", "Medium strength: in position check behind, control the pot and see the next card for free.", "Mittlere Stärke: in Position dahinter checken, Pot kontrollieren und die nächste Karte gratis sehen.")
                else L("Střední síla: checkni a nech soupeře rozhodnout. Na malou sázku můžeš dorovnat.", "Medium strength: check and let the opponent decide. You can call a small bet.", "Mittlere Stärke: checke und lass den Gegner entscheiden. Einen kleinen Bet kannst du callen."))
            } else {
                val ot = opps.firstOrNull()?.let { Reads.effType(g.pl(it)) } ?: "unk"
                val canBluff = nOpp == 1 && (ot == "rock" || ot == "tag") && (street == 3 || !wet) && pos.ip
                if (canBluff) {
                    val amt = R(pot * (if (street == 3) 0.66 else 0.5))
                    set("bet", amt, "BET ${fm(amt)} (bluff)", "n")
                    val tl = Reads.PROF.getValue(ot).l
                    Ln(L("Slabá ruka, ale jediný soupeř je $tl, ten umí složit. Malý bluff z pozice dává smysl.", "Weak hand, but the only opponent is a $tl who can fold. A small bluff in position makes sense.", "Schwache Hand, aber der einzige Gegner ist ein $tl, der folden kann. Ein kleiner Bluff in Position ergibt Sinn."))
                } else {
                    set("check", 0, "CHECK", "r")
                    Ln(L("Slabá ruka (${pc(eq, 0)}). Checkni a na sázku spíš zahoď", "Weak hand (${pc(eq, 0)}). Check and fold to a bet", "Schwache Hand (${pc(eq, 0)}). Checke und folde auf einen Bet") +
                        (if (nOpp > 1) L(". Proti víc soupeřům se bluff nevyplácí.", ". Bluffing multiway doesn't pay.", ". Gegen mehrere Gegner lohnt kein Bluff.") else "."))
                }
            }
            return finish(out)
        }
        // facing a bet
        val bl = aggr?.let { Reads.bluffRead(st, it.first, it.third) }
        if (aggr != null) {
            val b = bl?.v ?: Reads.baseBluff(aggr.third)
            val tl = Reads.prof(aggr.third).l
            Ln(L("${aggr.third.name} ($tl) vsadil ${fm(aggr.first.bet)}. Odhad bluffu: <b>${(b * 100).roundToInt()} %</b>.", "${aggr.third.name} ($tl) bet ${fm(aggr.first.bet)}. Bluff estimate: <b>${(b * 100).roundToInt()} %</b>.", "${aggr.third.name} ($tl) setzte ${fm(aggr.first.bet)}. Bluff-Schätzung: <b>${(b * 100).roundToInt()} %</b>."))
        }
        var impl = 0.0
        if (drawStrong && spr > 2) impl = min(s.stack.toDouble(), pot.toDouble()) * 0.25
        val needImpl = toCall / (pot + toCall + impl)
        if (eq >= 0.7 && eq >= need + 0.25) {
            val amt = if (spr < 2.2) mx else R(st.curBet * (if (wet) 3.0 else 2.6) + pot * 0.25)
            set(if (amt >= mx) "allin" else "raise", amt, if (amt >= mx) "$ALLIN ${fm(mx)}" else raiseTo(amt), "g")
            Ln(L("Máš výrazně navrch (${pc(eq, 0)}). Navyšuj pro hodnotu", "You're well ahead (${pc(eq, 0)}). Raise for value", "Du liegst klar vorn (${pc(eq, 0)}). Erhöhe auf Value") +
                (if (wet) L(" a nedávej drawům levnou kartu", " and don't give draws a cheap card", " und gib Draws keine billige Karte") else "") +
                (if (spr < 2.2) L(". Při nízkém SPR rovnou all-in.", ". With low SPR go all-in.", ". Bei niedrigem SPR direkt all-in.") else "."))
        } else if (drawStrong && nOpp == 1 && street == 1 && spr > 3 && eq >= 0.38 && aggr != null && Reads.effType(aggr.third) != "station") {
            val amt = R(st.curBet * 3.0)
            set("raise", amt, raiseTo(amt) + " (semi-bluff)", "n")
            Ln(L("Silný draw na flopu proti jednomu hráči: semi-bluff raise ti dá dvě cesty k výhře.", "Strong draw on the flop against one player: a semi-bluff raise gives you two ways to win.", "Starker Draw am Flop gegen einen Spieler: ein Semi-Bluff-Raise gibt dir zwei Wege zum Sieg."))
        } else if (eq >= need) {
            set("call", toCall, "CALL ${fm(toCall)}", "g")
            Ln(L("Tvoje šance (${pc(eq, 0)}) je nad pot odds (${pc(need, 0)}), call je dlouhodobě ziskový.", "Your equity (${pc(eq, 0)}) is above the pot odds (${pc(need, 0)}), calling is profitable long-term.", "Deine Equity (${pc(eq, 0)}) liegt über den Pot Odds (${pc(need, 0)}), Callen ist langfristig profitabel."))
            if (street == 3 && bl != null && bl.v >= 0.3) Ln(L("Na riveru je to bluff-catch: jeho linie má dost bluffů.", "On the river it's a bluff-catch: his line has plenty of bluffs.", "Am River ist es ein Bluff-Catch: seine Linie hat genug Bluffs."))
        } else if (impl > 0 && eq >= needImpl) {
            set("call", toCall, "CALL ${fm(toCall)} (implied odds)", "n")
            Ln(L("Na čisté pot odds to nestačí, ale s drawem a hlubokými stacky vyděláš, když trefíš (implied odds).", "Pure pot odds aren't enough, but with a draw and deep stacks you win when you hit (implied odds).", "Reine Pot Odds reichen nicht, aber mit Draw und tiefen Stacks verdienst du, wenn du triffst (Implied Odds)."))
        } else {
            set("fold", 0, "FOLD", "r")
            Ln(L("Potřebuješ ${pc(need, 0)}, máš ${pc(eq, 0)}. Dlouhodobě je to ztrátový call.", "You need ${pc(need, 0)}, you have ${pc(eq, 0)}. Long-term it's a losing call.", "Du brauchst ${pc(need, 0)}, hast ${pc(eq, 0)}. Langfristig ein Verlust-Call."))
            if (bl != null && bl.v < 0.15) Ln(L("Navíc tenhle hráč v takové situaci bluffuje málokdy.", "Also, this player rarely bluffs in such spots.", "Außerdem blufft dieser Spieler in solchen Situationen selten."))
        }
        return finish(out)
    }
}
