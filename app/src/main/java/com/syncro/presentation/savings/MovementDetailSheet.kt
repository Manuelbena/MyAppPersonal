package com.syncro.presentation.savings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.label
import com.syncro.domain.model.MovementOccurrence
import com.syncro.domain.model.MovementType
import com.syncro.presentation.components.DeleteItemButton
import com.syncro.presentation.components.DetailCard
import com.syncro.presentation.components.DetailHeader
import com.syncro.presentation.components.GradientSheetInsets
import com.syncro.presentation.components.InfoDivider
import com.syncro.presentation.components.InfoRow
import com.syncro.presentation.components.Pill
import com.syncro.presentation.components.QuickAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementDetailSheet(
    occurrence: MovementOccurrence,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
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
        MovementDetailContent(occurrence, onEdit = onEdit, onShare = onShare, onDelete = onDelete)
    }
}

/**
 * Detalle de un ingreso o gasto, con el mismo estilo que el de un evento: el importe como título
 * sobre el color de su categoría, sus datos y las acciones (editar, compartir el ticket, borrar).
 */
@Composable
fun MovementDetailContent(
    occurrence: MovementOccurrence,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val movement = occurrence.movement
    val accent = movement.category.color
    val isIncome = movement.type == MovementType.INCOME

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        DetailHeader(
            accent = accent,
            title = formatSignedEuros(movement.amountCents, movement.type),
            isCompleted = false,
            pills = {
                Pill(
                    if (isIncome) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                    if (isIncome) "Ingreso" else "Gasto",
                    movement.type.color
                )
                Pill(movement.category.icon, movement.category.label, accent)
                if (movement.repeatsMonthly) Pill(Icons.Rounded.EventRepeat, "Cada mes", accent)
            },
            actions = {
                QuickAction(Icons.Rounded.Edit, "Editar", accent, filled = true, onClick = onEdit, modifier = Modifier.weight(1f))
                QuickAction(Icons.Rounded.Share, "Compartir", accent, onClick = onShare, modifier = Modifier.weight(1f))
            }
        )

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            DetailCard {
                InfoRow(
                    Icons.Rounded.Event,
                    accent,
                    "Fecha",
                    occurrence.date.toDetailDate(),
                    // En los mensuales, desde cuándo se repite
                    if (movement.repeatsMonthly) "Cada mes desde el ${movement.date.format(SINCE)}" else null
                )
                movement.note?.let { note ->
                    InfoDivider()
                    InfoRow(Icons.AutoMirrored.Rounded.Notes, accent, "Concepto", note)
                }
            }

            // Igual que el estado de sincronización de eventos y tareas, pero esto nunca sale del móvil
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Guardado solo en este móvil",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DeleteItemButton(
                label = if (isIncome) "Eliminar ingreso" else "Eliminar gasto",
                confirmTitle = if (isIncome) "¿Eliminar este ingreso?" else "¿Eliminar este gasto?",
                warning = if (movement.repeatsMonthly) "Se repite cada mes: se quitará de todos los meses. No se puede deshacer."
                else "No se puede deshacer.",
                onDelete = onDelete
            )
        }
    }
}

private val SPANISH = Locale("es", "ES")
private val DETAIL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", SPANISH)
private val SINCE: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SPANISH)

private fun LocalDate.toDetailDate(): String = format(DETAIL_DATE).replaceFirstChar { it.uppercase() }
