package com.syncro.presentation.notes

import com.syncro.presentation.theme.NotebookFontFamily
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.components.*
import com.syncro.presentation.home.components.formatRelativeTime
import com.syncro.presentation.theme.toColor

/** Detalle de una nota, con el mismo estilo que los de eventos y tareas y el título de libreta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailSheet(
    note: SyncroItem.Note,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // La raya se dibuja dentro de la cabecera para que el degradado empiece en el borde del sheet
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        NoteDetailContent(note = note, onEdit = onEdit, onDelete = onDelete)
    }
}

@Composable
fun NoteDetailContent(
    note: SyncroItem.Note,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val noteColor = note.color.toColor()
    // Borrar no se puede deshacer: el primer toque solo pide confirmación
    var confirmingDelete by remember(note.id) { mutableStateOf(false) }
    val createdAt = note.createdAt

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        DetailHeader(
            accent = noteColor,
            title = note.title,
            isCompleted = false,
            titleStyle = TextStyle(fontFamily = NotebookFontFamily, fontStyle = FontStyle.Italic),
            pills = {
                Pill(Icons.Rounded.Schedule, formatRelativeTime(createdAt).lowercase().replaceFirstChar { it.uppercase() }, MaterialTheme.colorScheme.onSurfaceVariant)
            },
            actions = {
                QuickAction(
                    icon = Icons.Rounded.Edit,
                    label = "Editar",
                    color = noteColor,
                    filled = true,
                    contentColor = Color.Black.copy(alpha = 0.8f),
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                )
                QuickAction(
                    icon = if (confirmingDelete) Icons.Rounded.DeleteForever else Icons.Rounded.DeleteOutline,
                    // Corto para que quepa en una línea en medio botón; el rojo relleno ya avisa
                    label = if (confirmingDelete) "¿Borrar?" else "Borrar",
                    color = MaterialTheme.colorScheme.error,
                    filled = confirmingDelete,
                    onClick = { if (confirmingDelete) onDelete() else confirmingDelete = true },
                    modifier = Modifier.weight(1f)
                )
            }
        )

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (note.content.isNotBlank()) {
                DetailCard { DetailText(note.content) }
            }

            DetailCard {
                InfoRow(
                    icon = Icons.AutoMirrored.Rounded.Notes,
                    accent = noteColor,
                    label = "Creada",
                    headline = createdAt.toLocalDate().toLongDisplayDate(),
                    detail = createdAt.toLocalTime().toDisplayTime()
                )
            }
        }
    }
}
