package com.syncro.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.Priority

// Puente entre los colores del dominio (ArgbColor, sin dependencias de UI) y Compose

fun ArgbColor.toColor(): Color = Color(argb)

fun Color.toArgbColor(): ArgbColor = ArgbColor(toArgb())

/** Texto con el que se muestra la prioridad. */
val Priority.label: String
    get() = when (this) {
        Priority.HIGH -> "ALTA"
        Priority.MEDIUM -> "MEDIA"
        Priority.LOW -> "BAJA"
    }

/** Color con el que se pinta la prioridad. */
val Priority.color: Color
    get() = when (this) {
        Priority.HIGH -> Color(0xFFFF5252)
        Priority.MEDIUM -> Color(0xFFFFB74D)
        Priority.LOW -> Color(0xFF81C784)
    }
