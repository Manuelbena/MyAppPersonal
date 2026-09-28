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
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.components.Pill
import com.syncro.presentation.components.SubtaskProgress
import com.syncro.presentation.components.categoryIcon
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
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Tarjeta de un evento en el timeline. Muestra lo esencial (título, lugar, descripción corta,
 * categoría y progreso de subtareas); tocarla abre el detalle completo, con editar y compartir.
 */
@OptIn(ExperimentalLayoutApi::class)
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
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)))
                    .height(IntrinsicSize.Min)
                    .padding(14.dp)
            ) {
                // Barra de color redondeada
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                )
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (event.isCompleted) 0.6f else 1f),
                        textDecoration = if (event.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )

                    if (minutesLeft != null) {
                        Text(
                            text = "En curso · quedan ${formatDuration(minutesLeft)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = event.location,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!event.description.isNullOrBlank()) {
                        Text(
                            text = event.description,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    // FlowRow: en tarjetas estrechas (diálogo del Calendario) la prioridad baja de línea
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Pill(categoryIcon(event.categoryText), event.categoryText, event.categoryColor.toColor(), compact = true)
                        event.priority?.let { Pill(Icons.Rounded.Flag, it.label, it.color, compact = true) }
                    }

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
                                        maxLines = 1
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

@Composable
fun SubtaskRow(
    subtask: Subtask,
    color: Color,
    onClick: () -> Unit,
    // En la tarjeta cabe una línea; en el detalle se lee entera
    maxLines: Int = Int.MAX_VALUE
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
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = subtask.title,
            fontSize = 14.sp,
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

private val TIME_LABEL_WIDTH = 48.dp
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
                .width(TIME_LABEL_WIDTH)
                .fillMaxHeight()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (event.isAllDay) {
                TimeLabel("Todo el", bold = true, small = true)
                TimeLabel("día", bold = true, small = true)
                if (extraDays > 0) TimeLabel("${extraDays + 1} días", small = true)
            } else {
                TimeLabel(event.startTime.toDisplayTime(), bold = true)
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
                TimeLabel(event.endTime.toDisplayTime())
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
        Column(modifier = Modifier.width(TIME_LABEL_WIDTH), horizontalAlignment = Alignment.End) {
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

@Composable
private fun TimeLabel(text: String, bold: Boolean = false, small: Boolean = false) {
    Text(
        text = text,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = if (small) 12.sp else if (bold) 14.sp else 12.sp,
        color = if (bold) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

/**
 * Línea de la hora actual en el timeline de hoy: la hora en la columna de horas y, desde el raíl,
 * una línea hasta el borde, como en Google Calendar.
 */
@Composable
fun NowIndicator(now: LocalDateTime) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Ahora, ${now.toLocalTime().toDisplayTime()}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = now.toLocalTime().toDisplayTime(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(TIME_LABEL_WIDTH)
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val y = size.height / 2
            val x = RAIL_WIDTH.toPx() / 2
            drawLine(color, Offset(x, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(color, 5.dp.toPx(), Offset(x, y))
        }
    }
}

// endregion
