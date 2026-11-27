package com.onoseto.chipsnfish.game

import kotlinx.serialization.Serializable

@Serializable
data class Act(
    val st: Int,
    var a: String,
    var amt: Int? = null,
    var sz: String? = null,
)

@Serializable
data class Seat(
    var pid: String,
    var stack: Int,
    var folded: Boolean = false,
    var allin: Boolean = false,
    var bet: Int = 0,
    var total: Int = 0,
    var acted: Boolean = false,
    var actions: MutableList<Act> = mutableListOf(),
    var out: Boolean = false,
    var mood: MutableList<String> = mutableListOf(),
)

@Serializable
data class Player(
    val id: String,
    var name: String,
    var img: String = "",
    var override: String = "auto",
    var notes: String = "",
    var hands: Int = 0,
    var vp: Int = 0,
    var pfr: Int = 0,
    var agg: Int = 0,
    var calls: Int = 0,
    var caught: Int = 0,
    var showed: Int = 0,
    var t: Long = 0,
    var stored: Boolean = false,
    var bot: Boolean = false,
    var bt: String? = null,
    var hat: String = "",
)

@Serializable
data class Done(
    val w: List<Int>,
    val amt: List<Int>,
    val sd: Boolean,
    val cat: Map<Int, Int>,
    val net: Int,
)

@Serializable
data class Over(val win: Boolean, val place: Int)

@Serializable
data class GStats(var hands: Int = 0, var won: Int = 0)

@Serializable
data class GState(
    /** >0 = hero busted out in this place and keeps watching the computer players */
    var watchPlace: Int = 0,
    var mode: String = "coach",
    var blindMin: Int = 15,
    var blindStart: Long = 0,
    var blindPaused: Int? = null,
    var phase: String = "setup",
    var n: Int = 1,
    var dealer: Int = 0,
    var startLevel: Int = 0,
    var level: Int = 0,
    var curLevel: Int = 0,
    var sb: Int = 1,
    var bb: Int = 2,
    var startStack: Int = 200,
    var autoBlinds: Boolean = true,
    var seats: MutableList<Seat> = mutableListOf(),
    var sample: Boolean = false,
    var tab: Int = 0,
    var handNo: Int = 0,
    var hero: MutableList<Int?> = mutableListOf(null, null),
    var board: MutableList<Int?> = mutableListOf(null, null, null, null, null),
    var betStreet: Int = 0,
    var curBet: Int = 0,
    var lastRaise: Int = 2,
    var turn: Int = -1,
    var done: Done? = null,
    var reveal: MutableMap<Int, List<Int>>? = null,
    var hole: MutableMap<Int, List<Int>> = mutableMapOf(),
    var deck: MutableList<Int> = mutableListOf(),
    var shown: MutableMap<Int, MutableList<Int?>> = mutableMapOf(),
    var over: Over? = null,
    var stats: GStats? = null,
    var speed: Double = 1.0,
    var blindHands: Int = 0,
    var pos: MutableList<Double>? = null,
    var cfgDone: Boolean = false,
    var turnAt: Long = 0,
    var doneAt: Long = 0,
)

@Serializable
data class RecSeat(
    val pid: String,
    val name: String,
    val mood: List<String>? = null,
    val folded: Boolean,
    val actions: List<Act>,
    val reveal: String = "none",
    val cards: List<Int?>? = null,
)

@Serializable
data class HandRec(
    val id: String,
    val no: Int,
    val t: Long,
    val n: Int,
    val blinds: String,
    val street: Int,
    val hero: List<Int?>,
    val board: List<Int?>,
    val result: String,
    val winner: String? = null,
    val eq: Double? = null,
    val pot: Int,
    val net: Int,
    val seats: List<RecSeat>,
)

@Serializable
data class ChipDef(val k: String, val l: String, val c: Long, var v: Int, var off: Boolean = false)

@Serializable
data class Look(var felt: String = "blue", var back: String = "red", var face: String = "classic")

@Serializable
data class GameCfg(
    var opp: Int = 5,
    var stack: Int = 100,
    var level: Int = 0,
    var every: Int = 10,
    var style: String = "mix",
    var speed: Double = 1.0,
    var coach: Boolean = false,
)

/** End-of-hand dialog state for the live advisor mode. */
data class EndState(
    var result: String,
    var winner: String?,
    val rev: MutableMap<String, String> = mutableMapOf(),
    var manRes: Boolean = false,
    val man: MutableMap<String, Boolean> = mutableMapOf(),
)
