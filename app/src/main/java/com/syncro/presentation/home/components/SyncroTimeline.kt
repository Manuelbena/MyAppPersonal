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
    onClick: () -> Unit = {}
) {
    var isSubtasksExpanded by remember { mutableStateOf(false) }
    // Verde si está completado, si no el color de la categoría (igual que en el detalle)
    val accent = if (event.isCompleted) Emerald500 else event.categoryColor.toColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        TimeColumn(
            isAllDay = event.isAllDay,
            start = event.startTime,
            end = event.endTime,
            extraDays = java.time.temporal.ChronoUnit.DAYS.between(event.date, event.endDate),
            modifier = Modifier.padding(top = 16.dp)
        )

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
        TimeColumn(isAllDay = task.isAllDay, start = task.time)

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

/**
 * Columna de la izquierda del timeline, común a tareas y eventos: "Todo el día" o la hora de
 * inicio y, si la hay, la de fin. Si el evento termina otro día se indica debajo ("+1 día").
 */
@Composable
private fun TimeColumn(
    isAllDay: Boolean,
    start: java.time.LocalTime,
    end: java.time.LocalTime? = null,
    extraDays: Long = 0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isAllDay) {
            Text("Todo el", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
            Text("día", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
        } else {
            Text(start.toDisplayTime(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            end?.let {
                Text(it.toDisplayTime(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (extraDays > 0) {
                Text(
                    text = if (extraDays == 1L) "+1 día" else "+$extraDays días",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
