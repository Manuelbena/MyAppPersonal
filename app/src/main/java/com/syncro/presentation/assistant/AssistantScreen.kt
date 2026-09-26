package com.syncro.presentation.assistant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import com.syncro.presentation.components.ComingSoonScreen

@Composable
fun AssistantMainScreen() {
    ComingSoonScreen(
        icon = Icons.Outlined.AutoAwesome,
        title = "Asistente",
        description = "Un asistente con IA que te ayudará a organizar tu día a partir de tus tareas y eventos."
    )
}
