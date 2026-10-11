package com.syncro.domain.model

import com.syncro.testutil.anEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategorySuggestionTest {

    @Test
    fun `adivina la categoria por palabras del titulo, sin tildes ni mayusculas`() {
        assertEquals("Salud", suggestEventCategory("Dentista"))
        assertEquals("Salud", suggestEventCategory("cita con el MÉDICO"))
        assertEquals("Trabajo", suggestEventCategory("Reunion de equipo"))
        assertEquals("Deporte", suggestEventCategory("Pádel con Luis"))
        assertEquals("Ocio", suggestEventCategory("Cañas con amigos"))
        assertEquals("Recados", suggestEventCategory("Pasar la ITV"))
    }

    @Test
    fun `solo cuentan palabras enteras, salvo las raices`() {
        // "super" no es "superar"; "gym" no es "gymkana"
        assertNull(suggestEventCategory("Superar el examen"))
        assertNull(suggestEventCategory("Gymkana del cole"))
        assertEquals("Salud", suggestEventCategory("Psicóloga"))
    }

    @Test
    fun `si encajan varias gana la que aparece antes`() {
        assertEquals("Compras", suggestEventCategory("Comprar regalo de cumpleaños"))
        assertEquals("Personal", suggestEventCategory("Cumpleaños de mamá: comprar tarta"))
    }

    @Test
    fun `sin pistas no sugiere nada`() {
        assertNull(suggestEventCategory(""))
        assertNull(suggestEventCategory("Cosas"))
    }

    @Test
    fun `el gasto de un evento depende de su categoria y de su titulo`() {
        assertEquals(MovementCategory.GROCERIES, anEvent(title = "Mercadona", categoryText = "Compras").suggestedExpenseCategory())
        assertEquals(MovementCategory.SHOPPING, anEvent(title = "Zapatillas", categoryText = "Compras").suggestedExpenseCategory())
        assertEquals(MovementCategory.RESTAURANTS, anEvent(title = "Cena con Ana", categoryText = "Ocio").suggestedExpenseCategory())
        assertEquals(MovementCategory.LEISURE, anEvent(title = "Cine", categoryText = "Ocio").suggestedExpenseCategory())
        assertEquals(MovementCategory.HEALTH, anEvent(title = "Dentista", categoryText = "Salud").suggestedExpenseCategory())
        assertNull(anEvent(title = "Reunión", categoryText = "Trabajo").suggestedExpenseCategory())
    }
}
