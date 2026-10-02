package com.syncro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Adaptación a tablets. Se usan los cortes de ancho de Material (window size classes): móvil
 * (< 600 dp), tablet en vertical o plegable abierto (600–840 dp) y tablet en horizontal (≥ 840 dp).
 */

enum class WidthClass { Compact, Medium, Expanded }

fun widthClassFor(width: Dp): WidthClass = when {
    width < 600.dp -> WidthClass.Compact
    width < 840.dp -> WidthClass.Medium
    else -> WidthClass.Expanded
}

/** Tamaño de la ventana actual. Lo fija [ProvideWidthClass]; sin él (p. ej. en tests) es móvil. */
val LocalWidthClass = staticCompositionLocalOf { WidthClass.Compact }

/** Ancho máximo de las pantallas de lectura (chat, ajustes, textos legales, login) en tablet. */
val MaxReadableWidth = 680.dp

/** Mide la ventana y expone su [WidthClass] a todo lo que hay dentro. */
@Composable
fun ProvideWidthClass(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWidthClass provides widthClassFor(maxWidth)) {
            content()
        }
    }
}

/**
 * Centra la pantalla con un ancho máximo: en una tablet, una columna de texto de lado a lado se lee
 * mal. En el móvil no cambia nada.
 */
@Composable
fun ReadableWidth(maxWidth: Dp = MaxReadableWidth, content: @Composable () -> Unit) {
    // Fondo propio: los márgenes no dejan ver la pantalla de debajo durante las transiciones
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxSize()
        ) {
            content()
        }
    }
}
