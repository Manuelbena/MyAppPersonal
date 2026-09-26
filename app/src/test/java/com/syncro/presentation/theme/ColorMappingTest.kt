package com.syncro.presentation.theme

import androidx.compose.ui.graphics.Color
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Puente entre ArgbColor (dominio) y Color (Compose). Si la conversión perdiera información,
 * todas las categorías y notas cambiarían de color sin que nada fallara visiblemente.
 */
class ColorMappingTest {

    @Test
    fun `ida y vuelta conserva el color en los valores limite`() {
        listOf(
            0x00000000L, // transparente
            0xFF000000, // negro opaco
            0xFFFFFFFF, // blanco: todos los bits a 1 (Int negativo)
            0x80FF5252, // semitransparente
            0xFF6366F1  // color real de categoría (Trabajo)
        ).forEach { argb ->
            val original = ArgbColor(argb)

            assertEquals("0x${argb.toString(16)}", original, original.toColor().toArgbColor())
        }
    }

    @Test
    fun `ArgbColor desde literal Long equivale al Int que guarda Room`() {
        assertEquals(0xFF6366F1.toInt(), ArgbColor(0xFF6366F1).argb)
    }

    @Test
    fun `convertir a Compose da el mismo color que el literal`() {
        assertEquals(Color(0xFF6366F1), ArgbColor(0xFF6366F1).toColor())
    }

    @Test
    fun `cada prioridad tiene su texto`() {
        assertEquals(
            listOf("ALTA", "MEDIA", "BAJA"),
            listOf(Priority.HIGH, Priority.MEDIUM, Priority.LOW).map { it.label }
        )
    }

    @Test
    fun `cada prioridad tiene un color distinto`() {
        assertEquals(Priority.entries.size, Priority.entries.map { it.color }.toSet().size)
    }
}
