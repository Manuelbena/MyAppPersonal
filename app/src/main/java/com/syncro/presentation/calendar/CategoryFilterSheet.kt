package com.syncro.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syncro.presentation.theme.toColor

/**
 * Qué categorías de evento se ven en el Calendario: un interruptor por cada una de las del mes
 * (y las ocultas, para poder volver a mostrarlas). Sirve para desconectar, p. ej. sin Trabajo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterSheet(
    categories: List<CategoryOption>,
    hidden: Set<String>,
    onToggle: (category: String, hidden: Boolean) -> Unit,
    onShowAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        CategoryFilterContent(categories, hidden, onToggle, onShowAll)
    }
}

@Composable
fun CategoryFilterContent(
    categories: List<CategoryOption>,
    hidden: Set<String>,
    onToggle: (category: String, hidden: Boolean) -> Unit,
    onShowAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Mostrar en el calendario",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (hidden.isNotEmpty()) {
                TextButton(onClick = onShowAll) { Text("Mostrar todas") }
            }
        }
        Text(
            "Las tareas se ven siempre.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (categories.isEmpty()) {
            Text(
                "Este mes no hay eventos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        categories.forEach { category ->
            val isVisible = category.name !in hidden
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onToggle(category.name, isVisible) }
                    .heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(category.color.toColor().copy(alpha = if (isVisible) 1f else 0.35f))
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = isVisible, onCheckedChange = { visible -> onToggle(category.name, !visible) })
            }
        }
    }
}
