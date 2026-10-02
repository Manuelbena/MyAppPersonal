package com.syncro.presentation.task

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
import com.syncro.presentation.theme.Emerald500
import com.syncro.presentation.theme.toColor

/** Detalle de una tarea, con el mismo estilo que el de los eventos (ver `EventDetailSheet`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: SyncroItem.Task,
    onDismiss: () -> Unit,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit
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
        TaskDetailContent(task = task, onToggleCompleted = onToggleCompleted, onDelete = onDelete)
    }
}

@Composable
fun TaskDetailContent(
    task: SyncroItem.Task,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit
) {
    val taskColor = task.categoryColor?.toColor() ?: MaterialTheme.colorScheme.primary
    val accent = if (task.isCompleted) Emerald500 else taskColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        DetailHeader(
            accent = accent,
            title = task.title,
            isCompleted = task.isCompleted,
            pills = {
                Pill(Icons.Rounded.TaskAlt, "Tarea", taskColor)
                task.categoryText?.takeIf { it.isNotBlank() }?.let { Pill(categoryIcon(it), it, taskColor) }
                if (task.isCompleted) Pill(Icons.Rounded.CheckCircle, "Completada", Emerald500)
            },
            actions = {
                QuickAction(
                    icon = if (task.isCompleted) Icons.Rounded.RestartAlt else Icons.Rounded.CheckCircleOutline,
                    label = if (task.isCompleted) "Marcar como pendiente" else "Completar tarea",
                    color = accent,
                    filled = true,
                    onClick = onToggleCompleted,
                    modifier = Modifier.weight(1f)
                )
            }
        )

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            DetailCard {
                InfoRow(
                    icon = Icons.Rounded.Schedule,
                    accent = accent,
                    label = "Cuándo",
                    headline = task.date.toLongDisplayDate(),
                    detail = if (task.isAllDay) "Todo el día" else task.time.toDisplayTime()
                )
            }

            if (!task.description.isNullOrBlank()) {
                Column {
                    SectionTitle(Icons.AutoMirrored.Rounded.Notes, "Descripción")
                    DetailCard { DetailText(task.description) }
                }
            }

            SyncStatus(isSynced = task.remoteId != null)

            DeleteItemButton(
                label = "Eliminar tarea",
                confirmTitle = "¿Eliminar esta tarea?",
                warning = deleteWarning(isSynced = task.remoteId != null, googleService = "Google Tasks"),
                onDelete = onDelete
            )
        }
    }
}
