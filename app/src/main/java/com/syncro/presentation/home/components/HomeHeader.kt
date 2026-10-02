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
import androidx.compose.ui.unit.dp
import com.syncro.presentation.components.SyncroIconButton
import com.syncro.presentation.components.UserAvatar

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
        Column {
            Text(
                text = if (userName.isBlank()) "Hola" else "Hola, $userName",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
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
