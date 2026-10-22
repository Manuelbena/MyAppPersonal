package com.syncro.presentation.savings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.syncro.presentation.theme.DarkColorScheme
import com.syncro.presentation.theme.LightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Los colores de las gráficas de Ahorros se comprueban con cuentas, no a ojo, con los mismos umbrales
 * que el validador de paletas de la guía de gráficas (validate_palette.js), traído a Kotlin:
 *  - luz (OKLCH L) dentro de la banda de cada tema y croma suficiente (si no, se leen grises);
 *  - que se distingan con daltonismo (simulación Machado 2009, ΔE en OKLab ×100: objetivo 8, mínimo
 *    6 solo con apoyo de etiquetas) y con vista normal (≥ 15);
 *  - contraste ≥ 3:1 sobre la tarjeta de su propio tema.
 *
 * El donut tiene 3 trozos en círculo (todos son vecinos), así que se miran todas las parejas.
 * Cada tema tiene su paleta: el oscuro no es el claro invertido.
 */
class ChartPaletteTest {

    private fun BudgetChartPalette.hexes() = listOf(needs, wants, savings).map { it.hex() }

    @Test
    fun `en claro el reparto pasa todas las comprobaciones`() {
        checkPalette(LightBudgetChartPalette.hexes(), LightColorScheme.surface.hex(), LIGHT_BAND, minCvd = CVD_TARGET)
    }

    // En oscuro la peor pareja con daltonismo queda en la zona "mínimo" (6–8): vale porque la leyenda
    // con nombre e importe va siempre con el donut y la barra, y hay hueco entre trozos
    @Test
    fun `en oscuro el reparto pasa con la leyenda como apoyo`() {
        checkPalette(DarkBudgetChartPalette.hexes(), DarkColorScheme.surface.hex(), DARK_BAND, minCvd = CVD_FLOOR)
    }

    @Test
    fun `las barras y la linea usan el color principal, que se ve en cada tema`() {
        val light = contrast(LightColorScheme.primary.hex(), LightColorScheme.surface.hex())
        val dark = contrast(DarkColorScheme.primary.hex(), DarkColorScheme.surface.hex())
        assertTrue("claro ${"%.2f".format(light)}:1", light >= CONTRAST_MIN)
        assertTrue("oscuro ${"%.2f".format(dark)}:1", dark >= CONTRAST_MIN)
    }

    private fun checkPalette(colors: List<String>, surface: String, band: ClosedFloatingPointRange<Double>, minCvd: Double) {
        colors.forEach { color ->
            val (lightness, chroma) = oklch(color)
            assertTrue("$color luz ${"%.3f".format(lightness)} fuera de $band", lightness in band)
            assertTrue("$color croma ${"%.3f".format(chroma)} (se lee gris)", chroma >= CHROMA_FLOOR)
            val ratio = contrast(color, surface)
            assertTrue("$color sobre $surface: ${"%.2f".format(ratio)}:1", ratio >= CONTRAST_MIN)
        }
        pairs(colors).forEach { (a, b) ->
            val cvd = minOf(deltaE(a, b, PROTAN), deltaE(a, b, DEUTAN))
            assertTrue("$a↔$b con daltonismo ΔE ${"%.1f".format(cvd)} < $minCvd", cvd >= minCvd)
            val normal = deltaE(a, b, null)
            assertTrue("$a↔$b ΔE ${"%.1f".format(normal)} < $NORMAL_FLOOR", normal >= NORMAL_FLOOR)
        }
    }

    private fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)

    // region Cuentas (port de validate_palette.js)

    private fun pairs(colors: List<String>) =
        colors.indices.flatMap { i -> (i + 1 until colors.size).map { j -> colors[i] to colors[j] } }

    private fun srgb(hex: String) = hex.removePrefix("#").chunked(2).map { it.toInt(16) / 255.0 }
    private fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun linear(hex: String) = srgb(hex).map(::toLinear)

    private fun luminance(hex: String) = linear(hex).let { (r, g, b) -> 0.2126 * r + 0.7152 * g + 0.0722 * b }
    private fun contrast(a: String, b: String): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun oklab(rgb: List<Double>): List<Double> {
        val (r, g, b) = rgb
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return listOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private fun oklch(hex: String) = oklab(linear(hex)).let { (l, a, b) -> l to hypot(a, b) }

    private fun simulate(hex: String, matrix: List<List<Double>>): List<Double> {
        val rgb = linear(hex)
        return matrix.map { row -> (row[0] * rgb[0] + row[1] * rgb[1] + row[2] * rgb[2]).coerceIn(0.0, 1.0) }
    }

    private fun deltaE(a: String, b: String, matrix: List<List<Double>>?): Double {
        val la = oklab(if (matrix != null) simulate(a, matrix) else linear(a))
        val lb = oklab(if (matrix != null) simulate(b, matrix) else linear(b))
        return 100 * sqrt((la[0] - lb[0]).pow(2) + (la[1] - lb[1]).pow(2) + (la[2] - lb[2]).pow(2))
    }

    // endregion

    private companion object {
        const val CVD_TARGET = 8.0
        const val CVD_FLOOR = 6.0
        const val NORMAL_FLOOR = 15.0
        const val CHROMA_FLOOR = 0.10
        const val CONTRAST_MIN = 3.0
        val LIGHT_BAND = 0.43..0.77
        val DARK_BAND = 0.48..0.67

        // Machado, Oliveira y Fernandes (2009), severidad 1.0, en RGB lineal
        val PROTAN = listOf(
            listOf(0.152286, 1.052583, -0.204868),
            listOf(0.114503, 0.786281, 0.099216),
            listOf(-0.003882, -0.048116, 1.051998)
        )
        val DEUTAN = listOf(
            listOf(0.367322, 0.860646, -0.227968),
            listOf(0.280085, 0.672501, 0.047413),
            listOf(-0.011820, 0.042940, 0.968881)
        )
    }
}
