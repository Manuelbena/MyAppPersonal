package com.syncro.presentation.event

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.components.*
import com.syncro.presentation.home.components.SubtaskRow
import com.syncro.presentation.theme.Emerald500
import com.syncro.presentation.theme.color
import com.syncro.presentation.theme.label
import com.syncro.presentation.theme.toColor
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Detalle completo de un evento. El sheet se abre a la altura de su contenido: entero si cabe y, si
 * no, a pantalla completa con scroll. Sin estado parcial: hasta un evento sencillo mide algo más de
 * media pantalla y se quedaría cortado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: SyncroItem.Event,
    onDismiss: () -> Unit,
    onToggleCompleted: () -> Unit,
    onSubtaskToggle: (String) -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    // Si se repite: borrar también las siguientes repeticiones
    onDeleteFollowing: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // La raya se dibuja dentro de la cabecera para que el degradado empiece en el borde del sheet
        dragHandle = null,
        // Sin el hueco de arriba de Material: lo pone la cabecera, dentro del degradado
        contentWindowInsets = { GradientSheetInsets },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        EventDetailContent(
            event = event,
            onToggleCompleted = onToggleCompleted,
            onSubtaskToggle = onSubtaskToggle,
            onEdit = onEdit,
            onShare = onShare,
            onDelete = onDelete,
            onDeleteFollowing = onDeleteFollowing
        )
    }
}

@Composable
fun EventDetailContent(
    event: SyncroItem.Event,
    onToggleCompleted: () -> Unit,
    onSubtaskToggle: (String) -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDeleteFollowing: () -> Unit = {}
) {
    // Igual que en la tarjeta: verde si está completado, si no el color de la categoría
    val accent = if (event.isCompleted) Emerald500 else event.categoryColor.toColor()
    val whenText = event.whenText()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        DetailHeader(
            accent = accent,
            title = event.title,
            isCompleted = event.isCompleted,
            pills = {
                Pill(categoryIcon(event.categoryText), event.categoryText, event.categoryColor.toColor())
                event.priority?.let { Pill(Icons.Rounded.Flag, it.label, it.color) }
                event.repeat?.let { Pill(Icons.Rounded.Repeat, it.shortLabel, event.categoryColor.toColor()) }
                if (event.isCompleted) Pill(Icons.Rounded.CheckCircle, "Completado", Emerald500)
            },
            actions = {
                QuickAction(
                    icon = if (event.isCompleted) Icons.Rounded.RestartAlt else Icons.Rounded.CheckCircleOutline,
                    label = if (event.isCompleted) "Pendiente" else "Completar",
                    color = accent,
                    filled = true,
                    onClick = onToggleCompleted,
                    modifier = Modifier.weight(1f)
                )
                QuickAction(Icons.Rounded.Edit, "Editar", accent, onClick = onEdit, modifier = Modifier.weight(1f))
                QuickAction(Icons.Rounded.Share, "Compartir", accent, onClick = onShare, modifier = Modifier.weight(1f))
            }
        )

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            DetailCard {
                InfoRow(Icons.Rounded.Schedule, accent, "Cuándo", whenText.headline, whenText.detail)
                event.repeat?.let { repeat ->
                    InfoDivider()
                    InfoRow(Icons.Rounded.Repeat, accent, "Se repite", repeat.describe(event.date))
                }
                if (!event.location.isNullOrBlank()) {
                    InfoDivider()
                    InfoRow(Icons.Rounded.Place, accent, "Dónde", event.location)
                }
            }

            if (!event.description.isNullOrBlank()) {
                Column {
                    SectionTitle(Icons.AutoMirrored.Rounded.Notes, "Descripción")
                    DetailCard { DetailText(event.description) }
                }
            }

            if (event.subtasks.isNotEmpty()) {
                val done = event.subtasks.count { it.isCompleted }
                Column {
                    SectionTitle(Icons.Rounded.Checklist, "Subtareas", trailing = "$done/${event.subtasks.size}")
                    DetailCard {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            SubtaskProgress(done, event.subtasks.size, accent, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            event.subtasks.forEach { subtask ->
                                SubtaskRow(
                                    subtask = subtask,
                                    color = accent,
                                    onClick = { onSubtaskToggle(subtask.title) }
                                )
                            }
                        }
                    }
                }
            }

            SyncStatus(isSynced = event.remoteId != null)

            DeleteItemButton(
                label = "Eliminar evento",
                confirmTitle = "¿Eliminar este evento?",
                warning = deleteWarning(isSynced = event.remoteId != null, googleService = "Google Calendar"),
                onDelete = onDelete,
                onDeleteFollowing = onDeleteFollowing.takeIf { event.repeat != null },
                feminine = false
            )
        }
    }
}

// region Textos de fecha y duración

internal data class EventWhen(val headline: String, val detail: String)

/**
 * Cuándo ocurre el evento, en dos líneas: el día (o el rango de días) y el horario con su duración.
 * "Sábado, 26 de septiembre" / "10:00 – 11:30 · 1 h 30 min".
 */
internal fun SyncroItem.Event.whenText(): EventWhen {
    val multiDay = endDate != date
    val headline = if (multiDay) {
        "${date.toShortDisplayDate().replaceFirstChar { it.uppercase() }} → ${endDate.toShortDisplayDate()}"
    } else {
        date.toLongDisplayDate()
    }
    if (isAllDay) {
        val days = ChronoUnit.DAYS.between(date, endDate) + 1
        return EventWhen(headline, if (multiDay) "Todo el día · $days días" else "Todo el día")
    }
    val minutes = Duration.between(date.atTime(startTime), endDate.atTime(endTime)).toMinutes()
    val range = "${startTime.toDisplayTime()} – ${endTime.toDisplayTime()}"
    return EventWhen(headline, if (minutes > 0) "$range · ${formatDuration(minutes)}" else range)
}

internal fun formatDuration(minutes: Long): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "$rest min"
        rest == 0L -> "$hours h"
        else -> "$hours h $rest min"
    }
}

// endregion
