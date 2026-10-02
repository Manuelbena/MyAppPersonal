package com.syncro.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.NextUp
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.components.SubtaskProgress
import com.syncro.presentation.components.toDisplayTime
import com.syncro.presentation.event.formatDuration
import com.syncro.presentation.home.minutesLeftAt
import com.syncro.presentation.theme.toColor
import java.time.Duration
import java.time.LocalDateTime

/**
 * "Lo próximo", arriba del todo en Inicio cuando se mira hoy: el evento en curso (con lo que le
 * queda), el siguiente (con cuánto falta) y cómo van las tareas del día. Tocar un evento abre su
 * detalle. [now] avanza cada minuto, así que las cuentas atrás se mueven solas.
 */
@Composable
fun NextUpCard(
    nextUp: NextUp,
    now: LocalDateTime,
    onEventClick: (SyncroItem.Event) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = (nextUp.current ?: nextUp.next)?.categoryColor?.toColor() ?: MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.12f), Color.Transparent)))
                .padding(vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⏱️ Lo próximo",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (nextUp.tasksTotal > 0) {
                    Text(
                        tasksProgressText(nextUp.tasksDone, nextUp.tasksTotal),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (nextUp.tasksTotal > 0) {
                SubtaskProgress(
                    done = nextUp.tasksDone,
                    total = nextUp.tasksTotal,
                    color = accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                Spacer(Modifier.height(6.dp))
            }

            nextUp.current?.let { event ->
                NextUpEventRow(
                    event = event,
                    detail = "Ahora · quedan ${formatDuration(event.minutesLeftAt(now) ?: 0)}",
                    onClick = { onEventClick(event) }
                )
            }
            nextUp.next?.let { event ->
                NextUpEventRow(
                    event = event,
                    detail = "${event.startTime.toDisplayTime()} · ${startsInText(event, now)}",
                    onClick = { onEventClick(event) }
                )
            }
            if (nextUp.current == null && nextUp.next == null) {
                Text(
                    "No te quedan eventos hoy",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NextUpEventRow(event: SyncroItem.Event, detail: String, onClick: () -> Unit) {
    val color = event.categoryColor.toColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(detail, event.location?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** "en 25 min", "en 1 h 20 min" o "empieza ya" (redondeando hacia arriba, como el "quedan"). */
internal fun startsInText(event: SyncroItem.Event, now: LocalDateTime): String {
    val minutes = (Duration.between(now, event.date.atTime(event.startTime)).seconds + 59) / 60
    return if (minutes <= 0) "empieza ya" else "en ${formatDuration(minutes)}"
}

/** "3/5 tareas" o "¡Tareas hechas!" cuando no queda ninguna. */
internal fun tasksProgressText(done: Int, total: Int): String =
    if (done == total) "¡Tareas hechas! 🎉" else "$done/$total tareas"
