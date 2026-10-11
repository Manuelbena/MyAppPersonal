package com.syncro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDefaultsTest {

    @Test
    fun `el aviso por defecto depende de la categoria y de si es de todo el dia`() {
        assertEquals(24 * 60, defaultReminderFor("Salud", isAllDay = false))
        assertEquals(4 * 60, defaultReminderFor("Salud", isAllDay = true)) // el día antes a las 20:00
        assertEquals(15, defaultReminderFor("Trabajo", isAllDay = false))
        assertEquals(-9 * 60, defaultReminderFor("Recados", isAllDay = true)) // ese día a las 9:00
    }

    @Test
    fun `ocio, personal y las desconocidas no llevan aviso por defecto`() {
        assertNull(defaultReminderFor("Ocio", isAllDay = false))
        assertNull(defaultReminderFor("Personal", isAllDay = false))
        assertNull(defaultReminderFor("General", isAllDay = true))
    }

    @Test
    fun `cada categoria sugiere sus subtareas tipicas`() {
        assertTrue("Llevar la tarjeta sanitaria" in suggestedSubtasksFor("Salud"))
        assertTrue(suggestedSubtasksFor("Otro").isEmpty())
    }
}
