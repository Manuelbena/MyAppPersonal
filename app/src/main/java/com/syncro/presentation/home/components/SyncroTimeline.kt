package com.syncro.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncro.domain.model.Subtask
import com.syncro.domain.model.FreeGap
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.components.SubtaskProgress
import com.syncro.presentation.components.toDisplayTime
import com.syncro.presentation.theme.Emerald500
import com.syncro.presentation.theme.color
import com.syncro.presentation.theme.label
import com.syncro.presentation.theme.toColor
import com.syncro.presentation.event.formatDuration
import com.syncro.presentation.home.minutesLeftAt
import com.syncro.presentation.home.progressAt
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import com.syncro.domain.model.DayWindow
import com.syncro.presentation.home.TimeAnchor
import com.syncro.presentation.home.dayBoundaryIndices
import com.syncro.presentation.home.endMinuteOn
import com.syncro.presentation.home.nowLineY
import com.syncro.presentation.home.startMinuteOn
import java.time.temporal.ChronoUnit

/**
 * Tarjeta de un evento en el timeline. Muestra lo esencial (título, lugar, descripción corta,
 * categoría y progreso de subtareas); tocarla abre el detalle completo, con editar y compartir.
 */
@Composable
fun EventCard(
    event: SyncroItem.Event,
    onSubtaskToggle: (String) -> Unit,
    onToggleEvent: () -> Unit = {},
    onClick: () -> Unit = {},
    // Hora actual para el progreso del raíl; null = sin progreso (solo el trazo)
    now: LocalDateTime? = null
) {
    var isSubtasksExpanded by remember { mutableStateOf(false) }
    // Verde si está completado, si no el color de la categoría (igual que en el detalle)
    val accent = if (event.isCompleted) Emerald500 else event.categoryColor.toColor()
    val minutesLeft = now?.let { event.minutesLeftAt(it) }

    // IntrinsicSize.Min: el raíl de la izquierda se estira a la altura de la tarjeta
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 6.dp)
    ) {
        EventTimeRail(
            event = event,
            accent = accent,
            progress = now?.let { event.progressAt(it) } ?: 0f
        )
        Spacer(Modifier.width(RAIL_GAP))

        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            // Mismo acabado que TasksCard: borde y degradado vertical del color del evento
            border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.12f), Color.Transparent)))
                    .height(IntrinsicSize.Min)
                    .padding(14.dp)
            ) {
                // Barra de color redondeada
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                )
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = EVENT_TITLE_SIZE,
                        lineHeight = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (event.isCompleted) 0.6f else 1f),
                        textDecoration = if (event.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )

                    if (minutesLeft != null) {
                        Text(
                            text = "En curso · quedan ${formatDuration(minutesLeft)}",
                            fontSize = EVENT_DETAIL_SIZE,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (!event.location.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = event.location,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!event.description.isNullOrBlank()) {
                        Text(
                            text = event.description,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    EventMeta(event)

                    if (event.subtasks.isNotEmpty()) {
                        val done = event.subtasks.count { it.isCompleted }
                        // Resumen de subtareas; al tocarlo se despliegan para marcarlas aquí mismo
                        Row(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isSubtasksExpanded = !isSubtasksExpanded }
                                .heightIn(min = MIN_TOUCH_TARGET),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SubtaskProgress(done, event.subtasks.size, accent, Modifier.weight(1f))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "$done/${event.subtasks.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            Icon(
                                imageVector = if (isSubtasksExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = if (isSubtasksExpanded) "Ocultar subtareas" else "Ver subtareas",
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        AnimatedVisibility(visible = isSubtasksExpanded) {
                            Column(Modifier.padding(top = 4.dp)) {
                                event.subtasks.forEach { subtask ->
                                    SubtaskRow(
                                        subtask = subtask,
                                        color = accent,
                                        onClick = { onSubtaskToggle(subtask.title) },
                                        maxLines = 1,
                                        fontSize = EVENT_DETAIL_SIZE,
                                        iconSize = 16.dp
                                    )
                                }
                            }
                        }
                    }
                }

                // El área táctil (48 dp) se sale hacia el margen para que el círculo quede alineado
                // con la esquina de la tarjeta como si midiera solo lo que se ve
                CompletionToggle(
                    isCompleted = event.isCompleted,
                    uncheckedTint = accent.copy(alpha = 0.6f),
                    contentDescription = "Completar evento",
                    onToggle = onToggleEvent,
                    modifier = Modifier.offset(x = 11.dp, y = (-11).dp)
                )
            }
        }
    }
}

/**
 * Categoría y prioridad del evento en una línea discreta (punto de color + texto pequeño) en vez de
 * píldoras, para que no compitan con el título. Las píldoras quedan para el detalle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventMeta(event: SyncroItem.Event) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // FlowRow: en tarjetas estrechas (diálogo del Calendario) la prioridad baja de línea
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(event.categoryColor.toColor())
            )
            Spacer(Modifier.width(6.dp))
            MetaText(event.categoryText, muted)
        }
        event.priority?.let { priority ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Flag,
                    contentDescription = null,
                    tint = priority.color,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(4.dp))
                MetaText(priority.label, muted)
            }
        }
    }
}

@Composable
private fun MetaText(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SubtaskRow(
    subtask: Subtask,
    color: Color,
    onClick: () -> Unit,
    // En la tarjeta cabe una línea; en el detalle se lee entera
    maxLines: Int = Int.MAX_VALUE,
    // En la tarjeta del timeline, del tamaño de "En curso" (con el círculo más pequeño a juego)
    fontSize: TextUnit = 14.sp,
    iconSize: Dp = 20.dp
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .heightIn(min = MIN_TOUCH_TARGET)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (subtask.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (subtask.isCompleted) Emerald500 else color.copy(alpha = 0.5f),
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.width(iconSize * 0.6f))
        Text(
            text = subtask.title,
            fontSize = fontSize,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            color = if (subtask.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}

/**
 * Fila de una tarea en el timeline. Relleno suave y sin borde ni barra de color, para que nunca
 * se confunda con un evento.
 */
@Composable
fun TaskRow(
    task: SyncroItem.Task,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskTimeRail(task)
        Spacer(Modifier.width(RAIL_GAP))

        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = if (task.isCompleted) Emerald500.copy(alpha = 0.08f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)
        ) {
            // El botón de completar ya mide 48 dp, así que da el alto mínimo de la fila (56 dp con el margen)
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompletionToggle(
                    isCompleted = task.isCompleted,
                    uncheckedTint = task.categoryColor?.toColor() ?: MaterialTheme.colorScheme.outline,
                    contentDescription = "Completar",
                    onToggle = onToggle
                )

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (task.isCompleted) 0.6f else 1f),
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )

                    if (!task.description.isNullOrBlank()) {
                        Text(
                            text = task.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private val EVENT_TITLE_SIZE = 17.sp
/** Texto pequeño de la tarjeta: "En curso · quedan…" y las subtareas desplegadas. */
private val EVENT_DETAIL_SIZE = 12.sp

/** Área táctil mínima de Material (48 dp) para todo lo que se pulsa. */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * Círculo para completar un evento o una tarea: verde con check cuando está hecho. Se ve de 26 dp
 * pero, como IconButton, responde en 48 dp.
 */
@Composable
private fun CompletionToggle(
    isCompleted: Boolean,
    uncheckedTint: Color,
    contentDescription: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onToggle, modifier = modifier.size(MIN_TOUCH_TARGET)) {
        Icon(
            imageVector = if (isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = contentDescription,
            tint = if (isCompleted) Emerald500 else uncheckedTint,
            modifier = Modifier.size(26.dp)
        )
    }
}

// region Raíl de tiempo

/*
 * La columna izquierda del timeline es un raíl: las horas a la izquierda y, al lado, una línea del
 * color del evento que va de su inicio (punto) a su fin (anillo). La parte ya transcurrida se
 * rellena, así el día se lee de un vistazo: lo pasado lleno, lo que viene vacío. Las tareas no
 * duran, así que solo llevan un punto. La línea "Ahora" usa las mismas columnas para alinearse.
 */

/** Ancho mínimo de la columna de horas; si la hora no cabe (fuente, letra grande del sistema) se ensancha. */
private val MIN_TIME_LABEL_WIDTH = 48.dp
/** Números de ancho fijo: todas las horas miden lo mismo y quedan alineadas en columna. */
private const val TABULAR_NUMBERS = "tnum"
private val RAIL_WIDTH = 20.dp
private val RAIL_GAP = 8.dp
/** Distancia del borde de la tarjeta al centro de la primera/última línea de hora. */
private val RAIL_END_INSET = 24.dp

@Composable
private fun EventTimeRail(event: SyncroItem.Event, accent: Color, progress: Float) {
    val extraDays = ChronoUnit.DAYS.between(event.date, event.endDate)
    val surface = MaterialTheme.colorScheme.background

    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(timeLabelWidth())
                .fillMaxHeight()
                .padding(vertical = RAIL_END_INSET - RAIL_LABEL_SLOT / 2),
            horizontalAlignment = Alignment.End
        ) {
            if (event.isAllDay) {
                TimeLabel("Todo el", bold = true, small = true)
                TimeLabel("día", bold = true, small = true)
                if (extraDays > 0) TimeLabel("${extraDays + 1} días", small = true)
            } else {
                RailMarkLabel { TimeLabel(event.startTime.toDisplayTime(), bold = true) }
                Spacer(Modifier.weight(1f))
                // "+1 día" encima de la hora de fin, para que esta quede alineada con el anillo
                if (extraDays > 0) {
                    Text(
                        text = if (extraDays == 1L) "+1 día" else "+$extraDays días",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                RailMarkLabel { TimeLabel(event.endTime.toDisplayTime()) }
            }
        }

        Canvas(
            modifier = Modifier
                .width(RAIL_WIDTH)
                .fillMaxHeight()
        ) {
            val x = size.width / 2
            val top = RAIL_END_INSET.toPx()
            val stroke = 3.dp.toPx()
            if (event.isAllDay) {
                // Todo el día: la línea se desvanece hacia abajo, no tiene un fin concreto
                drawLine(
                    brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent), startY = top, endY = size.height),
                    start = Offset(x, top),
                    end = Offset(x, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            } else {
                val bottom = maxOf(size.height - RAIL_END_INSET.toPx(), top + 8.dp.toPx())
                drawLine(accent.copy(alpha = 0.25f), Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round)
                if (progress > 0f) {
                    drawLine(accent, Offset(x, top), Offset(x, top + (bottom - top) * progress), stroke, StrokeCap.Round)
                }
                // Anillo del fin: relleno con el fondo para que la línea no lo atraviese
                val ringRadius = 4.5.dp.toPx()
                drawCircle(if (progress >= 1f) accent else surface, ringRadius, Offset(x, bottom))
                drawCircle(accent, ringRadius, Offset(x, bottom), style = Stroke(2.dp.toPx()))
            }
            drawCircle(accent, 5.dp.toPx(), Offset(x, top))
        }
    }
}

@Composable
private fun TaskTimeRail(task: SyncroItem.Task) {
    val color = if (task.isCompleted) Emerald500 else task.categoryColor?.toColor() ?: MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.background
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(timeLabelWidth()), horizontalAlignment = Alignment.End) {
            if (task.isAllDay) {
                TimeLabel("Todo el", bold = true, small = true)
                TimeLabel("día", bold = true, small = true)
            } else {
                TimeLabel(task.time.toDisplayTime(), bold = true)
            }
        }
        // Punto pequeño: hueco si está pendiente, relleno si está hecha
        Canvas(modifier = Modifier.size(RAIL_WIDTH)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = 4.dp.toPx()
            drawCircle(if (task.isCompleted) color else surface, radius, center)
            drawCircle(color, radius, center, style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * Ancho de la columna de horas: el de "00:00" en negrita (la etiqueta más ancha) medido con la fuente
 * y la escala de letra actuales. Regresión: con un ancho fijo, "14:00" salía cortado como "14:0".
 */
@Composable
private fun timeLabelWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.merge(
        TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_NUMBERS)
    )
    val density = LocalDensity.current
    val measured = remember(style, density) {
        with(density) { measurer.measure("00:00", style, maxLines = 1).size.width.toDp() }
    }
    return maxOf(MIN_TIME_LABEL_WIDTH, measured + 2.dp)
}

/**
 * Estilo de las horas: números de ancho fijo y sin el interlineado de la fuente (alto de línea =
 * tamaño, recortado por arriba y por abajo), para que el centro del texto sea el centro de los
 * dígitos y quede alineado con los puntos del raíl y centrado en la píldora de "Ahora".
 */
@Composable
private fun timeTextStyle(size: TextUnit): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        fontSize = size,
        lineHeight = size,
        fontFeatureSettings = TABULAR_NUMBERS,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
    )
)

/** Alto de la franja de cada hora del raíl; su centro cae a [RAIL_END_INSET] del borde, como el punto o el anillo. */
private val RAIL_LABEL_SLOT = 20.dp

/** Centra la hora en su franja aunque la letra del sistema sea más grande que la franja. */
@Composable
private fun RailMarkLabel(content: @Composable () -> Unit) {
    Box(Modifier.height(RAIL_LABEL_SLOT), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.wrapContentHeight(unbounded = true)) { content() }
    }
}

@Composable
private fun TimeLabel(text: String, bold: Boolean = false, small: Boolean = false) {
    Text(
        text = text,
        style = timeTextStyle(if (small) 12.sp else if (bold) 14.sp else 12.sp),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = if (bold) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

/**
 * Hueco libre entre dos eventos: una línea discreta en el raíl con cuánto tiempo queda libre y un
 * "+" para llenarlo (abre un evento nuevo en ese hueco).
 */
@Composable
fun FreeGapRow(gap: FreeGap, modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Añadir un evento en este hueco", onClick = onAdd)
            .heightIn(min = MIN_TOUCH_TARGET),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(timeLabelWidth()))
        // Trazo punteado en el raíl: tiempo sin nada
        Canvas(modifier = Modifier.width(RAIL_WIDTH).height(MIN_TOUCH_TARGET)) {
            val x = size.width / 2
            // Tapa la línea continua del timeline para que el hueco se lea punteado
            drawLine(background, Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
            val dash = 3.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(muted.copy(alpha = 0.35f), Offset(x, y), Offset(x, minOf(y + dash, size.height)), 2.dp.toPx(), StrokeCap.Round)
                y += dash * 2.2f
            }
        }
        Spacer(Modifier.width(RAIL_GAP))
        Text(
            "Libre · ${formatDuration(gap.minutes)}",
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = muted,
            modifier = Modifier.padding(end = 14.dp).size(18.dp)
        )
    }
}

/**
 * Línea de la hora actual: la hora en una píldora sobre la columna de horas y, desde el raíl, una
 * línea hasta el borde, como en Google Calendar. Se dibuja por encima de las tarjetas.
 */
@Composable
fun NowIndicator(now: LocalDateTime, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NOW_INDICATOR_HEIGHT)
            .semantics(mergeDescendants = true) { contentDescription = "Ahora, ${now.toLocalTime().toDisplayTime()}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(timeLabelWidth()), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = now.toLocalTime().toDisplayTime(),
                style = timeTextStyle(11.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val y = size.height / 2
            val x = RAIL_WIDTH.toPx() / 2
            // Halo suave debajo para que la línea se lea también sobre el degradado de las tarjetas
            drawLine(color.copy(alpha = 0.18f), Offset(x, y), Offset(size.width, y), 6.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(color.copy(alpha = 0.25f), 9.dp.toPx(), Offset(x, y))
            drawCircle(color, 5.dp.toPx(), Offset(x, y))
        }
    }
}

private val NOW_INDICATOR_HEIGHT = 24.dp

/** Marca de inicio o fin del día en el raíl: la hora, un sol o una luna y una etiqueta discreta. */
@Composable
private fun DayBoundaryRow(time: LocalTime, label: String, isStart: Boolean, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DAY_BOUNDARY_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(timeLabelWidth()), contentAlignment = Alignment.CenterEnd) {
            TimeLabel(time.toDisplayTime())
        }
        Box(
            modifier = Modifier
                .size(RAIL_WIDTH)
                .clip(RoundedCornerShape(50))
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isStart) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = muted.copy(alpha = 0.8f),
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(RAIL_GAP))
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
    }
}

private val DAY_BOUNDARY_HEIGHT = 40.dp

/**
 * El timeline de eventos de un día, de principio a fin: una línea continua en el raíl desde la marca
 * de inicio del día hasta la de fin ([window]; las horas de los resúmenes, o 00:00–23:59), los huecos
 * libres ([gaps], por índice del evento que les sigue) y los eventos. Mirando hoy, la línea "Ahora"
 * va por encima de todo a la altura que le toca según la hora (dentro de la tarjeta del evento en
 * curso si lo hay) y el raíl queda relleno hasta ella.
 */
@Composable
fun DayTimeline(
    events: List<SyncroItem.Event>,
    day: LocalDate,
    now: LocalDateTime,
    window: DayWindow,
    gaps: Map<Int, FreeGap>,
    onAddInGap: (FreeGap) -> Unit,
    onSubtaskToggle: (SyncroItem.Event, String) -> Unit,
    onToggleEvent: (SyncroItem.Event) -> Unit,
    onEventClick: (SyncroItem.Event) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val labelWidth = timeLabelWidth()
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val passedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val isToday = day == now.toLocalDate()
    // Posición (arriba, alto) de cada fila medida en el layout, para colocar la línea "Ahora"
    val bounds = remember(day) { mutableStateMapOf<String, Pair<Float, Float>>() }
    fun Modifier.measured(key: String) = onGloballyPositioned { coordinates ->
        bounds[key] = coordinates.positionInParent().y to coordinates.size.height.toFloat()
    }

    val (startIndex, endIndex) = dayBoundaryIndices(events, day, window)
    val railInset = with(density) { (6.dp + RAIL_END_INSET).toPx() }

    // Anclas hora → altura en el orden en que se ven las filas
    val anchors = buildList {
        fun center(key: String) = bounds[key]?.let { (top, height) -> top + height / 2 }
        fun boundaryAt(index: Int) {
            if (index == startIndex) center(KEY_DAY_START)?.let { add(TimeAnchor(window.start.toSecondOfDay() / 60, it)) }
            if (index == endIndex) center(KEY_DAY_END)?.let { add(TimeAnchor(window.end.toSecondOfDay() / 60, it)) }
        }
        events.forEachIndexed { index, event ->
            boundaryAt(index)
            gaps[index]?.let { gap ->
                bounds["gap$index"]?.let { (top, height) ->
                    add(TimeAnchor(gap.start.toSecondOfDay() / 60, top))
                    add(TimeAnchor(gap.end.toSecondOfDay() / 60, top + height))
                }
            }
            if (!event.isAllDay) {
                bounds["event${event.id}"]?.let { (top, height) ->
                    add(TimeAnchor(event.startMinuteOn(day), top + railInset))
                    add(TimeAnchor(event.endMinuteOn(day), maxOf(top + height - railInset, top + railInset)))
                }
            }
        }
        boundaryAt(events.size)
    }
    val nowMinute = now.toLocalTime().toSecondOfDay() / 60
    val nowY = if (isToday) nowLineY(anchors, nowMinute) else null
    val railTop = bounds[KEY_DAY_START]?.let { (top, height) -> top + height / 2 }
    val railBottom = bounds[KEY_DAY_END]?.let { (top, height) -> top + height / 2 }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (railTop == null || railBottom == null) return@drawBehind
                    val x = labelWidth.toPx() + RAIL_WIDTH.toPx() / 2
                    val top = minOf(railTop, railBottom)
                    val bottom = maxOf(railTop, railBottom)
                    val stroke = 2.dp.toPx()
                    drawLine(railColor, Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round)
                    // Lo que ya pasó del día, relleno
                    val passedUntil = when {
                        day.isBefore(now.toLocalDate()) -> bottom
                        nowY != null -> nowY.coerceIn(top, bottom)
                        else -> null
                    }
                    if (passedUntil != null && passedUntil > top) {
                        drawLine(passedColor, Offset(x, top), Offset(x, passedUntil), stroke, StrokeCap.Round)
                    }
                }
        ) {
            events.forEachIndexed { index, event ->
                if (index == startIndex) {
                    DayBoundaryRow(window.start, "Empieza tu día", isStart = true, modifier = Modifier.measured(KEY_DAY_START))
                }
                if (index == endIndex) {
                    DayBoundaryRow(window.end, "Termina tu día", isStart = false, modifier = Modifier.measured(KEY_DAY_END))
                }
                gaps[index]?.let { gap ->
                    FreeGapRow(gap, modifier = Modifier.measured("gap$index")) { onAddInGap(gap) }
                }
                Box(Modifier.measured("event${event.id}")) {
                    EventCard(
                        event = event,
                        onSubtaskToggle = { onSubtaskToggle(event, it) },
                        onToggleEvent = { onToggleEvent(event) },
                        onClick = { onEventClick(event) },
                        now = now
                    )
                }
            }
            if (startIndex == events.size) {
                DayBoundaryRow(window.start, "Empieza tu día", isStart = true, modifier = Modifier.measured(KEY_DAY_START))
            }
            if (endIndex == events.size) {
                DayBoundaryRow(window.end, "Termina tu día", isStart = false, modifier = Modifier.measured(KEY_DAY_END))
            }
        }

        if (nowY != null) {
            val half = with(density) { NOW_INDICATOR_HEIGHT.toPx() / 2 }
            NowIndicator(
                now = now,
                modifier = Modifier
                    .zIndex(1f)
                    .offset { IntOffset(0, (nowY - half).roundToInt()) }
            )
        }
    }
}

private const val KEY_DAY_START = "dayStart"
private const val KEY_DAY_END = "dayEnd"

// endregion
