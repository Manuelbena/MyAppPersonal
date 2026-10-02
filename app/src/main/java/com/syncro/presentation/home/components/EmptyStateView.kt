package com.syncro.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Lo que dice el día vacío según sea pasado, hoy o futuro. */
internal data class EmptyDayMessage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    /** En un día pasado no se ofrece crear nada: las tareas nuevas irían a hoy, no a ese día. */
    val canAdd: Boolean
)

internal fun emptyDayMessage(date: LocalDate, today: LocalDate): EmptyDayMessage = when {
    date.isBefore(today) -> EmptyDayMessage(
        icon = Icons.Outlined.History,
        title = "Un día tranquilo",
        body = "No hubo tareas ni eventos este día.",
        canAdd = false
    )
    date == today -> EmptyDayMessage(
        icon = Icons.Filled.Coffee,
        title = "Un día despejado",
        body = "No tienes tareas ni eventos para hoy. ¡Disfruta de tu tiempo libre!",
        canAdd = true
    )
    else -> EmptyDayMessage(
        icon = Icons.Outlined.EventAvailable,
        title = "Nada planeado todavía",
        body = "Este día está libre. ¿Le apuntas algo?",
        canAdd = true
    )
}

/**
 * El día que se mira no tiene tareas ni eventos. Hoy y los días futuros ofrecen crear una tarea
 * o un evento directamente en ese día; los pasados solo lo cuentan.
 */
@Composable
fun EmptyStateView(
    date: LocalDate,
    today: LocalDate,
    onAddTask: () -> Unit,
    onAddEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = emptyDayMessage(date, today)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = message.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = message.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message.body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 48.dp)
        )

        if (message.canAdd) {
            Spacer(modifier = Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EmptyDayButton("Nueva tarea", onAddTask)
                EmptyDayButton("Nuevo evento", onAddEvent)
            }
        }
    }
}

@Composable
private fun EmptyDayButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        modifier = Modifier.height(48.dp)
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}
