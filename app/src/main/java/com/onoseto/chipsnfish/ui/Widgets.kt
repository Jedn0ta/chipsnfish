package com.onoseto.chipsnfish.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.draw.drawWithContent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onoseto.chipsnfish.game.Cards
import com.onoseto.chipsnfish.game.ChipDef
import com.onoseto.chipsnfish.game.Game
import com.onoseto.chipsnfish.game.Player
import com.onoseto.chipsnfish.game.Reads
import kotlin.math.min

// ---------------- text helpers ----------------

fun rich(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    var bold = false
    var ital = false
    while (i < s.length) {
        when {
            s.startsWith("<b>", i) -> { bold = true; i += 3 }
            s.startsWith("</b>", i) -> { bold = false; i += 4 }
            s.startsWith("<i>", i) -> { ital = true; i += 3 }
            s.startsWith("</i>", i) -> { ital = false; i += 4 }
            else -> {
                val st = SpanStyle(fontWeight = if (bold) FontWeight.ExtraBold else null, fontStyle = if (ital) FontStyle.Italic else null)
                pushStyle(st); append(s[i]); pop(); i++
            }
        }
    }
}

@Composable
fun T(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = C.ink,
    weight: FontWeight = FontWeight.Normal,
    num: Boolean = false,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    letter: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    /** Shrink the font (down to 55 %) until the text fits its box instead of being cut off. */
    fit: Boolean = false,
) {
    watch()
    if (!fit) {
        Text(
            text, modifier = modifier, color = color, fontSize = size, fontWeight = if (num) FontWeight.Bold else weight,
            fontFamily = if (num) F.num else F.body, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            letterSpacing = letter, lineHeight = lineHeight,
        )
        return
    }
    var k by remember(text, size) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var ready by remember(text, size) { mutableStateOf(false) }
    Text(
        text, modifier = modifier.drawWithContent { if (ready) drawContent() }, color = color, fontSize = size * k,
        fontWeight = if (num) FontWeight.Bold else weight, fontFamily = if (num) F.num else F.body, textAlign = align,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, softWrap = maxLines > 1,
        letterSpacing = if (letter.isSpecified) letter * k else letter, lineHeight = if (lineHeight.isSpecified) lineHeight * k else lineHeight,
        onTextLayout = { r -> if ((r.hasVisualOverflow || r.isLineEllipsized(r.lineCount - 1)) && k > 0.56f) k *= 0.9f else ready = true },
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) =
    T(text.uppercase(), 11.sp, C.muted, FontWeight.ExtraBold, letter = 1.1.sp, modifier = modifier)

@Composable
fun Note(text: String, modifier: Modifier = Modifier) = Text(rich(text), modifier = modifier, color = C.muted, fontSize = 12.5.sp, lineHeight = 16.sp)

fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(interactionSource = null, indication = null, onClick = onClick)

// ---------------- cards ----------------

private fun suitColor(s: Int, face: String): Color = when {
    face == "four" && s == 2 -> Color(0xFF1F62C9)
    face == "four" && s == 3 -> Color(0xFF17864A)
    Cards.RED[s] -> C.cardRed
    else -> C.cardBlk
}

@Composable
fun CardView(c: Int, cw: Dp, face: String = Game.look.face, glow: Boolean = false, modifier: Modifier = Modifier) {
    watch()
    val r = c shr 2
    val s = c and 3
    val col = suitColor(s, face)
    val w = cw.value
    val shape = RoundedCornerShape(cw * 0.13f)
    Box(
        modifier
            .size(cw, cw * 1.4f)
            .shadow(cw * 0.1f, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .then(if (glow) Modifier.border(2.dp, C.gold.copy(alpha = 0.8f), shape) else Modifier.border(0.6.dp, Color(0xFFCFCAC0), shape))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF6F3EC), Color(0xFFEDE8DD))))
    ) {
        val jumbo = face == "jumbo"
        val pic = face == "picture" && r >= 9
        Column(Modifier.padding(start = cw * 0.07f, top = cw * 0.02f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Cards.rl(r), color = col, fontFamily = F.num, fontWeight = FontWeight.ExtraBold, fontSize = (w * (if (jumbo) 0.66f else 0.5f)).sp, lineHeight = (w * (if (jumbo) 0.62f else 0.46f)).sp, letterSpacing = (-0.5).sp)
            Text(Cards.SYM[s], color = col, fontSize = (w * (if (jumbo) 0.42f else 0.32f)).sp, lineHeight = (w * (if (jumbo) 0.42f else 0.32f)).sp)
        }
        if (pic) {
            Box(Modifier.align(Alignment.Center).offset(x = cw * 0.12f, y = cw * 0.12f), contentAlignment = Alignment.Center) {
                Text(when (r) { 9 -> "♞"; 10 -> "♛"; 11 -> "♚"; else -> Cards.SYM[s] }, color = col, fontSize = (w * 0.62f).sp)
            }
        } else {
            Text(Cards.SYM[s], color = col.copy(alpha = if (jumbo) 0.85f else 1f), fontSize = (w * (if (jumbo) 0.4f else 0.62f)).sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = cw * 0.06f, bottom = cw * 0.02f), lineHeight = (w * 0.62f).sp)
        }
    }
}

@Composable
fun CardBack(cw: Dp, back: String = Game.look.back, modifier: Modifier = Modifier) {
    watch()
    val shape = RoundedCornerShape(cw * 0.13f)
    Box(modifier.size(cw, cw * 1.4f).clip(shape).background(Color.White).padding(maxOf(1.dp, cw * 0.045f)).clip(RoundedCornerShape(cw * 0.1f))) {
        if (back == "fish") {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF173A52), Color(0xFF0B1A26)))), contentAlignment = Alignment.Center) {
                FishLogo(Modifier.size(cw * 0.7f))
            }
        } else {
            val (a, b) = when (back) {
                "blue" -> Color(0xFF1F4FA0) to Color(0xFF163A78)
                "black" -> Color(0xFF2B2B2B) to Color(0xFF121212)
                else -> Color(0xFF9C1C26) to Color(0xFF7C1119)
            }
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.radialGradient(listOf(a, b), center, size.maxDimension * 0.7f))
                val step = 5.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(Color.White.copy(alpha = 0.09f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.09f), Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1.dp.toPx())
                    x += step
                }
                val ins = size.minDimension * 0.09f
                drawRect(Color(0xFFE8C56A).copy(alpha = 0.85f), Offset(ins, ins), androidx.compose.ui.geometry.Size(size.width - ins * 2, size.height - ins * 2), style = Stroke(maxOf(1f, size.minDimension * 0.035f)))
                val c = center; val d = size.minDimension * 0.22f
                val dia = androidx.compose.ui.graphics.Path().apply { moveTo(c.x, c.y - d * 1.3f); lineTo(c.x + d, c.y); lineTo(c.x, c.y + d * 1.3f); lineTo(c.x - d, c.y); close() }
                drawPath(dia, Color(0xFFE8C56A).copy(alpha = 0.9f))
                drawPath(dia, Color.Black.copy(alpha = 0.25f), style = Stroke(1f))
            }
        }
    }
}

@Composable
fun CardSlot(cw: Dp, label: String = "+", next: Boolean = false, locked: Boolean = false, modifier: Modifier = Modifier) {
    watch()
    val shape = RoundedCornerShape(cw * 0.13f)
    val col = if (next) C.gold else Color.White.copy(alpha = if (locked) 0.28f else 0.5f)
    Box(
        modifier.size(cw, cw * 1.4f).clip(shape)
            .background(if (next) C.gold.copy(alpha = 0.08f) else Color.Black.copy(alpha = if (locked) 0.14f else 0.18f))
            .drawBehind {
                drawRoundRect(
                    color = if (locked) Color.White.copy(alpha = 0.08f) else col,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cw.toPx() * 0.13f),
                    style = Stroke(2.dp.toPx(), pathEffect = if (locked) null else PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        T(label, (cw.value * if (locked) 0.24f else 0.4f).sp, col, num = locked || label.length > 1, letter = if (locked) 0.5.sp else TextUnit.Unspecified)
    }
}

// ---------------- chips ----------------

@Composable
fun ChipIcon(c: ChipDef, size: Dp = 22.dp) {
    watch()
    if (c.k == "bill") {
        Box(Modifier.size(size * 1.5f, size * 0.8f).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Color(0xFFEFE4BF), Color(0xFFD3C088)))).border(1.dp, Color(0xFF8F7D45), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            T(c.v.toString(), (size.value * 0.5f).sp, Color(0xFF3D4A20), num = true)
        }
    } else ChipDisc(Color(c.c), size)
}

@Composable
fun ChipDisc(col: Color, size: Dp) {
    watch()
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(Color.Black.copy(alpha = 0.55f), r, center.copy(y = center.y + 1.6f))
        drawCircle(col, r)
        val sw = r * 0.3f
        drawCircle(Color.White.copy(alpha = 0.92f), r - sw / 2, style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.52f, r * 0.45f))))
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.6f, style = Stroke(r * 0.07f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.12f, r * 0.1f))))
        drawCircle(col, r * 0.52f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), Offset(center.x - r * 0.35f, center.y - r * 0.4f), r * 0.9f), r)
        drawCircle(Color.Black.copy(alpha = 0.35f), r - 0.5f, style = Stroke(1f))
    }
}

/** Stacked chip piles like the original chipPile(). */
@Composable
fun ChipPile(amt: Int, maxTypes: Int = 3, chip: Dp = 16.dp) {
    watch()
    if (amt <= 0) return
    val parts = Game.chipBreak(amt).take(maxTypes)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((c, n) in parts) {
            val bill = c.k == "bill"
            val m = min(if (bill) 3 else 6, n)
            val stepY = if (bill) 2.dp else chip * 0.15f
            val h = (if (bill) chip * 0.8f else chip) + stepY * (m - 1)
            Box(Modifier.width(if (bill) chip * 1.6f else chip).heightIn(min = h)) {
                for (i in 0 until m) {
                    Box(Modifier.align(Alignment.BottomStart).offset(x = if (bill) (i * 1.5f).dp else 0.dp, y = -stepY * i)) {
                        if (bill) ChipIcon(c, chip) else ChipDisc(Color(c.c), chip)
                    }
                }
            }
        }
    }
}

// ---------------- avatar ----------------

private val imgCache = HashMap<Int, ImageBitmap?>()

fun decodeImg(dataUrl: String): ImageBitmap? {
    if (dataUrl.isEmpty()) return null
    val key = dataUrl.hashCode()
    return imgCache.getOrPut(key) {
        runCatching {
            val b64 = dataUrl.substringAfter("base64,", dataUrl)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
}

fun hue(s: String): Float { var h = 0; for (ch in s) h = (h * 31 + ch.code) % 360; return h.toFloat() }

fun initials(n: String): String {
    val w = n.trim().split(Regex("\\s+"))
    val a = w.getOrNull(0)?.firstOrNull()?.toString() ?: "?"
    val b = if (w.size > 1) w[1].firstOrNull()?.toString() ?: "" else w.getOrNull(0)?.getOrNull(1)?.toString() ?: ""
    return (a + b).uppercase()
}

@Composable
fun Avatar(p: Player, size: Dp, ring: Color? = null, shape: Shape = CircleShape, border: Dp = 3.dp, dim: Boolean = false, overlay: @Composable BoxScope.() -> Unit = {}) {
    watch()
    val rc = ring ?: if (p.id == "hero") C.gold else C.type(Reads.effType(p))
    val img = decodeImg(p.img)
    Box(Modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().clip(shape)
                .background(Color.hsl(hue(p.name), 0.35f, 0.32f))
                .border(border, rc, shape)
                .graphicsLayer(alpha = if (dim) 0.5f else 1f, compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha),
            contentAlignment = Alignment.Center,
        ) {
            if (img != null) Image(img, null, Modifier.fillMaxSize().padding(border - 0.5.dp).clip(shape), contentScale = ContentScale.Crop)
            else T(initials(p.name), (size.value * 0.36f).sp, Color.White, num = true)
        }
        // hat on the head (bought with wallet chips)
        val hat = com.onoseto.chipsnfish.online.Social.hatIcon(p.hat)
        if (hat.isNotEmpty() && size >= 22.dp) Box(
            Modifier.align(Alignment.TopCenter).offset(x = size * 0.06f, y = -size * 0.42f)
                .graphicsLayer(rotationZ = -14f, alpha = if (dim) 0.6f else 1f),
        ) { T(hat, (size.value * 0.5f).sp, lineHeight = (size.value * 0.6f).sp) }
        overlay()
    }
}

// ---------------- logo ----------------

internal val fishPath by lazy { PathParser().parsePathString("M10.5 20c2.6-4.2 6.4-5.6 10.2-4.6 2.2.6 3.9 2 5 3.4l3.3-2.6v7.6l-3.3-2.6c-1.1 1.4-2.8 2.8-5 3.4-3.8 1-7.6-.4-10.2-4.6z").toPath() }

@Composable
fun FishLogo(modifier: Modifier = Modifier) {
    watch()
    Canvas(modifier) {
        val s = size.minDimension / 40f
        translate((size.width - 40 * s) / 2, (size.height - 40 * s) / 2) {
            scale(s, s, Offset.Zero) {
                val c = Offset(20f, 20f)
                drawCircle(C.gold2, 18.5f, c)
                drawCircle(Color.White, 18.5f - 1.75f, c, style = Stroke(3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.2f, 5.4f))))
                drawCircle(C.gold, 12.5f, c)
                drawPath(fishPath, Color(0xFF1D4F7A))
                drawCircle(Color.White, 1.2f, Offset(14.6f, 19.2f))
            }
        }
    }
}

// ---------------- buttons ----------------

enum class BK { FOLD, CALL, RAISE, DARK, GOLD, GHOST, DANGER, BLUE }

@Composable
fun GBtn(
    kind: BK,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    on: Boolean = false,
    ring: Color? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    watch()
    val shape = RoundedCornerShape(14.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val bg: Brush = when (kind) {
        BK.FOLD -> Brush.verticalGradient(listOf(Color(0xFFE25A4B), C.fold, Color(0xFF8E2418)))
        BK.CALL -> Brush.verticalGradient(listOf(Color(0xFF3CCB7A), C.call, Color(0xFF16733F)))
        BK.RAISE -> Brush.verticalGradient(listOf(Color(0xFFFFD976), C.gold, C.gold2))
        BK.GOLD -> Brush.verticalGradient(listOf(Color(0xFF6CC4FF), Color(0xFF2F7BFF), Color(0xFF1C47C9)))
        BK.DARK -> Brush.verticalGradient(listOf(Color(0xFF2A3E66), Color(0xFF18284A), Color(0xFF101C38)))
        BK.GHOST -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f)))
        BK.BLUE -> Brush.verticalGradient(listOf(Color(0xFF6CC4FF), Color(0xFF2F7BFF), Color(0xFF1C47C9)))
        BK.DANGER -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val flat = kind == BK.GHOST || kind == BK.DANGER
    Box(
        modifier
            .graphicsLayer(scaleX = sc, scaleY = sc)
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (!flat && enabled) Modifier.shadow(if (pressed) 1.dp else 4.dp, shape) else Modifier)
            .then(if (ring != null) Modifier.border(3.dp, ring, shape) else Modifier)
            .clip(shape)
            .background(bg)
            .then(if (on) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .then(if (kind == BK.GHOST) Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), shape) else Modifier)
            .then(if (kind == BK.DANGER) Modifier.border(1.dp, C.lose, shape) else Modifier)
            .clickable(src, null, enabled = enabled) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onClick() }
            .drawBehind {
                if (!flat) drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f)), 0f, size.height * 0.5f), size = size.copy(height = size.height * 0.5f))
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Cards fly in when they appear on the table. */
@Composable
fun DealIn(key: Any?, content: @Composable () -> Unit) {
    watch()
    val a = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(key) { a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 420f)) }
    Box(Modifier.graphicsLayer(alpha = a.value.coerceIn(0f, 1f), scaleX = 0.6f + 0.4f * a.value, scaleY = 0.6f + 0.4f * a.value, translationY = (1 - a.value) * -40f, rotationZ = (1 - a.value) * -8f)) { content() }
}

fun btnInk(kind: BK): Color = when (kind) {
    BK.RAISE -> C.goldInk
    BK.GHOST -> C.ink
    BK.DANGER -> C.lose
    else -> Color.White
}

@Composable
fun Btn(text: String, kind: BK = BK.GOLD, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 44.dp, onClick: () -> Unit) {
    watch()
    GBtn(kind, modifier.heightIn(min = height), enabled, onClick = onClick) {
        T(text, 14.5.sp, btnInk(kind), FontWeight.ExtraBold, align = TextAlign.Center, maxLines = 2, lineHeight = 17.sp, fit = true)
    }
}

@Composable
fun Pill(text: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    watch()
    val shape = RoundedCornerShape(99.dp)
    Box(
        Modifier.alpha(if (enabled) 1f else 0.5f).clip(shape)
            .background(if (on) Color(0xFF2F7BFF) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, if (on) Color(0xFF8AD2FF) else Color.White.copy(alpha = 0.18f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) { T(text, 13.sp, Color.White, FontWeight.Bold, maxLines = 1, fit = true) }
}

@Composable
fun BoxPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    watch()
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(C.panel2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

val numStyle = TextStyle(fontFamily = F.num, fontWeight = FontWeight.ExtraBold)

/**
 * Subscribes the calling composable to every app-level change counter. The game data itself is plain
 * (not snapshot state), so each screen part reads these counters to redraw when anything changes.
 */
fun watch() {
    Game.rev
    Ui.sheetRev
    com.onoseto.chipsnfish.online.Online.rev
}
