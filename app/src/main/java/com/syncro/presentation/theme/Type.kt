package com.syncro.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.syncro.R

/*
 * Tipografía de Syncro. Las fuentes van dentro de la app (res/font) y no se descargan de Google
 * Play Services: la app funciona sin conexión y así se ven igual desde el primer arranque.
 * Ambas son variables (un archivo con todos los grosores); cada peso fija su eje "wght".
 * Licencias OFL en assets/licenses.
 */

/** Pesos que usa la app; si se usa otro, Android lo simularía a partir del más cercano. */
private val UI_WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/** Plus Jakarta Sans: geométrica y redondeada, para toda la interfaz. */
@OptIn(ExperimentalTextApi::class)
val SyncroFontFamily = FontFamily(
    UI_WEIGHTS.map { weight ->
        Font(
            R.font.plus_jakarta_sans,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
        )
    }
)

/**
 * Fraunces cursiva: el toque "de libreta" (títulos de notas, la frase del día). Tamaño óptico de
 * titular y algo de suavidad (eje SOFT) para que case con las formas redondeadas de la interfaz.
 */
@OptIn(ExperimentalTextApi::class)
val NotebookFontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            R.font.fraunces_italic,
            weight = weight,
            style = FontStyle.Italic,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("opsz", 36f),
                FontVariation.Setting("SOFT", 50f)
            )
        )
    }
)

private fun TextStyle.withAppFont() = copy(fontFamily = SyncroFontFamily)

/** Escala de Material 3 (tamaños y alturas de línea estándar) con la fuente de la app. */
val Typography = Typography().run {
    copy(
        displayLarge = displayLarge.withAppFont(),
        displayMedium = displayMedium.withAppFont(),
        displaySmall = displaySmall.withAppFont(),
        headlineLarge = headlineLarge.withAppFont(),
        headlineMedium = headlineMedium.withAppFont(),
        headlineSmall = headlineSmall.withAppFont(),
        titleLarge = titleLarge.withAppFont(),
        titleMedium = titleMedium.withAppFont(),
        titleSmall = titleSmall.withAppFont(),
        bodyLarge = bodyLarge.withAppFont(),
        bodyMedium = bodyMedium.withAppFont(),
        bodySmall = bodySmall.withAppFont(),
        labelLarge = labelLarge.withAppFont(),
        labelMedium = labelMedium.withAppFont(),
        labelSmall = labelSmall.withAppFont()
    )
}
