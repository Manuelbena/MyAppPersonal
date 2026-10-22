package com.syncro.presentation.savings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.BudgetPlan
import com.syncro.domain.model.CategoryShare
import com.syncro.domain.model.CumulativeSpend
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.label
import androidx.compose.ui.graphics.luminance
import com.syncro.domain.model.BudgetGroup
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Gráficas del desglose de Ahorros, dibujadas a mano en Canvas (sin librerías):
 *  - el donut del reparto 50/30/20 (3 trozos con su color; tocar uno lo destaca),
 *  - el ritmo del mes (lo gastado acumulado día a día frente a lo previsto; arrastrar enseña cada día),
 *  - "¿En qué se va?" (barras de un solo color: la categoría la dicen su icono y su nombre).
 * Trazos finos, huecos de 2 dp entre trozos, ejes discretos y el texto siempre en los colores de
 * texto del tema (el color de la serie va en la marca, no en las cifras). Los colores de los trozos
 * están comprobados con cuentas en ChartPaletteTest (daltonismo, contraste en claro y oscuro).
 */

/**
 * Colores del reparto 50/30/20 (Necesidades, Caprichos, Ahorro), elegidos para cada tema con
 * cuentas (ChartPaletteTest), no invirtiendo los del claro:
 *  - claro: Sky/Violet/Emerald 600 — se distinguen también con daltonismo (ΔE ≥ 8) y se ven sobre blanco;
 *  - oscuro: Sky 600, Violet 500, Emerald 600 — dentro de la banda de luz del oscuro; la peor pareja
 *    con daltonismo queda en 7,5 (zona "mínimo"), que solo vale con apoyo: por eso el donut y la
 *    barra llevan siempre la leyenda con nombre e importe, huecos entre trozos y toque para destacar.
 */
internal data class BudgetChartPalette(val needs: Color, val wants: Color, val savings: Color) {
    fun of(group: BudgetGroup): Color = when (group) {
        BudgetGroup.NEEDS -> needs
        BudgetGroup.WANTS -> wants
    }
}

internal val LightBudgetChartPalette = BudgetChartPalette(
    needs = Color(0xFF0284C7), // Sky 600
    wants = Color(0xFF7C3AED), // Violet 600
    savings = Color(0xFF059669) // Emerald 600
)

internal val DarkBudgetChartPalette = BudgetChartPalette(
    needs = Color(0xFF0284C7), // Sky 600
    wants = Color(0xFF8B5CF6), // Violet 500
    savings = Color(0xFF059669) // Emerald 600
)

/** La paleta del tema que se ve (el de la app, no el del sistema: puede estar forzado en Ajustes). */
internal val budgetChartPalette: BudgetChartPalette
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkBudgetChartPalette else LightBudgetChartPalette

// region Donut del reparto

private data class DonutSlice(val label: String, val cents: Long, val color: Color)

/**
 * El reparto del mes en un donut: lo gastado en Necesidades y Caprichos y lo ahorrado, sobre todo
 * lo que ha entrado (lo que falta del círculo es lo que aún queda sin gastar). En el centro, lo
 * gastado; tocar un trozo enseña el suyo. La leyenda va aparte ([BudgetStackLegend]).
 */
@Composable
internal fun SpendingDonut(plan: BudgetPlan, modifier: Modifier = Modifier) {
    val palette = budgetChartPalette
    val slices = plan.groups.map { DonutSlice(it.group.shortName, it.spentCents, palette.of(it.group)) } +
        listOfNotNull(plan.savings?.takeIf { it.savedCents > 0 }?.let { DonutSlice("Ahorro", it.savedCents, palette.savings) })
    val spent = plan.groups.sumOf { it.spentCents }
    // El círculo entero es lo que ha entrado; si se gastó más, lo gastado (el donut no se sale)
    val whole = maxOf(plan.base?.cents ?: 0L, slices.sumOf { it.cents }, 1L)
    var selected by remember(plan) { mutableStateOf<Int?>(null) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

    // Se dibuja girando al entrar (y al cambiar de mes)
    val reveal = remember(plan) { Animatable(0f) }
    LaunchedEffect(plan) { reveal.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing)) }

    val description = buildString {
        append("Reparto del mes: ")
        append(slices.joinToString { "${it.label} ${formatEuros(it.cents)}" })
        plan.base?.let { append(", de ${formatEuros(it.cents)} que han entrado") }
    }

    Box(
        modifier = modifier
            .size(DONUT_SIZE)
            .semantics { contentDescription = description }
            .pointerInput(slices, whole) {
                detectTapGestures { tap ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val distance = hypot(tap.x - center.x, tap.y - center.y)
                    val outer = min(size.width, size.height) / 2f
                    if (distance < outer - DONUT_STROKE.toPx() * 1.6f || distance > outer) {
                        selected = null
                        return@detectTapGestures
                    }
                    // Ángulo desde las 12 en punto, en el sentido de las agujas del reloj
                    val angle = ((atan2(tap.y - center.y, tap.x - center.x) * 180 / PI + 90 + 360) % 360).toFloat()
                    var start = 0f
                    val hit = slices.indexOfFirst { slice ->
                        val sweep = 360f * slice.cents / whole
                        (angle >= start && angle < start + sweep).also { start += sweep }
                    }
                    selected = hit.takeIf { it >= 0 && it != selected }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = DONUT_STROKE.toPx()
            // Sitio para que el trozo destacado engorde sin salirse
            val inset = strokePx / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokePx))

            // 2 dp de hueco entre trozos, pasados a grados a este radio
            val gap = (2.dp.toPx() / (arcSize.width / 2) * 180 / PI).toFloat()
            var start = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = 360f * slice.cents / whole * reveal.value
                if (sweep > gap) {
                    val isSelected = selected == index
                    drawArc(
                        color = slice.color.copy(alpha = if (selected == null || isSelected) 1f else 0.3f),
                        startAngle = start + gap / 2,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(if (isSelected) strokePx + 6.dp.toPx() else strokePx)
                    )
                }
                start += sweep
            }
        }

        // En el centro, la cifra: lo gastado, o el trozo tocado
        val slice = selected?.let { slices.getOrNull(it) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                slice?.label ?: "Gastado",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatEuros(slice?.cents ?: spent),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            val of = plan.base?.cents
            Text(
                when {
                    of == null -> "este mes"
                    slice != null -> "${(slice.cents * 100 / of).toInt()} % de lo que entró"
                    else -> "de ${formatEuros(of)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val DONUT_SIZE = 184.dp
private val DONUT_STROKE = 20.dp

// endregion

// region Ritmo del mes

/**
 * Lo gastado acumulado día a día: la línea llena hasta hoy y discontinua desde hoy hasta fin de mes
 * (lo previsto que ya está comprometido), con lo que se puede gastar según el 50/30/20 como línea de
 * referencia. Tocar o arrastrar enseña el día y lo que se llevaba gastado.
 */
@Composable
internal fun SpendingPaceChart(
    points: List<CumulativeSpend>,
    limitCents: Long?,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val line = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val textOnTooltip = MaterialTheme.colorScheme.inverseOnSurface
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    var selected by remember(points) { mutableStateOf<Int?>(null) }

    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, tween(durationMillis = 800, easing = FastOutSlowInEasing)) }

    // Hasta qué día es real (hoy, o todo el mes si ya pasó); lo de después es previsto
    val todayIndex = points.indexOfLast { !it.date.isAfter(today) }
    val total = points.last().cents
    val yMax = (maxOf(total, limitCents ?: 0L, 1L) * 1.15f)

    val description = buildString {
        append("Ritmo del mes: ")
        points.getOrNull(todayIndex)?.let { append("llevas ${formatEuros(it.cents)} gastados") } ?: append("aún no ha empezado")
        if (total != points.getOrNull(todayIndex)?.cents) append(", ${formatEuros(total)} con lo previsto")
        limitCents?.let { append(", de ${formatEuros(it)} para gastar") }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(PACE_HEIGHT)
            .semantics { contentDescription = description }
            // Tocar o arrastrar: el día bajo el dedo. Al soltar se queda; otro toque en el mismo día lo quita.
            // No se consume el gesto, así que deslizar en vertical sigue moviendo la lista
            .pointerInput(points) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val left = PACE_PAD_START.toPx()
                    val width = size.width - left - PACE_PAD_END.toPx()
                    fun indexAt(x: Float) = (((x - left) / width) * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                    val first = indexAt(down.position.x)
                    val wasSelected = selected == first
                    selected = first
                    var moved = false
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            val index = indexAt(change.position.x)
                            if (index != first) moved = true
                            selected = index
                        }
                    } while (event.changes.any { it.pressed })
                    if (!moved && wasSelected) selected = null
                }
            }
    ) {
        val left = PACE_PAD_START.toPx()
        val top = PACE_PAD_TOP.toPx()
        val bottom = size.height - PACE_PAD_BOTTOM.toPx()
        val width = size.width - left - PACE_PAD_END.toPx()
        fun x(index: Int) = left + width * index / (points.size - 1).coerceAtLeast(1)
        fun y(cents: Long) = bottom - (bottom - top) * (cents / yMax)

        // Base y referencia: discretas
        drawLine(grid, Offset(left, bottom), Offset(left + width, bottom), 1.dp.toPx())
        limitCents?.let { limit ->
            val ly = y(limit)
            drawLine(
                muted.copy(alpha = 0.6f), Offset(left, ly), Offset(left + width, ly), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            )
            val text = measurer.measure("Para gastar ${formatEuros(limit)}", labelStyle.copy(color = muted))
            drawText(text, topLeft = Offset(left + width - text.size.width, ly - text.size.height - 2.dp.toPx()))
        }

        // La línea se va dibujando de izquierda a derecha al entrar
        val visibleUntil = ((points.size - 1) * reveal.value).roundToInt()
        val solidEnd = minOf(todayIndex, visibleUntil)
        if (solidEnd >= 0) {
            val solid = Path().apply {
                moveTo(x(0), y(points[0].cents))
                for (i in 1..solidEnd) lineTo(x(i), y(points[i].cents))
            }
            // Relleno suave bajo lo ya gastado
            val area = Path().apply {
                addPath(solid)
                lineTo(x(solidEnd), bottom)
                lineTo(x(0), bottom)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.22f), line.copy(alpha = 0f)), startY = top, endY = bottom))
            drawPath(solid, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        if (visibleUntil > maxOf(todayIndex, 0)) {
            val from = maxOf(todayIndex, 0)
            val planned = Path().apply {
                moveTo(x(from), y(points[from].cents))
                for (i in from + 1..visibleUntil) lineTo(x(i), y(points[i].cents))
            }
            drawPath(
                planned, line.copy(alpha = 0.55f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
            )
        }

        // Hoy: un punto con aro del color de la tarjeta, como el "Ahora" del timeline
        if (todayIndex in points.indices && todayIndex < points.size - 1 && reveal.value >= 1f) {
            val center = Offset(x(todayIndex), y(points[todayIndex].cents))
            drawCircle(surface, 6.dp.toPx(), center)
            drawCircle(line, 4.dp.toPx(), center)
        }

        // Fechas abajo: el primer día, hoy y el último
        val dateStyle = labelStyle.copy(color = muted)
        val firstLabel = measurer.measure(points.first().date.shortDay(), dateStyle)
        drawText(firstLabel, topLeft = Offset(left, bottom + 6.dp.toPx()))
        val lastLabel = measurer.measure(points.last().date.shortDay(), dateStyle)
        drawText(lastLabel, topLeft = Offset(left + width - lastLabel.size.width, bottom + 6.dp.toPx()))
        if (todayIndex in 1 until points.size - 1) {
            val todayLabel = measurer.measure("Hoy", dateStyle.copy(fontWeight = FontWeight.Bold))
            val tx = (x(todayIndex) - todayLabel.size.width / 2).coerceIn(left + firstLabel.size.width + 4.dp.toPx(), left + width - lastLabel.size.width - todayLabel.size.width - 4.dp.toPx())
            drawText(todayLabel, topLeft = Offset(tx, bottom + 6.dp.toPx()))
        }

        // El día tocado: línea vertical, punto y etiqueta con la fecha y lo gastado hasta entonces
        selected?.let { index ->
            val point = points[index]
            val px = x(index)
            val py = y(point.cents)
            drawLine(muted.copy(alpha = 0.5f), Offset(px, top), Offset(px, bottom), 1.dp.toPx())
            drawCircle(surface, 7.dp.toPx(), Offset(px, py))
            drawCircle(line, 5.dp.toPx(), Offset(px, py))
            val previsto = if (index > todayIndex) " · previsto" else ""
            val text = measurer.measure("${point.date.shortDay()} · ${formatEuros(point.cents)}$previsto", labelStyle.copy(color = textOnTooltip, fontWeight = FontWeight.SemiBold))
            val padH = 8.dp.toPx()
            val padV = 4.dp.toPx()
            val boxWidth = text.size.width + padH * 2
            val boxHeight = text.size.height + padV * 2
            val bx = (px - boxWidth / 2).coerceIn(0f, size.width - boxWidth)
            val by = (py - boxHeight - 12.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(tooltipBackground, Offset(bx, by), Size(boxWidth, boxHeight), CornerRadius(8.dp.toPx()))
            drawText(text, topLeft = Offset(bx + padH, by + padV))
        }
    }
}

private val PACE_HEIGHT = 190.dp
private val PACE_PAD_START = 4.dp
private val PACE_PAD_END = 4.dp
private val PACE_PAD_TOP = 28.dp
private val PACE_PAD_BOTTOM = 24.dp

// endregion

// region ¿En qué se va?

/**
 * Una fila por categoría, de mayor a menor gasto: su icono, el nombre, una barra fina (todas del
 * mismo color: la categoría la dicen el icono y el nombre) y lo gastado con su %. Tocar una abre su
 * presupuesto ("Otros" junta varias y no se toca).
 */
@Composable
internal fun ExpenseShareBars(shares: List<CategoryShare>, onCategoryClick: (MovementCategory) -> Unit) {
    val max = shares.maxOfOrNull { it.cents }?.coerceAtLeast(1L) ?: return
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    var revealed by remember(shares) { mutableStateOf(false) }
    LaunchedEffect(shares) { revealed = true }

    Column {
        shares.forEach { share ->
            val category = share.category
            val fraction by animateFloatAsState(
                targetValue = if (revealed) share.cents.toFloat() / max else 0f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                label = "shareBar"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (category != null) Modifier.clickable { onCategoryClick(category) } else Modifier)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tint = category?.color ?: MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(category?.icon ?: Icons.Rounded.MoreHoriz, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            category?.label ?: "Otros",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            formatEuros(share.cents),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(track)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(bar)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${share.percent} %",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(min = 36.dp)
                        )
                    }
                }
            }
        }
    }
}

// endregion
