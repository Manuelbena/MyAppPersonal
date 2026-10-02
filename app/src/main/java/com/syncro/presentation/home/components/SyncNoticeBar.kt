package com.syncro.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syncro.presentation.home.SyncNotice

/**
 * Aviso discreto bajo la cabecera de Inicio cuando lo que se ve puede no coincidir con Google:
 * sin conexión, la última sincronización falló o hay cambios que no se han podido subir.
 * Sin conexión no hay "Reintentar": la app sincroniza sola al volver la red.
 */
@Composable
fun SyncNoticeBar(
    notice: SyncNotice,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val (icon, container, content) = when (notice) {
        is SyncNotice.Offline -> Triple(Icons.Rounded.CloudOff, colors.surfaceVariant, colors.onSurfaceVariant)
        SyncNotice.SyncFailed -> Triple(Icons.Rounded.SyncProblem, colors.errorContainer, colors.onErrorContainer)
        is SyncNotice.PendingChanges -> Triple(Icons.Rounded.CloudUpload, colors.secondaryContainer, colors.onSecondaryContainer)
    }
    SyncNoticeRow(
        icon = icon,
        text = syncNoticeText(notice),
        container = container,
        content = content,
        onRetry = onRetry.takeIf { notice !is SyncNotice.Offline },
        modifier = modifier
    )
}

@Composable
private fun SyncNoticeRow(
    icon: ImageVector,
    text: String,
    container: Color,
    content: Color,
    onRetry: (() -> Unit)?,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .heightIn(min = 44.dp)
            .padding(start = 14.dp, end = if (onRetry == null) 14.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = content
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text("Reintentar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = content)
            }
        }
    }
}

/** El texto del aviso, con el número de cambios en singular o plural. */
internal fun syncNoticeText(notice: SyncNotice): String = when (notice) {
    is SyncNotice.Offline -> when (notice.pendingChanges) {
        0 -> "Sin conexión · ves lo guardado en el móvil"
        1 -> "Sin conexión · 1 cambio se subirá a Google al volver la red"
        else -> "Sin conexión · ${notice.pendingChanges} cambios se subirán a Google al volver la red"
    }
    SyncNotice.SyncFailed -> "No se pudo sincronizar con Google"
    is SyncNotice.PendingChanges ->
        if (notice.count == 1) "1 cambio sin subir a Google" else "${notice.count} cambios sin subir a Google"
}
