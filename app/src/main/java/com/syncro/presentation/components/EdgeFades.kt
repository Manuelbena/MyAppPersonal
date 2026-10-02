package com.syncro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Degradados arriba y abajo de una zona con scroll (Inicio, Ahorros): arriba el contenido se
 * desvanece al subir bajo la cabecera fija y abajo se ve por detrás de la barra de navegación
 * flotante. Va dentro del mismo Box que el contenido, después de él.
 */
@Composable
fun BoxScope.EdgeFades() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .align(Alignment.TopCenter)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        Color.Transparent
                    )
                )
            )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .align(Alignment.BottomCenter)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    )
}
