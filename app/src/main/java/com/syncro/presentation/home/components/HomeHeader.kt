package com.syncro.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncro.presentation.components.SyncroIconButton
import com.syncro.presentation.components.UserAvatar

/** Letras del nombre que caben en el saludo sin llegar a los botones de la derecha. */
private const val GREETING_NAME_MAX_LETTERS = 6

/** Acorta el nombre del saludo: "Alejandro" → "Alejan...". */
internal fun greetingName(name: String): String =
    if (name.codePointCount(0, name.length) <= GREETING_NAME_MAX_LETTERS) name
    else name.substring(0, name.offsetByCodePoints(0, GREETING_NAME_MAX_LETTERS)) + "..."

@Composable
fun HomeHeader(
    userName: String,
    userPhotoUrl: String?,
    currentDate: String,
    onOpenSettings: () -> Unit,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTodayButton: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Con weight, la foto y el botón de hoy miden primero: el texto nunca los encoge
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = if (userName.isBlank()) "Hola" else "Hola, ${greetingName(userName)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentDate,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Volver a hoy": aparece al mirar otro día y se va al volver
            AnimatedVisibility(
                visible = showTodayButton,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                SyncroIconButton(
                    icon = Icons.Outlined.Today,
                    onClick = onTodayClick,
                    contentColor = MaterialTheme.colorScheme.primary,
                    contentDescription = "Volver a hoy"
                )
            }
            // La foto de perfil lleva a Ajustes (perfil, avisos, tema…)
            UserAvatar(
                name = userName,
                photoUrl = userPhotoUrl,
                size = 44.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .semantics { contentDescription = "Ajustes" }
            )
        }
    }
}
