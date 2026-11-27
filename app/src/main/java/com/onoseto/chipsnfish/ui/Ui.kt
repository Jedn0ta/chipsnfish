package com.onoseto.chipsnfish.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onoseto.chipsnfish.game.Game
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** A bottom sheet / dialog. [arg] holds sheet-specific mutable state, [ret] is reopened on close. */
class Sheet(val kind: String, val arg: Any? = null, val ret: Sheet? = null)

class ConfirmArg(val title: String, val text: String, val ok: String, val fn: () -> Unit)
class SettingsArg(var tab: String = "game", var level: Int, var startStack: Int, var autoBlinds: Boolean, var dealer: Int, var bk: String? = null)
class BlindsArg(var sel: Int)
class CardArg(var slot: Int)
class PlayerArg(val id: String, var confirm: Boolean = false)

object Ui {
    var view by mutableStateOf("table")
    var solo by mutableStateOf(false)
    var menu by mutableStateOf(false)
    var splash by mutableStateOf(true)
    var sheet by mutableStateOf<Sheet?>(null)
    var sheetRev by mutableIntStateOf(0)
    var tip by mutableStateOf<List<String>?>(null)
    var moodSeat by mutableIntStateOf(-1)
    var fishText by mutableStateOf<String?>(null)
    var fishId by mutableIntStateOf(0)
    var swimId by mutableIntStateOf(0)
    var rainId by mutableIntStateOf(0)
    var bye by mutableStateOf(false)
    var peek by mutableStateOf(false)
    /** Hole cards being dragged to the fold zone (offset in px), whether they're over it, and where the zone is (root px). */
    var foldDrag by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    var foldHot by mutableStateOf(false)
    var foldZone = androidx.compose.ui.geometry.Offset.Zero
    var foldR = 0f
    var exitRequest by mutableIntStateOf(0)
    var coachMin by mutableStateOf(false)
    /** Pending invite from a link: "t" table code or "g" group id. */
    var deep by mutableStateOf<Pair<String, String>?>(null)

    fun open(kind: String, arg: Any? = null, ret: Sheet? = null) { sheet = Sheet(kind, arg, ret); sheetRev++; refreshActive() }
    fun close() { sheet = sheet?.ret; sheetRev++; refreshActive() }
    fun redraw() { sheetRev++ }

    fun refreshActive() {
        val a = !menu && !splash && sheet == null && view == "table" && !bye
        if (a != Game.screenActive) { Game.screenActive = a }
        Game.gameTick()
    }

    fun goView(v: String) { view = v; refreshActive() }
    fun showMenu() { menu = true; refreshActive() }
    fun hideMenu() { menu = false; refreshActive() }

    fun confirm(title: String, text: String, ok: String, fn: () -> Unit) = open("confirm", ConfirmArg(title, text, ok, fn))

    fun openSettings() {
        val s = Game.st
        open("settings", SettingsArg(tab = if (Game.gm() && !menu) "game" else "table", level = s.startLevel, startStack = s.startStack, autoBlinds = s.autoBlinds, dealer = s.dealer))
    }

    fun openGameSetup() = open("gamesetup", Game.gameCfg.copy())

    fun fishSay(t: String) { fishText = t; fishId++ }
}

/** Synthesised chip clatter (port of the Web Audio chipSound()). */
object Sound {
    private const val RATE = 22050
    private val pcm: ShortArray by lazy { build() }

    private fun build(): ShortArray {
        val r = Random(7)
        val total = (RATE * 0.9).toInt()
        val buf = FloatArray(total)
        var t = 0.02
        repeat(14) {
            t += 0.035 + r.nextDouble() * 0.07
            val start = (t * RATE).toInt()
            val len = (RATE * 0.03).toInt()
            val f = 2600 + r.nextDouble() * 2400
            val amp = 0.55 + r.nextDouble() * 0.3
            var lp = 0.0; var bp = 0.0
            val w = 2 * sin(PI * f / RATE)
            for (j in 0 until len) {
                val i = start + j
                if (i >= total) break
                val noise = (r.nextDouble() * 2 - 1) * (1 - j.toDouble() / len).pow(3)
                // simple state-variable band-pass
                lp += w * bp
                val hp = noise - lp - 0.25 * bp
                bp += w * hp
                buf[i] += (bp * amp * 0.9).toFloat()
            }
            val tf = 3800 + r.nextDouble() * 2200
            val tl = (RATE * 0.05).toInt()
            for (j in 0 until tl) {
                val i = start + j
                if (i >= total) break
                val env = 0.08 * (1 - j.toDouble() / tl).pow(4)
                val ph = (j * tf / RATE) % 1.0
                val tri = 4 * kotlin.math.abs(ph - 0.5) - 1
                buf[i] += (tri * env).toFloat()
            }
        }
        val peak = buf.maxOf { kotlin.math.abs(it) }.coerceAtLeast(0.001f)
        return ShortArray(total) { (buf[it] / peak * 0.8f * Short.MAX_VALUE).toInt().toShort() }
    }

    private val chime: ShortArray by lazy {
        val n = (RATE * 0.45).toInt()
        ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val e1 = kotlin.math.exp(-t * 9) * (if (t < 0.18) 1.0 else 0.0)
            val e2 = if (t >= 0.12) kotlin.math.exp(-(t - 0.12) * 7) else 0.0
            val v = 0.35 * e1 * sin(2 * PI * 880 * t) + 0.35 * e2 * sin(2 * PI * 1318.5 * t)
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Soft two-note chime: it's your turn. */
    // sounds are switched off (user request); kept as no-ops so call sites stay simple
    fun turn() {}

    fun chip() {}

    private fun play(data: ShortArray) {
        thread(isDaemon = true) {
            runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(data, 0, data.size)
                track.play()
                Thread.sleep(1000)
                track.release()
            }
        }
    }
}
